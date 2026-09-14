package com.smart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.entity.DimRegion;
import com.smart.entity.FactEnergyMonth;
import com.smart.entity.ProvinceParam;
import com.smart.mapper.DimRegionMapper;
import com.smart.mapper.FactEmissionMonthMapper;
import com.smart.mapper.FactEmissionYearMapper;
import com.smart.mapper.FactEnergyMonthMapper;
import com.smart.mapper.ProvinceParamMapper;
import com.smart.vo.CalcResultVO;
import com.smart.vo.GenerateResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 全国碳排放模拟数据生成器（SRS-CG 第 2.3/2.4 节）
 * <p>
 * 数据模型：
 * 月消费 = 市GDP × 行业能源强度/12 × 能耗强度系数 × 能源结构系数 × 产业结构系数
 *         × 季节因子 × 供暖因子 × 年增长 × 随机波动
 * <p>
 * 省际差异化（省级参数，公开统计近似值）：
 * - 能耗强度系数 = 省能耗强度 / 全国均值（山西/宁夏高、北京/上海低）
 * - 能源结构系数 = 煤炭占比相对全国均值的偏差（煤炭类与清洁类反向调整）
 * - 产业结构系数 = 二产占比偏差（工业/电力行业与服务业行业反向调整，弹性 0.6）
 * - 供暖因子：北方省份 11~3 月热力 3.2 倍峰值、燃料 1.15 倍、电力 1.1 倍
 * <p>
 * 模式：all 全量（2021 至今已过完的月份）/ recent 增量（仅上月，幂等：先删该月再插）
 * 规模：424 市 × 24 组合 × 12 月 × 6 年 ≈ 73 万条
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataGenerateService {

    // ---- 全国基准（校准锚点：全国年排放约 115 亿吨 CO2） ----
    private static final double NATIONAL_ENERGY_INTENSITY = 0.55;  // 全国平均能耗强度（吨标煤/万元）
    private static final double NATIONAL_COAL_RATIO = 55.0;        // 全国平均煤炭占能源消费比重（%）
    private static final double NATIONAL_SECONDARY_RATIO = 39.0;   // 全国平均二产占 GDP 比重（%）
    /** 能耗强度年均下降率（技术效率提升，全国趋势） */
    private static final double ENERGY_EFFICIENCY_DECLINE = 0.985;

    /**
     * 每亿元 GDP 的【年度】能源消费强度（行业 × 能源，索引 0 起，全国平均口径）
     * 单位：原煤/焦炭/原油/汽油/柴油=吨、天然气=万m³、电力=万kWh、热力=GJ
     */
    private static final double[][] INTENSITY = {
            //  原煤    焦炭    原油   汽油    柴油   天然气  电力    热力
            { 515.8,   0.0,   0.0,   0.0,   0.0,   3.0, 119.0,  680 },  // 电力生产
            { 266.2,  53.0,  32.0,   0.0,  16.8,   6.7, 408.0, 4080 },  // 工业
            {  25.0,   0.0,   0.0,   0.0,   5.6,   1.8, 187.0, 2040 },  // 建筑
            {   0.0,   0.0,   0.0, 105.8,  67.2,   0.6,  68.0,    0 },  // 交通
            {  25.0,   0.0,   0.0,   9.2,  22.4,   0.0,  68.0,    0 }   // 农业
    };

    /** 基础季节系数（行业 × 12 月，年均约 1.0） */
    private static final double[][] SEASON = {
            { 1.15, 1.10, 0.95, 0.85, 0.85, 1.00, 1.20, 1.20, 0.90, 0.80, 0.95, 1.15 }, // 电力：夏冬双峰
            { 0.85, 0.80, 1.02, 1.05, 1.05, 1.05, 1.00, 1.00, 1.05, 1.08, 1.05, 1.00 }, // 工业：春节低谷
            { 0.50, 0.50, 1.15, 1.15, 1.15, 1.10, 1.05, 1.05, 1.10, 1.15, 1.05, 1.05 }, // 建筑：冬季停工期
            { 1.05, 0.85, 0.95, 0.95, 0.95, 0.95, 1.10, 1.10, 0.95, 0.95, 0.95, 1.05 }, // 交通：暑期高峰
            { 0.60, 0.60, 0.80, 1.20, 1.30, 1.30, 1.25, 1.20, 1.10, 1.10, 0.80, 0.60 }  // 农业：农忙期高峰
    };

    /** 供暖因子（北方供暖省份叠加） */
    private static final double[] HEATING_THERMAL = {3.2, 2.8, 2.0, 1.0, 0.5, 0.3, 0.3, 0.3, 0.4, 0.8, 2.2, 3.0}; // 热力
    private static final double[] HEATING_FUEL = {1.15, 1.15, 1.05, 1.0, 0.95, 0.95, 0.95, 0.95, 0.95, 1.0, 1.10, 1.15}; // 原煤/天然气
    private static final double[] HEATING_POWER = {1.10, 1.05, 1.0, 0.95, 0.95, 1.0, 1.05, 1.05, 1.0, 0.95, 1.05, 1.10}; // 电力

    private final DimRegionMapper regionMapper;
    private final ProvinceParamMapper paramMapper;
    private final FactEnergyMonthMapper energyMonthMapper;
    private final FactEmissionMonthMapper emissionMonthMapper;
    private final FactEmissionYearMapper emissionYearMapper;
    private final FactEnergyMonthService energyMonthService;
    private final CalcService calcService;

    /**
     * 月度自动任务（CG-11）：每月 1 日 00:30 生成"已过完的最新月份"数据并自动核算
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 0 1 * ?")
    public void scheduledMonthlyGenerate() {
        try {
            GenerateResultVO vo = generate("recent", 2021, true);
            log.info("月度自动数据生成完成：{} 条，{} 至 {}-{}，异常 {} 条，耗时 {}s",
                    vo.getTotalCount(), vo.getStartYear(), vo.getEndYear(), vo.getEndYear(), vo.getAnomalyCount(), vo.getSeconds());
            CalcResultVO calc = calcService.execute(0, 0);
            log.info("月度自动核算完成：月度 {} 行，年度 {} 行，耗时 {}s", calc.getMonthRows(), calc.getYearRows(), calc.getSeconds());
        } catch (Exception e) {
            log.error("月度自动生成/核算失败（可手动调用 /data/generate mode=recent 补跑）", e);
        }
    }

    /**
     * 生成全国月度数据
     *
     * @param mode          all 全量（清空重建）/ recent 增量（仅上月，幂等）
     * @param startYear     全量模式起始年份（默认 2021）
     * @param injectAnomaly 是否注入异常值（0.4% 放大 3~4.5 倍，供 AI 检测演示）
     */
    public GenerateResultVO generate(String mode, int startYear, boolean injectAnomaly) {
        long start = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 1. 确定目标范围：起始年 ~ 已过完的最新月份
        LocalDate now = LocalDate.now();
        int lastYear = now.getYear();
        int lastMonth = now.getMonthValue() - 1;
        if (lastMonth == 0) {
            lastMonth = 12;
            lastYear -= 1;
        }
        boolean fullMode = !"recent".equalsIgnoreCase(mode);
        if (fullMode) {
            if (startYear > lastYear) {
                startYear = lastYear;
            }
        } else {
            startYear = lastYear;
        }
        final int endYear = lastYear;
        final int endMonth = lastMonth;

        // 2. 清理旧数据（幂等）
        if (fullMode) {
            energyMonthMapper.deleteSimulated();
            emissionMonthMapper.deleteAll();
            emissionYearMapper.deleteAll();
        } else {
            energyMonthMapper.deleteMonth(endYear, endMonth);
        }

        // 3. 加载市级区域与省级参数
        List<DimRegion> cities = regionMapper.selectList(new LambdaQueryWrapper<DimRegion>()
                .eq(DimRegion::getLevel, 2));
        Map<Integer, ProvinceParam> paramByProvince = new HashMap<>();
        for (ProvinceParam p : paramMapper.selectList(null)) {
            paramByProvince.put(p.getRegionId(), p);
        }
        if (cities.isEmpty() || paramByProvince.isEmpty()) {
            log.error("区域或省级参数缺失：cities={}, params={}", cities.size(), paramByProvince.size());
            throw new IllegalStateException("全国区域数据未初始化，请先执行 docs/sql/regions_cn.sql");
        }

        // 4. 生成
        long total = 0;
        int anomalyCount = 0;
        int noParamCount = 0;
        List<FactEnergyMonth> batch = new ArrayList<>(2000);

        for (DimRegion city : cities) {
            ProvinceParam param = paramByProvince.get(city.getParentId());
            if (param == null || city.getGdp() == null || city.getGdp().signum() <= 0) {
                noParamCount++;
                continue;
            }
            double gdp = city.getGdp().doubleValue();
            double eiFactor = param.getEnergyIntensity().doubleValue() / NATIONAL_ENERGY_INTENSITY;
            double coalRatio = param.getCoalRatio().doubleValue();
            double secRatio = param.getSecondaryRatio().doubleValue();
            double growthBase = (1 + param.getGdpGrowth().doubleValue() / 100.0) * ENERGY_EFFICIENCY_DECLINE;
            int heating = param.getHeating() == null ? 0 : param.getHeating();

            for (int industry = 0; industry < 5; industry++) {
                for (int energy = 0; energy < 8; energy++) {
                    double intensity = INTENSITY[industry][energy] / 12.0;
                    if (intensity <= 0) {
                        continue;
                    }
                    // 能源结构系数：煤炭类随省煤炭占比调整，清洁类反向
                    double coalAdj = (energy == 0 || energy == 1)
                            ? coalRatio / NATIONAL_COAL_RATIO
                            : (100.0 - coalRatio) / (100.0 - NATIONAL_COAL_RATIO);
                    // 产业结构系数：二产占比偏差（工业/电力生产 vs 服务业行业，弹性 0.6）
                    double secAdj = (industry == 0 || industry == 1)
                            ? Math.pow(secRatio / NATIONAL_SECONDARY_RATIO, 0.6)
                            : Math.pow((100.0 - secRatio) / (100.0 - NATIONAL_SECONDARY_RATIO), 0.6);

                    for (int year = startYear; year <= endYear; year++) {
                        int maxMonth = (year == endYear) ? endMonth : 12;
                        double growth = Math.pow(growthBase, year - startYear);
                        for (int month = 1; month <= maxMonth; month++) {
                            double value = gdp * intensity * eiFactor * coalAdj * secAdj
                                    * SEASON[industry][month - 1]
                                    * heatingFactor(heating, energy, month)
                                    * growth
                                    * (0.95 + random.nextDouble(0.10));
                            // 单位换算：燃料吨→万吨、热力GJ→万GJ
                            if (energy < 5 || energy == 7) {
                                value /= 10000.0;
                            }
                            if (injectAnomaly && random.nextDouble() < 0.004) {
                                value *= 3.0 + random.nextDouble(1.5);
                                anomalyCount++;
                            }
                            FactEnergyMonth row = new FactEnergyMonth();
                            row.setRegionId(city.getId());
                            row.setIndustryId(industry + 1);
                            row.setEnergyId(energy + 1);
                            row.setYear(year);
                            row.setMonth(month);
                            row.setConsumption(BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP));
                            row.setDataSource(1);
                            batch.add(row);
                            total++;
                            if (batch.size() >= 2000) {
                                energyMonthService.saveBatch(batch, 2000);
                                batch.clear();
                            }
                        }
                    }
                }
            }
        }
        if (!batch.isEmpty()) {
            energyMonthService.saveBatch(batch, 2000);
        }

        GenerateResultVO vo = new GenerateResultVO();
        vo.setTotalCount(total);
        vo.setCountyCount(cities.size());
        vo.setAnomalyCount(anomalyCount);
        vo.setStartYear(startYear);
        vo.setEndYear(endYear);
        vo.setSeconds((System.currentTimeMillis() - start) / 1000);
        log.info("全国数据生成完成：{} 条（{} 市），{} 至 {}-{}，异常 {} 条，跳过无参数区域 {} 个，耗时 {}s",
                total, cities.size(), startYear, endYear, endMonth, anomalyCount, noParamCount, vo.getSeconds());
        return vo;
    }

    /** 供暖因子：仅北方供暖省份，按能源品类叠加 */
    private double heatingFactor(int heating, int energyIdx, int month) {
        if (heating != 1) {
            return 1.0;
        }
        if (energyIdx == 7) {
            return HEATING_THERMAL[month - 1];
        }
        if (energyIdx == 0 || energyIdx == 5) {
            return HEATING_FUEL[month - 1];
        }
        if (energyIdx == 6) {
            return HEATING_POWER[month - 1];
        }
        return 1.0;
    }
}
