package com.smart.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.common.BizException;
import com.smart.dto.ReportGenerateDTO;
import com.smart.dto.SimulateDTO;
import com.smart.entity.AlertRecord;
import com.smart.entity.DimIndustry;
import com.smart.entity.DimRegion;
import com.smart.entity.ReportRecord;
import com.smart.mapper.AlertRecordMapper;
import com.smart.mapper.DimIndustryMapper;
import com.smart.mapper.DimRegionMapper;
import com.smart.mapper.FactEnergyMonthMapper;
import com.smart.vo.CalcResultVO;
import com.smart.vo.DataStatusVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.KpiVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.PredictVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.SimResultVO;
import com.smart.vo.StructureVO;
import com.smart.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 分析助手的 14 个分析工具（Spring AI @Tool 声明式注册）。
 * <p>
 * 方法签名 + 注解即工具 schema 的唯一来源；@Tool.name 保持与模型长期使用的
 * snake_case 名称一致（query_kpi 等），参数名同样保持 snake_case（start_year 等），
 * 避免工具选择退化。工具由 AgentServiceImpl 的手工循环逐轮调用（执行链路可视化
 * 需要每轮事件），不启用框架内部自动执行。
 */
@Service
@RequiredArgsConstructor
public class AgentToolService {

    private final AnalysisService analysisService;
    private final CalcService calcService;
    private final SimulationService simulationService;
    private final AlertService alertService;
    private final ReportService reportService;
    private final DimRegionMapper regionMapper;
    private final DimIndustryMapper industryMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final FactEnergyMonthMapper energyMonthMapper;

    // ============ 工具 ============

    @Tool(name = "query_kpi", description = "查询区域某年实际核算的核心指标（排放总量/同比/碳强度）；仅限已有核算数据的年份，未来年份请先调用 predict_emission")
    public Map<String, Object> queryKpi(
            @ToolParam(description = "区域中文名，如：全国、江苏、南京") String region,
            @ToolParam(description = "年份，默认最近完整年", required = false) Integer year) {
        int regionId = resolveRegionId(region);
        int fullYear = latestFullYear();
        int y = year == null ? fullYear : year;
        if (y > fullYear) {
            // 未来年份：引导走独立的预测步骤（predict_emission）
            return Map.of("summary", y + " 年为未来年份，暂无核算数据；请先调用 predict_emission 工具获得预测值");
        }
        KpiVO kpi = analysisService.kpi(regionId, y);
        if (kpi == null || kpi.getTotalEmission() == null) {
            return Map.of("summary", "暂无该区域核算数据");
        }
        String s = String.format("%d 年排放总量 %s 亿吨", kpi.getYear(),
                kpi.getTotalEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP));
        if (kpi.getYoyRate() != null) {
            s += "，同比 " + kpi.getYoyRate() + "%";
        }
        if (kpi.getIntensity() != null) {
            s += "，碳强度 " + kpi.getIntensity() + " tCO2/万元";
        }
        return Map.of("summary", s);
    }

    @Tool(name = "query_trend", description = "查询区域历史年度排放趋势序列；仅限已有核算数据的年份，含未来年份请先调用 predict_emission")
    public Map<String, Object> queryTrend(
            @ToolParam(description = "区域中文名") String region,
            @ToolParam(description = "起始年份", required = false) Integer start_year,
            @ToolParam(description = "结束年份", required = false) Integer end_year) {
        int regionId = resolveRegionId(region);
        int fullYear = latestFullYear();
        int start = start_year == null ? 2021 : start_year;
        int end = end_year == null ? fullYear : end_year;
        if (end > fullYear) {
            // 含未来年份：引导走独立的预测步骤（predict_emission 提供含未来段的图表）
            return Map.of("summary", end + " 年为未来年份，暂无核算数据；请先调用 predict_emission 工具获得含预测的完整趋势");
        }
        List<TrendVO> rows = analysisService.trend(regionId, start, end, null);
        return trendResult(rows, "排放趋势");
    }

    @Tool(name = "query_structure", description = "查询区域某年行业排放结构")
    public Map<String, Object> queryStructure(
            @ToolParam(description = "区域中文名") String region,
            @ToolParam(description = "年份", required = false) Integer year) {
        int regionId = resolveRegionId(region);
        int y = year == null ? latestFullYear() : year;
        List<StructureVO> rows = analysisService.structure(regionId, y);
        return structureResult(rows, y + " 年行业结构");
    }

    @Tool(name = "run_calc", description = "重新执行碳核算（需先有活动数据），返回核算行数与校准报告")
    public Map<String, Object> runCalc() {
        CalcResultVO result = calcService.execute(0, 0);
        String s = String.format("核算完成：月度 %d 行，年度 %d 行，耗时 %ds", result.getMonthRows(),
                result.getYearRows(), result.getSeconds());
        if (result.getCheckReport() != null) {
            s += "。" + result.getCheckReport().replace("\n", "；");
        }
        return Map.of("summary", s);
    }

    @Tool(name = "predict_emission", description = "LSTM 预测区域未来排放趋势（含达峰判断与置信区间）")
    public Map<String, Object> predictEmission(
            @ToolParam(description = "区域中文名") String region) {
        int regionId = resolveRegionId(region);
        PredictVO p = simulationService.predict(regionId);
        return predictResult(p);
    }

    @Tool(name = "detect_anomaly", description = "孤立森林 AI 异常检测，返回检出异常点与统计")
    public Map<String, Object> detectAnomaly() {
        Map<String, Object> r = alertService.runAnomalyDetection();
        // Top 10 异常点（按分数降序）
        List<AlertRecord> top = alertRecordMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getDetectType, 2)
                .orderByDesc(AlertRecord::getAnomalyScore)
                .last("LIMIT 10"));
        List<String> names = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (AlertRecord rec : top) {
            DimRegion region = regionMapper.selectById(rec.getRegionId());
            String regionName = region == null ? "区域" + rec.getRegionId() : region.getRegionName();
            names.add(regionName + " " + rec.getYear() + "-" + rec.getMonth());
            scores.add(rec.getAnomalyScore() == null ? 0 : rec.getAnomalyScore().doubleValue());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("summary", String.format("检测完成：%d 个序列，检出异常 %d 个，入库 Top %d（耗时 %ds）",
                r.get("series"), r.get("anomalies"), r.get("saved"), r.get("seconds")));
        if (!scores.isEmpty()) {
            result.put("chart", Map.of("type", "bar", "title", "AI 异常检测 Top 异常点（分数）",
                    "x", names, "series", List.of(Map.of("name", "异常分数", "data", scores))));
        }
        return result;
    }

    @Tool(name = "simulate_policy", description = "政策情景仿真：调整煤炭占比/工业占比/能效下降率，推演未来排放与达峰")
    public Map<String, Object> simulatePolicy(
            @ToolParam(description = "区域中文名") String region,
            @ToolParam(description = "煤炭占比%", required = false) Double coal_ratio,
            @ToolParam(description = "工业占GDP比重%", required = false) Double industry_ratio,
            @ToolParam(description = "单位能耗年均下降率%", required = false) Double tech_efficiency) {
        int regionId = resolveRegionId(region);
        SimulateDTO dto = new SimulateDTO();
        dto.setRegionId(regionId);
        // 未指定参数时取该区域基准参数（维持现状）
        Map<String, Object> base = simulationService.baseParam(regionId);
        dto.setCoalRatio(coal_ratio == null ? ((Number) base.get("coalRatio")).doubleValue() : coal_ratio);
        dto.setIndustryRatio(industry_ratio == null ? ((Number) base.get("industryRatio")).doubleValue() : industry_ratio);
        dto.setTechEfficiency(tech_efficiency == null ? 1.5 : tech_efficiency);
        dto.setYears(6);
        SimResultVO sim = simulationService.simulate(dto);
        Map<String, Object> result = new HashMap<>();
        String s = String.format("仿真完成（煤 %.1f%% / 工业 %.1f%% / 能效 %.1f%%）",
                dto.getCoalRatio(), dto.getIndustryRatio(), dto.getTechEfficiency());
        if (sim.getPeakYear() != null) {
            s += String.format("：%d 年达峰，峰值 %s 亿吨", sim.getPeakYear(),
                    sim.getPeakValue().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP));
        } else {
            s += "：仿真期内未达峰";
        }
        result.put("summary", s);
        result.put("chart", Map.of("type", "trend", "title", "政策仿真轨迹",
                "x", sim.getYears(),
                "series", List.of(Map.of("name", "仿真排放(亿吨)", "data",
                        sim.getValues().stream().map(v -> v.divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    @Tool(name = "generate_report", description = "一键生成 AIGC 监测报告")
    public Map<String, Object> generateReport(
            @ToolParam(description = "区域中文名") String region,
            @ToolParam(description = "1月报 2年报") Integer period_type,
            @ToolParam(description = "年份", required = false) Integer year,
            @ToolParam(description = "月报必填", required = false) Integer month) {
        int regionId = resolveRegionId(region);
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setRegionId(regionId);
        dto.setPeriodType(period_type == null ? 2 : period_type);
        dto.setYear(year == null ? latestFullYear() : year);
        dto.setMonth(month);
        ReportRecord record = reportService.generate(dto, null);
        String brief = record.getContent() == null ? "" : record.getContent().substring(0, Math.min(200, record.getContent().length()));
        return Map.of("summary", "报告已生成：《" + record.getTitle() + "》。摘要：" + brief);
    }

    @Tool(name = "check_threshold", description = "判断区域某年排放是否超过阈值（阈值未指定时用平台预警规则）")
    public Map<String, Object> checkThreshold(
            @ToolParam(description = "区域中文名") String region,
            @ToolParam(description = "判断年份（可为预测年）") Integer year,
            @ToolParam(description = "自定义阈值（亿吨）", required = false) Double threshold) {
        int regionId = resolveRegionId(region);
        int y = year == null ? latestFullYear() : year;
        // 目标年为未来/不完整年时使用预测值对比（如"明年会超标吗"）
        boolean predicted = y > latestFullYear();
        BigDecimal totalYi;
        if (predicted) {
            PredictVO p = simulationService.predict(regionId);
            int idx = p.getYears().indexOf(y);
            if (idx < 0) {
                return Map.of("summary", y + " 年不在预测范围内（预测至 " +
                        p.getYears().get(p.getYears().size() - 1) + " 年）");
            }
            totalYi = p.getValues().get(idx).divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP);
        } else {
            List<TrendVO> rows = analysisService.trend(regionId, y, y, null);
            if (rows.isEmpty()) {
                return Map.of("summary", y + " 年暂无该区域数据");
            }
            totalYi = rows.get(0).getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP);
        }
        // 阈值优先级：用户指定 > 区域默认基值（上一个完整年排放 × 1.05，允许 5% 年增长）
        BigDecimal thresholdYi = null;
        String thresholdSource = "";
        if (threshold != null) {
            thresholdYi = BigDecimal.valueOf(threshold);
            thresholdSource = "自定义";
        } else {
            int baseYear = latestFullYear();
            List<TrendVO> baseRows = analysisService.trend(regionId, baseYear, baseYear, null);
            if (!baseRows.isEmpty() && baseRows.get(0).getEmission().signum() > 0) {
                thresholdYi = baseRows.get(0).getEmission()
                        .multiply(BigDecimal.valueOf(1.05))
                        .divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP);
                thresholdSource = "默认基值（" + baseYear + " 年 × 1.05）";
            }
        }
        if (thresholdYi == null) {
            return Map.of("summary", String.format("%d 年排放总量 %s 亿吨；该区域无历史数据可计算默认基值，请指定阈值后重新判断", y, totalYi));
        }
        boolean exceed = totalYi.compareTo(thresholdYi) > 0;
        return Map.of("summary", String.format("%d 年排放%s %s 亿吨 vs 阈值%s %s 亿吨：%s%s", y,
                predicted ? "（预测值）" : "", totalYi,
                thresholdSource.isEmpty() ? "" : "（" + thresholdSource + "）", thresholdYi,
                exceed ? "超标" : "未超标", exceed ? " " + totalYi.subtract(thresholdYi) + " 亿吨" : ""));
    }

    @Tool(name = "query_energy_structure", description = "查询区域某年能源结构（返回各能源品种排放量与占比明细）")
    public Map<String, Object> queryEnergyStructure(
            @ToolParam(description = "区域中文名") String region,
            @ToolParam(description = "年份", required = false) Integer year) {
        int regionId = resolveRegionId(region);
        int y = year == null ? latestFullYear() : year;
        List<EnergyStructureVO> rows = analysisService.energyStructure(regionId, y);
        Map<String, Object> r = energyResult(rows, y + " 年能源结构（化石燃料直接排放）");
        // 附注间接排放（电力/热力，展示口径，不计入总量）
        List<EnergyStructureVO> indirect = analysisService.indirect(y);
        if (indirect != null && !indirect.isEmpty()) {
            String indDesc = indirect.stream()
                    .map(v -> v.getEnergyName() + " " + v.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP) + " 亿吨")
                    .collect(Collectors.joining("，"));
            r.put("summary", r.get("summary") + "；间接排放（不计入总量）：" + indDesc);
        }
        return r;
    }

    @Tool(name = "query_region_ranking", description = "查询某年各市排放排行（返回各市排放量明细）；指定 region（省名）时返回该省下辖各市排行")
    public Map<String, Object> queryRegionRanking(
            @ToolParam(description = "省中文名（可选，不传为全国各市）", required = false) String region,
            @ToolParam(description = "年份，默认最近完整年", required = false) Integer year) {
        int y = year == null ? latestFullYear() : year;
        List<RegionRankVO> rows;
        String scope;
        if (region != null) {
            int provinceId = resolveRegionId(region);
            rows = analysisService.cityRankingByProvince(provinceId, y);
            scope = resolveName(region) + " 各市";
        } else {
            rows = analysisService.regionRanking(y);
            scope = "各市";
        }
        if (rows == null || rows.isEmpty()) {
            return Map.of("summary", "暂无数据");
        }
        String detail = rows.stream().limit(10)
                .map(r -> r.getRegionName() + " " + r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP) + "亿吨")
                .collect(Collectors.joining("，"));
        Map<String, Object> result = new HashMap<>();
        result.put("summary", y + " 年" + scope + "排放排行 Top10：" + detail);
        result.put("chart", Map.of("type", "bar", "title", y + " 年" + scope + "排放排行",
                "x", rows.stream().limit(10).map(RegionRankVO::getRegionName).toList(),
                "series", List.of(Map.of("name", "排放(亿吨)", "data",
                        rows.stream().limit(10).map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    @Tool(name = "query_monthly_trend", description = "查询区域某年各月排放（返回各月数值明细，可分析季节规律）")
    public Map<String, Object> queryMonthlyTrend(
            @ToolParam(description = "区域中文名") String region,
            @ToolParam(description = "年份", required = false) Integer year) {
        int regionId = resolveRegionId(region);
        int y = year == null ? latestFullYear() : year;
        List<MonthlyVO> rows = analysisService.monthlyTrend(regionId, y, null, null);
        if (rows == null || rows.isEmpty()) {
            return Map.of("summary", "暂无数据");
        }
        String detail = rows.stream()
                .map(r -> r.getMonth() + "月" + r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP) + "亿吨")
                .collect(Collectors.joining("，"));
        Map<String, Object> result = new HashMap<>();
        result.put("summary", y + " 年各月排放：" + detail);
        result.put("chart", Map.of("type", "bar", "title", y + " 年各月排放",
                "x", rows.stream().map(r -> r.getMonth() + "月").toList(),
                "series", List.of(Map.of("name", "排放(亿吨)", "data",
                        rows.stream().map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    @Tool(name = "query_industry_trend", description = "查询区域某行业年度排放趋势（行业名如：电力生产、工业、建筑、交通、农业）")
    public Map<String, Object> queryIndustryTrend(
            @ToolParam(description = "区域中文名") String region,
            @ToolParam(description = "行业中文名") String industry,
            @ToolParam(description = "起始年份", required = false) Integer start_year,
            @ToolParam(description = "结束年份", required = false) Integer end_year) {
        int regionId = resolveRegionId(region);
        int fullYear = latestFullYear();
        int start = start_year == null ? 2021 : start_year;
        int end = end_year == null ? fullYear : end_year;
        if (end > fullYear) {
            return Map.of("summary", end + " 年为未来年份，请先调用 predict_emission");
        }
        int industryId = resolveIndustryId(industry);
        List<TrendVO> rows = analysisService.industryTrend(regionId, start, end, industryId);
        return trendResult(rows, industry + " 行业排放趋势");
    }

    @Tool(name = "query_alerts", description = "查询平台预警概况（待确认数、规则预警数、AI检测异常数、最近预警）")
    public Map<String, Object> queryAlerts() {
        Map<String, Object> summary = alertService.summary();
        List<AlertRecord> top = alertRecordMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                .orderByDesc(AlertRecord::getCreateTime)
                .last("LIMIT 5"));
        String topDesc = top.stream().map(r -> (r.getRuleName() == null ? "" : r.getRuleName()) + "（"
                + r.getYear() + (r.getMonth() == null ? "" : "-" + r.getMonth()) + "）")
                .collect(Collectors.joining("，"));
        return Map.of("summary", String.format("预警概况：待确认 %s 条，阈值规则预警 %s 条，AI 检测异常 %s 条；最近预警：%s",
                summary.get("pending"), summary.get("ruleCount"), summary.get("aiCount"),
                topDesc.isEmpty() ? "无" : topDesc));
    }

    // ============ 结果组装（含图表指令） ============

    private Map<String, Object> trendResult(List<TrendVO> rows, String title) {
        Map<String, Object> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            result.put("summary", "暂无数据");
            return result;
        }
        // summary 含全部年度数值（LLM 可据此回答细节问题，图表仅为可视化）
        String detail = rows.stream()
                .map(r -> r.getYear() + "年" + r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP) + "亿吨")
                .collect(Collectors.joining("，"));
        result.put("summary", title + "：" + detail);
        result.put("chart", Map.of("type", "trend", "title", title,
                "x", rows.stream().map(TrendVO::getYear).toList(),
                "series", List.of(Map.of("name", "排放(亿吨)", "data",
                        rows.stream().map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    private Map<String, Object> structureResult(List<StructureVO> rows, String title) {
        Map<String, Object> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            result.put("summary", "暂无数据");
            return result;
        }
        // summary 含各行业排放量与占比明细（LLM 可排序、对比、回答细节）
        BigDecimal total = rows.stream().map(StructureVO::getEmission).reduce(BigDecimal.ZERO, BigDecimal::add);
        String detail = rows.stream().map(r -> {
            BigDecimal yi = r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP);
            BigDecimal pct = total.signum() > 0
                    ? r.getEmission().multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return r.getIndustryName() + " " + yi + " 亿吨（占 " + pct + "%）";
        }).collect(Collectors.joining("，"));
        result.put("summary", title + "：" + detail);
        result.put("chart", Map.of("type", "pie", "title", title,
                "x", rows.stream().map(StructureVO::getIndustryName).toList(),
                "series", List.of(Map.of("name", "排放(亿吨)", "data",
                        rows.stream().map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    private Map<String, Object> energyResult(List<EnergyStructureVO> rows, String title) {
        Map<String, Object> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            result.put("summary", "暂无数据");
            return result;
        }
        BigDecimal total = rows.stream().map(EnergyStructureVO::getEmission).reduce(BigDecimal.ZERO, BigDecimal::add);
        String detail = rows.stream().map(r -> {
            BigDecimal yi = r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP);
            BigDecimal pct = total.signum() > 0
                    ? r.getEmission().multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return r.getEnergyName() + " " + yi + " 亿吨（占 " + pct + "%）";
        }).collect(Collectors.joining("，"));
        result.put("summary", title + "：" + detail);
        result.put("chart", Map.of("type", "pie", "title", title,
                "x", rows.stream().map(EnergyStructureVO::getEnergyName).toList(),
                "series", List.of(Map.of("name", "排放(亿吨)", "data",
                        rows.stream().map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    private Map<String, Object> predictResult(PredictVO p) {
        Map<String, Object> result = new HashMap<>();
        String s = String.format("预测模型：%s；", "lstm".equals(p.getMethod()) ? "LSTM" : "趋势模型");
        if (p.getPeakYear() != null) {
            s += String.format("%d 年达峰（峰值 %s 亿吨）；", p.getPeakYear(),
                    p.getPeakValue().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP));
        } else {
            s += "预测期内未达峰；";
        }
        // 逐年明细（LLM 可回答任意年份）
        for (int i = 0; i < p.getYears().size(); i++) {
            s += p.getYears().get(i) + "年" + p.getValues().get(i)
                    .divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP) + "亿吨";
            if (i < p.getYears().size() - 1) {
                s += "，";
            }
        }
        result.put("summary", s);
        // 历史 + 预测合并为一条趋势
        List<Integer> x = new ArrayList<>(p.getHistoryYears());
        x.addAll(p.getYears());
        List<BigDecimal> vals = new ArrayList<>(p.getHistoryValues());
        vals.addAll(p.getValues());
        result.put("chart", Map.of("type", "trend", "title", "排放预测（历史+未来）",
                "x", x,
                "series", List.of(Map.of("name", "排放(亿吨)", "data",
                        vals.stream().map(v -> v.divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    // ============ 辅助 ============

    private int latestFullYear() {
        try {
            // 完整年：数据最晚年若不足 12 个月则回退上一年
            DataStatusVO status = energyMonthMapper.selectStatus();
            if (status == null || status.getMinYear() == null) {
                return 2025;
            }
            int year = status.getMaxYear();
            if (energyMonthMapper.selectMaxMonth(year) < 12 && year > 2021) {
                year -= 1;
            }
            return year;
        } catch (Exception e) {
            return 2025;
        }
    }

    private int resolveIndustryId(String name) {
        List<DimIndustry> list = industryMapper.selectList(new LambdaQueryWrapper<DimIndustry>()
                .likeRight(DimIndustry::getIndustryName, name.replace("行业", "").trim()).last("LIMIT 1"));
        if (list.isEmpty()) {
            list = industryMapper.selectList(new LambdaQueryWrapper<DimIndustry>()
                    .like(DimIndustry::getIndustryName, name.trim()).last("LIMIT 1"));
        }
        if (list.isEmpty()) {
            throw new BizException("未找到行业：" + name + "（可选：电力生产、工业、建筑、交通、农业）");
        }
        return list.get(0).getId();
    }

    private String resolveName(Object region) {
        String name = String.valueOf(region).trim();
        DimRegion r = regionMapper.selectOne(new LambdaQueryWrapper<DimRegion>()
                .eq(DimRegion::getRegionName, name).last("LIMIT 1"));
        if (r == null) {
            r = regionMapper.selectOne(new LambdaQueryWrapper<DimRegion>()
                    .likeRight(DimRegion::getRegionName, name).last("LIMIT 1"));
        }
        return r == null ? name : r.getRegionName();
    }

    private int resolveRegionId(Object region) {
        String name = region == null ? "" : String.valueOf(region).trim();
        if (StrUtil.isBlank(name) || "全国".equals(name)) {
            return 1;
        }
        // 依次尝试：精确名、补"省/市"后缀、前缀模糊（简称如"江苏"→"江苏省"）
        DimRegion r = regionMapper.selectOne(new LambdaQueryWrapper<DimRegion>()
                .eq(DimRegion::getRegionName, name).last("LIMIT 1"));
        if (r == null) {
            r = regionMapper.selectOne(new LambdaQueryWrapper<DimRegion>()
                    .eq(DimRegion::getRegionName, name + "省").last("LIMIT 1"));
        }
        if (r == null) {
            r = regionMapper.selectOne(new LambdaQueryWrapper<DimRegion>()
                    .eq(DimRegion::getRegionName, name + "市").last("LIMIT 1"));
        }
        if (r == null) {
            r = regionMapper.selectOne(new LambdaQueryWrapper<DimRegion>()
                    .likeRight(DimRegion::getRegionName, name).last("LIMIT 1"));
        }
        if (r == null) {
            throw new BizException("未找到区域：" + name + "（请使用全称，如：江苏省、南京市）");
        }
        return r.getId();
    }
}
