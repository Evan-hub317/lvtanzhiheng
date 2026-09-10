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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // 2. 加载排放因子（每个能源品种取最新生效版本）
        Map<Integer, FactorEmission> factorMap = new HashMap<>();
        List<FactorEmission> factors = factorMapper.selectList(new LambdaQueryWrapper<FactorEmission>()
                .orderByAsc(FactorEmission::getEnergyId)
                .orderByAsc(FactorEmission::getEffectiveYear));
        for (FactorEmission f : factors) {
            factorMap.put(f.getEnergyId(), f);
        }
        if (factorMap.size() < 8) {
            throw new BizException("排放因子库不完整，请在因子库中维护全部能源品种因子");
        }

        long start = System.currentTimeMillis();
        // 3. 重算前清理同范围旧结果（幂等）
        emissionMonthMapper.deleteYearRange(from, to);
        emissionYearMapper.deleteYearRange(from, to);

        // 4. 按能源品种核算 + 碳强度 + 年度聚合 + 同比
        long monthRows = 0;
        for (Map.Entry<Integer, FactorEmission> entry : factorMap.entrySet()) {
            FactorEmission f = entry.getValue();
            monthRows += emissionMonthMapper.insertCalcMonth(
                    entry.getKey(), f.getFactorValue(), f.getOxidRate(), from, to);
        }
        emissionMonthMapper.updateIntensity(from, to);
        long yearRows = emissionMonthMapper.insertYearAgg(from, to);
        emissionMonthMapper.updateYoy(from, to);

        CalcResultVO vo = new CalcResultVO();
        vo.setMonthRows(monthRows);
        vo.setYearRows(yearRows);
        vo.setSeconds((System.currentTimeMillis() - start) / 1000);
        log.info("碳核算完成：{}-{}，月度 {} 行，年度 {} 行，耗时 {}s", from, to, monthRows, yearRows, vo.getSeconds());
        return vo;
    }
}
