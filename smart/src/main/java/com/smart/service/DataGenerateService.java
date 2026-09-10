package com.smart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.entity.DimRegion;
import com.smart.entity.FactEnergyMonth;
import com.smart.mapper.DimRegionMapper;
import com.smart.mapper.FactEmissionMonthMapper;
import com.smart.mapper.FactEmissionYearMapper;
import com.smart.mapper.FactEnergyMonthMapper;
import com.smart.vo.GenerateResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟数据生成器（无硬件方案的核心）
 * <p>
 * 数据规律：
 * 1. 区域下钻：为每个市自动扩展 10 个区县（level=3），区县 GDP 按市 GDP 均分 ±18% 浮动；
 * 2. 行业结构：按「每亿元 GDP 能源消费强度 × 行业占比矩阵」生成，与真实行业规律一致
 * （电力行业耗煤为主、交通行业油品为主、工业多能并举）；
 * 3. 季节规律：每个行业内置 12 个月季节系数（电力夏冬双峰、建筑冬季低谷、农业 4-10 月高峰）；
 * 4. 年增长：燃料 1.5%/年、电力 3.0%/年（电气化趋势）；
 * 5. 随机波动 ±5%；
 * 6. 异常注入：约 0.4% 的记录放大 3~4.5 倍（模拟偷排/数据造假，供孤立森林演示）。
 * <p>
 * 演示配置（8 市 × 10 区县 × 5 行业 × 8 能源 × 12 月 × 5 年 ≈ 19 万条），
 * 支持按需扩展年份与区域实现百万级数据量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataGenerateService {

    private static final String[] COUNTY_SUFFIX = {
            "城关区", "滨湖区", "临港区", "新城区", "高新区",
            "经开区", "工业园区", "生态区", "示范区", "科创区"
    };

    /**
     * 每亿元 GDP 的【年度】能源消费强度（行业 × 能源，索引 0 起）
     * 单位：原煤/焦炭/原油/汽油/柴油=吨、天然气=万m³、电力=万kWh、热力=GJ
     * 按演示省 8.5 万亿 GDP 校准：全省年排放约 2.7 亿吨 CO2
     * 注意：生成月度数据时需除以 12 转为月强度
     */
    private static final double[][] INTENSITY = {
            //  原煤    焦炭    原油   汽油    柴油   天然气  电力    热力
            { 515.8,   0.0,   0.0,   0.0,   0.0,   3.0, 119.0,  680 },  // 电力生产
            { 266.2,  53.0,  32.0,   0.0,  16.8,   6.7, 408.0, 4080 },  // 工业
            {  25.0,   0.0,   0.0,   0.0,   5.6,   1.8, 187.0, 2040 },  // 建筑
            {   0.0,   0.0,   0.0, 105.8,  67.2,   0.6,  68.0,    0 },  // 交通
            {  25.0,   0.0,   0.0,   9.2,  22.4,   0.0,  68.0,    0 }   // 农业
    };

    /** 季节系数（行业 × 12 月，年均约 1.0） */
    private static final double[][] SEASON = {
            { 1.15, 1.10, 0.95, 0.85, 0.85, 1.00, 1.20, 1.20, 0.90, 0.80, 0.95, 1.15 }, // 电力：夏冬双峰
            { 0.85, 0.80, 1.02, 1.05, 1.05, 1.05, 1.00, 1.00, 1.05, 1.08, 1.05, 1.00 }, // 工业：春节低谷
            { 0.50, 0.50, 1.15, 1.15, 1.15, 1.10, 1.05, 1.05, 1.10, 1.15, 1.05, 1.05 }, // 建筑：冬季停工期
            { 1.05, 0.85, 0.95, 0.95, 0.95, 0.95, 1.10, 1.10, 0.95, 0.95, 0.95, 1.05 }, // 交通：暑期高峰
            { 0.60, 0.60, 0.80, 1.20, 1.30, 1.30, 1.25, 1.20, 1.10, 1.10, 0.80, 0.60 }  // 农业：农忙期高峰
    };

    /** 年增长率（energy_id 1~8 索引 0 起）：燃料 1.5%、天然气 1.2%、电力 3.0%、热力 2.0% */
    private static final double[] YEAR_GROWTH = {
            1.015, 1.015, 1.015, 1.015, 1.015, 1.012, 1.030, 1.020
    };

    private final DimRegionMapper regionMapper;
    private final FactEnergyMonthMapper energyMonthMapper;
    private final FactEmissionMonthMapper emissionMonthMapper;
    private final FactEmissionYearMapper emissionYearMapper;
    private final FactEnergyMonthService energyMonthService;

    /**
     * 生成模拟数据（覆盖旧模拟数据）
     *
     * @param years          年份跨度（默认 5）
     * @param endYear        结束年份（默认当前年）
     * @param injectAnomaly  是否注入异常值
     */
    public GenerateResultVO generate(int years, int endYear, boolean injectAnomaly) {
        long start = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 1. 清理旧模拟数据与核算结果
        energyMonthMapper.deleteSimulated();
        emissionMonthMapper.deleteAll();
        emissionYearMapper.deleteAll();
        regionMapper.delete(new LambdaQueryWrapper<DimRegion>().eq(DimRegion::getLevel, 3));

        // 2. 为每个市扩展 10 个区县
        List<DimRegion> cities = regionMapper.selectList(
                new LambdaQueryWrapper<DimRegion>().eq(DimRegion::getLevel, 2));
        List<DimRegion> counties = new ArrayList<>();
        for (DimRegion city : cities) {
            String base = city.getRegionName().replace("市", "");
            for (int i = 0; i < COUNTY_SUFFIX.length; i++) {
                DimRegion county = new DimRegion();
                county.setRegionCode(city.getRegionCode() + String.format("%02d", i + 1));
                county.setRegionName(base + COUNTY_SUFFIX[i]);
                county.setParentId(city.getId());
                county.setLevel(3);
                county.setGdp(BigDecimal.valueOf(city.getGdp().doubleValue() / 10.0 * (0.82 + random.nextDouble(0.36)))
                        .setScale(2, RoundingMode.HALF_UP));
                county.setSortOrder(i + 1);
                counties.add(county);
            }
        }
        regionMapper.insertBatch(counties);
        // 自定义批量插入不回填自增主键，重新查询获取真实 ID
        counties = regionMapper.selectList(new LambdaQueryWrapper<DimRegion>()
                .eq(DimRegion::getLevel, 3));

        // 3. 生成月度活动数据
        int yearStart = endYear - years + 1;
        long total = 0;
        int anomalyCount = 0;
        List<FactEnergyMonth> batch = new ArrayList<>(2000);

        for (DimRegion county : counties) {
            double gdp = county.getGdp().doubleValue();
            for (int industry = 0; industry < 5; industry++) {
                for (int energy = 0; energy < 8; energy++) {
                    // 年强度 ÷ 12 = 月强度
                    double intensity = INTENSITY[industry][energy] / 12.0;
                    if (intensity <= 0) {
                        continue;
                    }
                    for (int year = yearStart; year <= endYear; year++) {
                        double growth = Math.pow(YEAR_GROWTH[energy], year - yearStart);
                        for (int month = 1; month <= 12; month++) {
                            double value = gdp * intensity * growth * SEASON[industry][month - 1]
                                    * (0.95 + random.nextDouble(0.10));
                            // 单位换算：燃料吨→万吨、热力GJ→万GJ；天然气/电力单位一致
                            if (energy < 5 || energy == 7) {
                                value /= 10000.0;
                            }
                            if (injectAnomaly && random.nextDouble() < 0.004) {
                                value *= 3.0 + random.nextDouble(1.5);
                                anomalyCount++;
                            }
                            FactEnergyMonth row = new FactEnergyMonth();
                            row.setRegionId(county.getId());
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
        vo.setCountyCount(counties.size());
        vo.setAnomalyCount(anomalyCount);
        vo.setStartYear(yearStart);
        vo.setEndYear(endYear);
        vo.setSeconds((System.currentTimeMillis() - start) / 1000);
        log.info("模拟数据生成完成：{} 条，区县 {} 个，异常 {} 条，耗时 {}s",
                total, counties.size(), anomalyCount, vo.getSeconds());
        return vo;
    }
}
