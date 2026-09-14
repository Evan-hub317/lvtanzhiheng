package com.smart.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.client.AlgoClient;
import com.smart.common.BizException;
import com.smart.dto.ReportGenerateDTO;
import com.smart.entity.DimRegion;
import com.smart.entity.ReportRecord;
import com.smart.mapper.DimRegionMapper;
import com.smart.mapper.ReportRecordMapper;
import com.smart.service.AnalysisService;
import com.smart.service.AlertService;
import com.smart.service.ReportService;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.KpiVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.StructureVO;
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
 * AIGC 监测报告服务
 * <p>
 * 生成链路：聚合数据摘要（总量/同比/碳强度/行业结构/能源结构/预警）→
 * Python 算法服务调 DeepSeek 生成 Markdown 正文；AI 不可用时降级为模板文字报告，保证演示不中断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final AnalysisService analysisService;
    private final AlertService alertService;
    private final AlgoClient algoClient;
    private final DimRegionMapper regionMapper;
    private final ReportRecordMapper reportMapper;

    @Override
    public ReportRecord generate(ReportGenerateDTO dto, Long creatorId) {
        Map<String, Object> summary = buildSummary(dto);

        // AI 生成正文，失败降级模板
        String content;
        try {
            JSONObject data = algoClient.post("/api/alg/report", JSONUtil.parseObj(JSONUtil.toJsonStr(
                    Map.of("summary", summary))));
            content = data.getStr("content");
        } catch (Exception e) {
            log.warn("AI 报告生成失败，使用模板降级: {}", e.getMessage());
            content = buildFallbackReport(summary);
        }

        ReportRecord record = new ReportRecord();
        record.setTitle((String) summary.get("title"));
        record.setRegionId(dto.getRegionId());
        record.setPeriodType(dto.getPeriodType());
        record.setReportYear(dto.getYear());
        record.setReportMonth(dto.getMonth());
        record.setContent(content);
        record.setStatus(1);
        record.setCreatorId(creatorId);
        reportMapper.insert(record);
        log.info("监测报告已生成：{}（id={}）", record.getTitle(), record.getId());
        return record;
    }

    @Override
    public List<ReportRecord> list() {
        return reportMapper.selectList(new LambdaQueryWrapper<ReportRecord>()
                .orderByDesc(ReportRecord::getCreateTime));
    }

    @Override
    public ReportRecord detail(long id) {
        ReportRecord record = reportMapper.selectById(id);
        if (record == null) {
            throw new BizException("报告不存在");
        }
        return record;
    }

    @Override
    public ReportRecord regenerate(long id, Long creatorId) {
        ReportRecord old = detail(id);
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setRegionId(old.getRegionId());
        dto.setPeriodType(old.getPeriodType());
        dto.setYear(old.getReportYear());
        dto.setMonth(old.getReportMonth());
        ReportRecord fresh = generate(dto, creatorId);
        reportMapper.deleteById(old.getId());
        return fresh;
    }

    @Override
    public void delete(long id) {
        reportMapper.deleteById(id);
    }

    // ===== 内部方法 =====

    /**
     * 构建数据摘要（供提示词与降级模板共用）
     */
    private Map<String, Object> buildSummary(ReportGenerateDTO dto) {
        DimRegion region = regionMapper.selectById(dto.getRegionId());
        String regionName = region == null ? "区域" : region.getRegionName();

        Map<String, Object> summary = new HashMap<>();
        String period;
        if (dto.getPeriodType() == 1) {
            period = dto.getYear() + "年" + dto.getMonth() + "月";
            summary.put("title", regionName + " " + period + "碳排放监测月报");
            // 月报：当月总量 + 环比
            List<MonthlyVO> monthTotal = analysisService.monthlyTrend(dto.getRegionId(), dto.getYear(), null, null);
            BigDecimal current = monthTotal.stream()
                    .filter(m -> m.getMonth().equals(dto.getMonth()))
                    .map(MonthlyVO::getEmission).findFirst().orElse(BigDecimal.ZERO);
            summary.put("total_emission_yi", current.divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP));
            BigDecimal prev = monthTotal.stream()
                    .filter(m -> m.getMonth() == dto.getMonth() - 1)
                    .map(MonthlyVO::getEmission).findFirst().orElse(BigDecimal.ZERO);
            BigDecimal mom = prev.signum() > 0
                    ? current.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 2, RoundingMode.HALF_UP)
                    : null;
            summary.put("mom_rate", mom);
        } else {
            period = dto.getYear() + "年";
            summary.put("title", regionName + " " + period + "碳排放监测年报");
            KpiVO kpi = analysisService.kpi(dto.getRegionId(), dto.getYear());
            if (kpi != null && kpi.getTotalEmission() != null) {
                summary.put("total_emission_yi", kpi.getTotalEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP));
                summary.put("yoy_rate", kpi.getYoyRate());
                summary.put("intensity", kpi.getIntensity());
            }
        }
        summary.put("region_name", regionName);
        summary.put("period", period);

        // 行业结构（占比 %）
        List<StructureVO> industries = analysisService.structure(dto.getRegionId(), dto.getYear());
        summary.put("industries", industries.stream().map(s -> {
            Map<String, Object> item = new HashMap<>();
            item.put("name", s.getIndustryName());
            item.put("emission_yi", s.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP));
            return item;
        }).collect(Collectors.toList()));

        // 能源结构
        List<EnergyStructureVO> energies = analysisService.energyStructure(dto.getRegionId(), dto.getYear());
        summary.put("energies", energies.stream().map(s -> {
            Map<String, Object> item = new HashMap<>();
            item.put("name", s.getEnergyName());
            item.put("emission_yi", s.getEmission().divide(BigDecimal.valueOf(1e8), 2, RoundingMode.HALF_UP));
            return item;
        }).collect(Collectors.toList()));

        // 预警动态
        Map<String, Object> alertSummary = alertService.summary();
        summary.put("alerts", alertSummary);
        return summary;
    }

    /**
     * 降级模板报告（AI 服务不可用时保证可用）
     */
    private String buildFallbackReport(Map<String, Object> summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(summary.get("title")).append("\n\n");
        sb.append("## 一、总体情况\n");
        sb.append("- 报告期内区域碳排放总量为 **").append(summary.get("total_emission_yi"))
                .append(" 亿吨**；\n");
        Object yoy = summary.get("yoy_rate");
        if (yoy != null) {
            sb.append("- 同比 ").append(yoy).append("%；\n");
        }
        Object intensity = summary.get("intensity");
        if (intensity != null) {
            sb.append("- 碳强度 ").append(intensity).append(" tCO₂/万元。\n");
        }
        sb.append("\n## 二、行业结构\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> industries = (List<Map<String, Object>>) summary.get("industries");
        if (industries != null) {
            for (Map<String, Object> item : industries) {
                sb.append("- ").append(item.get("name")).append("：").append(item.get("emission_yi")).append(" 亿吨\n");
            }
        }
        sb.append("\n## 三、能源结构\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> energies = (List<Map<String, Object>>) summary.get("energies");
        if (energies != null) {
            for (Map<String, Object> item : energies) {
                sb.append("- ").append(item.get("name")).append("：").append(item.get("emission_yi")).append(" 亿吨\n");
            }
        }
        sb.append("\n## 四、预警动态\n");
        @SuppressWarnings("unchecked")
        Map<String, Object> alerts = (Map<String, Object>) summary.get("alerts");
        if (alerts != null) {
            sb.append("- 累计预警 ").append(alerts.get("ruleCount")).append(" 条，AI 检测异常 ")
                    .append(alerts.get("aiCount")).append(" 条，待确认 ").append(alerts.get("pending")).append(" 条\n");
        }
        sb.append("\n## 五、结论与建议\n");
        sb.append("- 加强重点行业能效监测，推动能源结构低碳转型；\n");
        sb.append("- 对未确认预警及时核查处置，防范排放异常风险。\n\n");
        sb.append("> 本报告由平台模板自动生成；配置 AI 服务（DeepSeek API Key）后可获得智能分析文本。\n");
        return sb.toString();
    }
}
