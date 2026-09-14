package com.smart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.common.BizException;
import com.smart.entity.FactorEmission;
import com.smart.mapper.FactEmissionMonthMapper;
import com.smart.mapper.FactEmissionYearMapper;
import com.smart.mapper.FactEnergyMonthMapper;
import com.smart.mapper.FactorEmissionMapper;
import com.smart.service.CalcService;
import com.smart.vo.CalcResultVO;
import com.smart.vo.DataStatusVO;
import com.smart.vo.RegionRankVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 碳核算引擎（能源活动类）
 * <p>
 * 核算公式：排放量 = 活动数据 × 排放因子 × 氧化率
 * 执行链路：月明细聚合核算 → 碳强度 → 年度聚合 → 同比增速，全部由 SQL 完成，可追溯留痕
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalcServiceImpl implements CalcService {

    private final FactEnergyMonthMapper energyMonthMapper;
    private final FactEmissionMonthMapper emissionMonthMapper;
    private final FactEmissionYearMapper emissionYearMapper;
    private final FactorEmissionMapper factorMapper;

    @Override
    public CalcResultVO execute(int startYear, int endYear) {
        // 1. 确定数据年份范围
        DataStatusVO status = energyMonthMapper.selectStatus();
        if (status == null || status.getMinYear() == null) {
            throw new BizException("暂无活动数据，请先生成模拟数据");
        }
        int from = startYear > 0 ? startYear : status.getMinYear();
        int to = endYear > 0 ? endYear : status.getMaxYear();
        if (from > to) {
            throw new BizException("年份范围不合法");
        }

        // 2. 加载排放因子：非电力能源（每种取最新版本；电力走区域电网因子，由 SQL 关联）
        Map<Integer, FactorEmission> factorMap = new HashMap<>();
        List<FactorEmission> factors = factorMapper.selectList(new LambdaQueryWrapper<FactorEmission>()
                .isNull(FactorEmission::getGridCode)
                .orderByAsc(FactorEmission::getEnergyId)
                .orderByAsc(FactorEmission::getEffectiveYear));
        for (FactorEmission f : factors) {
            factorMap.put(f.getEnergyId(), f);
        }
        if (factorMap.size() < 7) {
            throw new BizException("排放因子库不完整（缺非电力能源因子），请执行 docs/sql/init.sql 与 migration_cn.sql");
        }

        long start = System.currentTimeMillis();
        // 3. 重算前清理同范围旧结果（幂等）
        emissionMonthMapper.deleteYearRange(from, to);
        emissionYearMapper.deleteYearRange(from, to);

        // 4. 按能源品种核算：非电力 7 种 + 电力（区域电网因子关联）
        long monthRows = 0;
        for (Map.Entry<Integer, FactorEmission> entry : factorMap.entrySet()) {
            FactorEmission f = entry.getValue();
            monthRows += emissionMonthMapper.insertCalcMonth(
                    entry.getKey(), f.getFactorValue(), f.getOxidRate(), from, to);
        }
        monthRows += emissionMonthMapper.insertCalcMonthPower(from, to);
        emissionMonthMapper.updateIntensity(from, to);
        long yearRows = emissionMonthMapper.insertYearAgg(from, to);
        emissionMonthMapper.updateYoy(from, to);

        CalcResultVO vo = new CalcResultVO();
        vo.setMonthRows(monthRows);
        vo.setYearRows(yearRows);
        vo.setSeconds((System.currentTimeMillis() - start) / 1000);
        vo.setCheckReport(buildCheckReport(to));
        log.info("碳核算完成：{}-{}，月度 {} 行，年度 {} 行，耗时 {}s", from, to, monthRows, yearRows, vo.getSeconds());
        return vo;
    }

    /**
     * 全国校准校验报告（CG-17）：全国总量 100~130 亿吨 + 省际常识校验
     * 注意：使用最近一个完整年度（末年不足 12 个月时回退上一年，避免把不完整年当全年）
     */
    private String buildCheckReport(int year) {
        try {
            int checkYear = year;
            if (energyMonthMapper.selectMaxMonth(year) < 12 && year > 2021) {
                checkYear = year - 1;
            }
            // 单位换算：SQL 返回 tCO2，此处转为亿吨展示
            BigDecimal nationalYi = emissionYearMapper.selectNationalTotal(checkYear)
                    .divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP);
            if (nationalYi == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(checkYear).append(" 年全国排放总量：").append(nationalYi).append(" 亿吨");
            if (nationalYi.compareTo(BigDecimal.valueOf(100)) >= 0 && nationalYi.compareTo(BigDecimal.valueOf(130)) <= 0) {
                sb.append(" ✅ 在合理区间（100~130 亿吨）");
            } else {
                sb.append(" ⚠️ 超出合理区间，请检查省级参数");
            }
            List<RegionRankVO> ranking = emissionYearMapper.selectProvinceRanking(checkYear);
            if (!ranking.isEmpty()) {
                sb.append("\n前三：").append(ranking.stream().limit(3)
                        .map(r -> r.getRegionName() + " " + r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP))
                        .collect(Collectors.joining("，")));
                sb.append("\n末三：").append(ranking.stream().skip(Math.max(0, ranking.size() - 3))
                        .map(r -> r.getRegionName() + " " + r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP))
                        .collect(Collectors.joining("，")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("校准校验失败", e);
            return null;
        }
    }
}
