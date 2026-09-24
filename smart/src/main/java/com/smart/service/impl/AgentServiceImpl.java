package com.smart.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.common.BizException;
import com.smart.dto.ReportGenerateDTO;
import com.smart.dto.SimulateDTO;
import com.smart.entity.AgentSession;
import com.smart.entity.AlertRecord;
import com.smart.entity.AlertRule;
import com.smart.entity.DimIndustry;
import com.smart.entity.DimRegion;
import com.smart.entity.ReportRecord;
import com.smart.mapper.AgentSessionMapper;
import com.smart.mapper.AlertRecordMapper;
import com.smart.mapper.AlertRuleMapper;
import com.smart.mapper.DimIndustryMapper;
import com.smart.mapper.DimRegionMapper;
import com.smart.mapper.FactEnergyMonthMapper;
import com.smart.service.AgentService;
import com.smart.service.AnalysisService;
import com.smart.service.AlertService;
import com.smart.service.CalcService;
import com.smart.service.ReportService;
import com.smart.service.SimulationService;
import com.smart.vo.CalcResultVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.KpiVO;
import com.smart.vo.PredictVO;
import com.smart.vo.SimResultVO;
import com.smart.vo.StructureVO;
import com.smart.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话式分析 Agent（LLM Function Calling 自主编排）
 * <p>
 * 循环：提问 → DeepSeek（工具列表）→ 执行工具（复用现有业务服务）→ 结果回填 → 再问 → 最终回答
 * SSE 事件：tool_call / tool_result / answer / done / error；工具图表以轻量指令回传前端渲染
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private static final int MAX_ROUNDS = 6;
    private static final int ANSWER_CHUNK = 30;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${deepseek.api-key:}")
    private String deepseekApiKey;

    private final AnalysisService analysisService;
    private final CalcService calcService;
    private final SimulationService simulationService;
    private final AlertService alertService;
    private final ReportService reportService;
    private final DimRegionMapper regionMapper;
    private final DimIndustryMapper industryMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final AgentSessionMapper sessionMapper;
    private final FactEnergyMonthMapper energyMonthMapper;

    // ============ 系统提示词与工具注册 ============

    private static final String SYSTEM_PROMPT = """
            你是'绿碳智衡'平台的 AI 数据分析助手，通过调用工具获取真实数据回答用户问题。
            要求：
            1. 自主决定调用哪些工具及顺序，用最少步骤完成任务；
            2. 区域参数使用中文名称（如"江苏"指江苏省、"全国"指全国口径）；
            3. 工具返回的数据是真实核算数据，回答必须基于数据，不得编造；涉及未来年份数据时，必须先调用 predict_emission 获得预测（含趋势图），再基于预测值回答或判断；
            4. 涉及阈值判断时调用 check_threshold 工具（用户未指定阈值时用平台默认规则）；
            5. 每次收到新的用户问题后，你会先被要求输出分析计划（2-4 句话）；获得工具调用能力后，严格执行计划，基于工具数据作答；
            6. 最终回答需包含：关键数据、判断结论、简短建议，300 字以内。
            """;

    /** 9 个工具定义（OpenAI function calling 格式） */
    private static final String TOOLS_JSON = """
            [{"type":"function","function":{"name":"query_kpi","description":"查询区域某年实际核算的核心指标（排放总量/同比/碳强度）；仅限已有核算数据的年份，未来年份请先调用 predict_emission","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名，如：全国、江苏、南京"},"year":{"type":"integer","description":"年份，默认最近完整年"}},"required":["region"]}}},
             {"type":"function","function":{"name":"query_trend","description":"查询区域历史年度排放趋势序列；仅限已有核算数据的年份，含未来年份请先调用 predict_emission","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名"},"start_year":{"type":"integer"},"end_year":{"type":"integer"}},"required":["region"]}}},
             {"type":"function","function":{"name":"query_structure","description":"查询区域某年行业排放结构","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名"},"year":{"type":"integer"}},"required":["region"]}}},
             {"type":"function","function":{"name":"run_calc","description":"重新执行碳核算（需先有活动数据），返回核算行数与校准报告","parameters":{"type":"object","properties":{}}}},
             {"type":"function","function":{"name":"predict_emission","description":"LSTM 预测区域未来排放趋势（含达峰判断与置信区间）","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名"}},"required":["region"]}}},
             {"type":"function","function":{"name":"detect_anomaly","description":"孤立森林 AI 异常检测，返回检出异常点与统计","parameters":{"type":"object","properties":{}}}},
             {"type":"function","function":{"name":"simulate_policy","description":"政策情景仿真：调整煤炭占比/工业占比/能效下降率，推演未来排放与达峰","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名"},"coal_ratio":{"type":"number","description":"煤炭占比%"},"industry_ratio":{"type":"number","description":"工业占GDP比重%"},"tech_efficiency":{"type":"number","description":"单位能耗年均下降率%"}},"required":["region"]}}},
             {"type":"function","function":{"name":"generate_report","description":"一键生成 AIGC 监测报告","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名"},"period_type":{"type":"integer","description":"1月报 2年报"},"year":{"type":"integer"},"month":{"type":"integer","description":"月报必填"}},"required":["region","period_type","year"]}}},
             {"type":"function","function":{"name":"check_threshold","description":"判断区域某年排放是否超过阈值（阈值未指定时用平台预警规则）","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名"},"year":{"type":"integer","description":"判断年份（可为预测年）"},"threshold":{"type":"number","description":"自定义阈值（亿吨）"}},"required":["region","year"]}}},
             {"type":"function","function":{"name":"query_energy_structure","description":"查询区域某年能源结构（返回各能源品种排放量与占比明细）","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名"},"year":{"type":"integer"}},"required":["region"]}}},
             {"type":"function","function":{"name":"query_region_ranking","description":"查询某年各市排放排行（返回各市排放量明细）；指定 region（省名）时返回该省下辖各市排行","parameters":{"type":"object","properties":{"region":{"type":"string","description":"省中文名（可选，不传为全国各市）"},"year":{"type":"integer","description":"年份，默认最近完整年"}}}}},
             {"type":"function","function":{"name":"query_monthly_trend","description":"查询区域某年各月排放（返回各月数值明细，可分析季节规律）","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名"},"year":{"type":"integer"}},"required":["region"]}}},
             {"type":"function","function":{"name":"query_industry_trend","description":"查询区域某行业年度排放趋势（行业名如：电力生产、工业、建筑、交通、农业）","parameters":{"type":"object","properties":{"region":{"type":"string","description":"区域中文名"},"industry":{"type":"string","description":"行业中文名"},"start_year":{"type":"integer"},"end_year":{"type":"integer"}},"required":["region","industry"]}}},
             {"type":"function","function":{"name":"query_alerts","description":"查询平台预警概况（待确认数、规则预警数、AI检测异常数、最近预警）","parameters":{"type":"object","properties":{}}}}]""";

    // ============ 会话管理 ============

    @Override
    public List<AgentSession> listSessions(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getUserId, userId)
                .orderByDesc(AgentSession::getUpdateTime));
    }

    @Override
    public AgentSession detail(long id) {
        return sessionMapper.selectById(id);
    }

    @Override
    public void delete(long id, Long userId) {
        AgentSession session = sessionMapper.selectById(id);
        if (session == null) {
            return;
        }
        if (!session.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作该会话");
        }
        sessionMapper.deleteById(id);
    }

    // ============ Agent 循环（SSE） ============

    @Override
    public void chat(Long sessionId, String question, Long userId, SseEmitter emitter) {
        List<Map<String, Object>> messages = new ArrayList<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        String finalAnswer = "";
        final Long[] effectiveSessionId = {sessionId};
        try {
            if (StrUtil.isBlank(deepseekApiKey)) {
                sendEvent(emitter, "error", Map.of("message", "未配置 DeepSeek API Key（系统环境变量 DEEPSEEK_API_KEY）"));
                emitter.complete();
                return;
            }

            // 1. 消息初始化（同一会话加载历史上下文，最近 6 条）
            // 注入当前日期：LLM 需据此正确理解"明年/今年/上月"等相对时间
            messages.add(Map.of("role", "system", "content",
                    "今天是" + java.time.LocalDate.now() + "。注意：涉及「明年」「今年」「上个月」等相对时间时，以此日期为基准换算。\n" + SYSTEM_PROMPT));
            if (sessionId != null) {
                AgentSession old = sessionMapper.selectById(sessionId);
                if (old != null && StrUtil.isNotBlank(old.getMessagesJson())) {
                    JSONArray history = JSONUtil.parseArray(old.getMessagesJson());
                    int from = Math.max(0, history.size() - 6);
                    for (int i = from; i < history.size(); i++) {
                        JSONObject m = history.getJSONObject(i);
                        messages.add(Map.of("role", m.getStr("role", "user"), "content", m.getStr("content", "")));
                    }
                }
            }
            messages.add(Map.of("role", "user", "content", question));

            // 2. 阶段一：分析规划（独立指令副本，强制只输出计划，防止直接回答/污染上下文）
            try {
                List<Map<String, Object>> planMessages = new ArrayList<>(messages);
                planMessages.add(Map.of("role", "user", "content",
                        "在调用工具之前，请仅输出你的分析计划（2-4 句话：说明对用户意图的理解与将执行的步骤）。"
                                + "注意：不要回答用户问题，不要给出任何数据、数值或结论。"));
                JSONObject planResp = callDeepSeek(planMessages, false);
                if (planResp != null) {
                    String plan = planResp.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getStr("content", "");
                    // 校验：真正的计划是 2-4 句话（不超过 200 字），超长说明 LLM 违规直接回答了问题，丢弃
                    if (StrUtil.isNotBlank(plan) && plan.length() <= 200) {
                        Map<String, Object> planStep = new HashMap<>();
                        planStep.put("type", "plan");
                        planStep.put("label", "分析问题");
                        planStep.put("content", plan);
                        planStep.put("status", "done");
                        steps.add(planStep);
                        sendEvent(emitter, "step", Map.of("type", "plan", "content", plan));
                        messages.add(Map.of("role", "assistant", "content", plan));
                    } else {
                        log.warn("分析规划输出异常（长度 {}），已丢弃", plan == null ? 0 : plan.length());
                    }
                }
            } catch (Exception e) {
                log.warn("分析规划生成失败（跳过）: {}", e.getMessage());
            }

            // 3. 阶段二：工具循环
            for (int round = 0; round < MAX_ROUNDS; round++) {
                JSONObject llmResp = callDeepSeek(messages, true);
                if (llmResp == null) {
                    sendEvent(emitter, "error", Map.of("message", "大模型调用失败，请稍后重试"));
                    break;
                }
                JSONObject message = llmResp.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
                JSONArray toolCalls = message.getJSONArray("tool_calls");

                if (toolCalls != null && !toolCalls.isEmpty()) {
                    // 记录 assistant 的 tool_calls 消息
                    messages.add(message.toBean(Map.class));
                    // 逐个执行工具
                    for (int i = 0; i < toolCalls.size(); i++) {
                        JSONObject call = toolCalls.getJSONObject(i);
                        String callId = call.getStr("id");
                        JSONObject fn = call.getJSONObject("function");
                        String toolName = fn.getStr("name");
                        Map<String, Object> args = parseArgs(fn.getStr("arguments"));

                        long t0 = System.currentTimeMillis();
                        sendEvent(emitter, "tool_call", Map.of("name", toolName, "args", args));
                        Map<String, Object> result;
                        try {
                            // 复制为可变 Map（工具可能返回不可变 Map，后续需写入耗时）
                            result = new HashMap<>(executeTool(toolName, args));
                        } catch (Exception e) {
                            log.warn("工具执行失败 {}: {}", toolName, e.getMessage());
                            result = new HashMap<>(Map.of("summary", "执行失败：" + e.getMessage()));
                        }
                        long cost = System.currentTimeMillis() - t0;
                        result.put("durationMs", cost);
                        // 步骤留痕补全：工具名/参数/状态（历史会话恢复时需要）
                        result.put("name", toolName);
                        result.put("args", args);
                        result.put("status", String.valueOf(result.get("summary")).contains("失败") ? "error" : "done");
                        steps.add(result);
                        sendEvent(emitter, "tool_result", result);

                        // 工具结果回填（LLM 只看文本摘要，图表不进上下文）
                        messages.add(Map.of("role", "tool", "tool_call_id", callId,
                                "content", String.valueOf(result.getOrDefault("summary", "完成"))));
                    }
                } else {
                    finalAnswer = message.getStr("content", "");
                    // 切片流式输出
                    for (int i = 0; i < finalAnswer.length(); i += ANSWER_CHUNK) {
                        sendEvent(emitter, "answer",
                                Map.of("content", finalAnswer.substring(i, Math.min(i + ANSWER_CHUNK, finalAnswer.length()))));
                    }
                    break;
                }
            }
            if (StrUtil.isBlank(finalAnswer)) {
                sendEvent(emitter, "answer", Map.of("content", "分析已完成，详见执行链路中的工具结果。"));
            }
            // 收尾：综合结论步骤（链路最后一步；由 LLM 单独生成一句话核心结论，与完整回答区分）
            if (steps.stream().noneMatch(s -> "conclusion".equals(s.get("type")))) {
                String core = "";
                try {
                    List<Map<String, Object>> conclMessages = new ArrayList<>(messages);
                    conclMessages.add(Map.of("role", "user", "content",
                            "请用一句话（20 字以内）总结本次分析的核心结论，直接输出结论本身，不要 Markdown 标记、不要补充说明。"));
                    JSONObject conclResp = callDeepSeek(conclMessages, false);
                    if (conclResp != null) {
                        core = conclResp.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getStr("content", "");
                    }
                } catch (Exception e) {
                    log.warn("核心结论生成失败（使用兜底提取）: {}", e.getMessage());
                }
                if (StrUtil.isBlank(core) || core.length() > 60) {
                    // 兜底：从最终回答提取"结论"句，剥离 Markdown
                    core = finalAnswer.replaceAll("\\*+", "")
                            .replaceAll("(?m)^\\s*[#>\\-]+\\s*", "")
                            .replaceAll("\\n{2,}", " ")
                            .trim();
                    int end = core.indexOf("。");
                    if (end > 0) {
                        core = core.substring(0, end + 1);
                    }
                    if (core.length() > 60) {
                        core = core.substring(0, 60) + "…";
                    }
                }
                Map<String, Object> conclusion = new HashMap<>();
                conclusion.put("type", "conclusion");
                conclusion.put("label", "综合结论");
                conclusion.put("summary", core);
                conclusion.put("status", "done");
                steps.add(conclusion);
                sendEvent(emitter, "step", Map.of("type", "conclusion", "summary", core));
            }
            // 先保存会话拿到真实 ID，再发 done——否则前端收不到新会话 ID，连续提问会分裂会话
            try {
                effectiveSessionId[0] = saveSession(effectiveSessionId[0], question, finalAnswer, messages, steps, userId);
            } catch (Exception e) {
                log.warn("会话保存失败", e);
            }
            sendEvent(emitter, "done", Map.of("sessionId", effectiveSessionId[0] == null ? -1L : effectiveSessionId[0]));
        } catch (Exception e) {
            log.error("Agent 执行异常", e);
            sendEvent(emitter, "error", Map.of("message", "分析失败：" + e.getMessage()));
        } finally {
            emitter.complete();
        }
    }

    // ============ DeepSeek 调用 ============

    private JSONObject callDeepSeek(List<Map<String, Object>> messages, boolean withTools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "deepseek-chat");
        body.put("messages", messages);
        if (withTools) {
            body.put("tools", JSONUtil.parseArray(TOOLS_JSON));
        }
        body.put("temperature", 0.3);
        String url = deepseekBaseUrl + "/chat/completions";
        try {
            String resp = HttpRequest.post(url)
                    .headerMap(Map.of("Authorization", "Bearer " + deepseekApiKey,
                            "Content-Type", "application/json"), true)
                    .body(JSONUtil.toJsonStr(body))
                    .timeout(120000)
                    .execute().body();
            return JSONUtil.parseObj(resp);
        } catch (Exception e) {
            log.error("DeepSeek 调用失败", e);
            return null;
        }
    }

    // ============ 9 个工具 ============

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeTool(String name, Map<String, Object> args) {
        switch (name) {
            case "query_kpi" -> {
                int regionId = resolveRegionId(args.get("region"));
                int fullYear = latestFullYear();
                int year = intArg(args, "year", fullYear);
                if (year > fullYear) {
                    // 未来年份：引导走独立的预测步骤（predict_emission）
                    return Map.of("summary", year + " 年为未来年份，暂无核算数据；请先调用 predict_emission 工具获得预测值");
                }
                KpiVO kpi = analysisService.kpi(regionId, year);
                if (kpi == null || kpi.getTotalEmission() == null) {
                    return Map.of("summary", "暂无该区域核算数据");
                }
                String s = String.format("%d 年排放总量 %s 亿吨", kpi.getYear(),
                        kpi.getTotalEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP));
                if (kpi.getYoyRate() != null) {
                    s += "，同比 " + kpi.getYoyRate() + "%";
                }
                if (kpi.getIntensity() != null) {
                    s += "，碳强度 " + kpi.getIntensity() + " tCO2/万元";
                }
                return Map.of("summary", s);
            }
            case "query_trend" -> {
                int regionId = resolveRegionId(args.get("region"));
                int fullYear = latestFullYear();
                int startYear = intArg(args, "start_year", 2021);
                int endYear = intArg(args, "end_year", fullYear);
                if (endYear > fullYear) {
                    // 含未来年份：引导走独立的预测步骤（predict_emission 提供含未来段的图表）
                    return Map.of("summary", endYear + " 年为未来年份，暂无核算数据；请先调用 predict_emission 工具获得含预测的完整趋势");
                }
                List<TrendVO> rows = analysisService.trend(regionId, startYear, endYear, null);
                return trendResult(rows, "排放趋势");
            }
            case "query_structure" -> {
                int regionId = resolveRegionId(args.get("region"));
                int year = intArg(args, "year", latestFullYear());
                List<StructureVO> rows = analysisService.structure(regionId, year);
                return structureResult(rows, year + " 年行业结构");
            }
            case "run_calc" -> {
                CalcResultVO result = calcService.execute(0, 0);
                String s = String.format("核算完成：月度 %d 行，年度 %d 行，耗时 %ds", result.getMonthRows(),
                        result.getYearRows(), result.getSeconds());
                if (result.getCheckReport() != null) {
                    s += "。" + result.getCheckReport().replace("\n", "；");
                }
                return Map.of("summary", s);
            }
            case "predict_emission" -> {
                int regionId = resolveRegionId(args.get("region"));
                PredictVO p = simulationService.predict(regionId);
                return predictResult(p);
            }
            case "detect_anomaly" -> {
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
            case "simulate_policy" -> {
                int regionId = resolveRegionId(args.get("region"));
                SimulateDTO dto = new SimulateDTO();
                dto.setRegionId(regionId);
                // 未指定参数时取该区域基准参数（维持现状）
                Map<String, Object> base = simulationService.baseParam(regionId);
                dto.setCoalRatio(numArg(args, "coal_ratio", ((Number) base.get("coalRatio")).doubleValue()));
                dto.setIndustryRatio(numArg(args, "industry_ratio", ((Number) base.get("industryRatio")).doubleValue()));
                dto.setTechEfficiency(numArg(args, "tech_efficiency", 1.5));
                dto.setYears(6);
                SimResultVO sim = simulationService.simulate(dto);
                Map<String, Object> result = new HashMap<>();
                String s = String.format("仿真完成（煤 %.1f%% / 工业 %.1f%% / 能效 %.1f%%）",
                        dto.getCoalRatio(), dto.getIndustryRatio(), dto.getTechEfficiency());
                if (sim.getPeakYear() != null) {
                    s += String.format("：%d 年达峰，峰值 %s 亿吨", sim.getPeakYear(),
                            sim.getPeakValue().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP));
                } else {
                    s += "：仿真期内未达峰";
                }
                result.put("summary", s);
                result.put("chart", Map.of("type", "trend", "title", "政策仿真轨迹",
                        "x", sim.getYears(),
                        "series", List.of(Map.of("name", "仿真排放(亿吨)", "data",
                                sim.getValues().stream().map(v -> v.divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP)).toList()))));
                return result;
            }
            case "generate_report" -> {
                int regionId = resolveRegionId(args.get("region"));
                ReportGenerateDTO dto = new ReportGenerateDTO();
                dto.setRegionId(regionId);
                dto.setPeriodType(intArg(args, "period_type", 2));
                dto.setYear(intArg(args, "year", latestFullYear()));
                dto.setMonth(args.get("month") != null ? intArg(args, "month", 1) : null);
                ReportRecord record = reportService.generate(dto, null);
                String brief = record.getContent() == null ? "" : record.getContent().substring(0, Math.min(200, record.getContent().length()));
                return Map.of("summary", "报告已生成：《" + record.getTitle() + "》。摘要：" + brief);
            }
            case "query_energy_structure" -> {
                int regionId = resolveRegionId(args.get("region"));
                int year = intArg(args, "year", latestFullYear());
                List<EnergyStructureVO> rows = analysisService.energyStructure(regionId, year);
                return energyResult(rows, year + " 年能源结构");
            }
            case "query_region_ranking" -> {
                int year = intArg(args, "year", latestFullYear());
                List<RegionRankVO> rows;
                String scope;
                if (args.get("region") != null) {
                    int provinceId = resolveRegionId(args.get("region"));
                    rows = analysisService.cityRankingByProvince(provinceId, year);
                    scope = resolveName(args.get("region")) + " 各市";
                } else {
                    rows = analysisService.regionRanking(year);
                    scope = "各市";
                }
                if (rows == null || rows.isEmpty()) {
                    return Map.of("summary", "暂无数据");
                }
                String detail = rows.stream().limit(10)
                        .map(r -> r.getRegionName() + " " + r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP) + "亿吨")
                        .collect(java.util.stream.Collectors.joining("，"));
                Map<String, Object> result = new HashMap<>();
                result.put("summary", year + " 年" + scope + "排放排行 Top10：" + detail);
                result.put("chart", Map.of("type", "bar", "title", year + " 年" + scope + "排放排行",
                        "x", rows.stream().limit(10).map(RegionRankVO::getRegionName).toList(),
                        "series", List.of(Map.of("name", "排放(亿吨)", "data",
                                rows.stream().limit(10).map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP)).toList()))));
                return result;
            }
            case "query_monthly_trend" -> {
                int regionId = resolveRegionId(args.get("region"));
                int year = intArg(args, "year", latestFullYear());
                List<MonthlyVO> rows = analysisService.monthlyTrend(regionId, year, null, null);
                if (rows == null || rows.isEmpty()) {
                    return Map.of("summary", "暂无数据");
                }
                String detail = rows.stream()
                        .map(r -> r.getMonth() + "月" + r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP) + "亿吨")
                        .collect(java.util.stream.Collectors.joining("，"));
                Map<String, Object> result = new HashMap<>();
                result.put("summary", year + " 年各月排放：" + detail);
                result.put("chart", Map.of("type", "bar", "title", year + " 年各月排放",
                        "x", rows.stream().map(r -> r.getMonth() + "月").toList(),
                        "series", List.of(Map.of("name", "排放(亿吨)", "data",
                                rows.stream().map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP)).toList()))));
                return result;
            }
            case "query_industry_trend" -> {
                int regionId = resolveRegionId(args.get("region"));
                int fullYear = latestFullYear();
                int startYear = intArg(args, "start_year", 2021);
                int endYear = intArg(args, "end_year", fullYear);
                if (endYear > fullYear) {
                    return Map.of("summary", endYear + " 年为未来年份，请先调用 predict_emission");
                }
                String industryName = String.valueOf(args.getOrDefault("industry", ""));
                int industryId = resolveIndustryId(industryName);
                List<TrendVO> rows = analysisService.industryTrend(regionId, startYear, endYear, industryId);
                return trendResult(rows, industryName + " 行业排放趋势");
            }
            case "query_alerts" -> {
                Map<String, Object> summary = alertService.summary();
                List<AlertRecord> top = alertRecordMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                        .orderByDesc(AlertRecord::getCreateTime)
                        .last("LIMIT 5"));
                String topDesc = top.stream().map(r -> (r.getRuleName() == null ? "" : r.getRuleName()) + "（"
                        + r.getYear() + (r.getMonth() == null ? "" : "-" + r.getMonth()) + "）")
                        .collect(java.util.stream.Collectors.joining("，"));
                return Map.of("summary", String.format("预警概况：待确认 %s 条，阈值规则预警 %s 条，AI 检测异常 %s 条；最近预警：%s",
                        summary.get("pending"), summary.get("ruleCount"), summary.get("aiCount"),
                        topDesc.isEmpty() ? "无" : topDesc));
            }
            case "check_threshold" -> {
                int regionId = resolveRegionId(args.get("region"));
                int year = intArg(args, "year", latestFullYear());
                // 目标年为未来/不完整年时使用预测值对比（如"明年会超标吗"）
                boolean predicted = year > latestFullYear();
                BigDecimal totalYi;
                if (predicted) {
                    PredictVO p = simulationService.predict(regionId);
                    int idx = p.getYears().indexOf(year);
                    if (idx < 0) {
                        return Map.of("summary", year + " 年不在预测范围内（预测至 " +
                                p.getYears().get(p.getYears().size() - 1) + " 年）");
                    }
                    totalYi = p.getValues().get(idx).divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP);
                } else {
                    List<TrendVO> rows = analysisService.trend(regionId, year, year, null);
                    if (rows.isEmpty()) {
                        return Map.of("summary", year + " 年暂无该区域数据");
                    }
                    totalYi = rows.get(0).getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP);
                }
                // 阈值优先级：用户指定 > 区域默认基值（上一个完整年排放 × 1.05，允许 5% 年增长）
                BigDecimal thresholdYi = null;
                String thresholdSource = "";
                if (args.get("threshold") != null) {
                    thresholdYi = BigDecimal.valueOf(numArg(args, "threshold", 0));
                    thresholdSource = "自定义";
                } else {
                    int baseYear = latestFullYear();
                    List<TrendVO> baseRows = analysisService.trend(regionId, baseYear, baseYear, null);
                    if (!baseRows.isEmpty() && baseRows.get(0).getEmission().signum() > 0) {
                        thresholdYi = baseRows.get(0).getEmission()
                                .multiply(BigDecimal.valueOf(1.05))
                                .divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP);
                        thresholdSource = "默认基值（" + baseYear + " 年 × 1.05）";
                    }
                }
                if (thresholdYi == null) {
                    return Map.of("summary", String.format("%d 年排放总量 %s 亿吨；该区域无历史数据可计算默认基值，请指定阈值后重新判断", year, totalYi));
                }
                boolean exceed = totalYi.compareTo(thresholdYi) > 0;
                return Map.of("summary", String.format("%d 年排放%s %s 亿吨 vs 阈值%s %s 亿吨：%s%s", year,
                        predicted ? "（预测值）" : "", totalYi,
                        thresholdSource.isEmpty() ? "" : "（" + thresholdSource + "）", thresholdYi,
                        exceed ? "超标" : "未超标", exceed ? " " + totalYi.subtract(thresholdYi) + " 亿吨" : ""));
            }
            default -> throw new BizException("未知工具：" + name);
        }
    }

    // ============ 工具结果组装（含图表指令） ============

    private Map<String, Object> trendResult(List<TrendVO> rows, String title) {
        Map<String, Object> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            result.put("summary", "暂无数据");
            return result;
        }
        // summary 含全部年度数值（LLM 可据此回答细节问题，图表仅为可视化）
        String detail = rows.stream()
                .map(r -> r.getYear() + "年" + r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP) + "亿吨")
                .collect(java.util.stream.Collectors.joining("，"));
        result.put("summary", title + "：" + detail);
        result.put("chart", Map.of("type", "trend", "title", title,
                "x", rows.stream().map(TrendVO::getYear).toList(),
                "series", List.of(Map.of("name", "排放(亿吨)", "data",
                        rows.stream().map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP)).toList()))));
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
            BigDecimal yi = r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal pct = total.signum() > 0
                    ? r.getEmission().multiply(BigDecimal.valueOf(100)).divide(total, 1, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return r.getIndustryName() + " " + yi + " 亿吨（占 " + pct + "%）";
        }).collect(java.util.stream.Collectors.joining("，"));
        result.put("summary", title + "：" + detail);
        result.put("chart", Map.of("type", "pie", "title", title,
                "x", rows.stream().map(StructureVO::getIndustryName).toList(),
                "series", List.of(Map.of("name", "排放(亿吨)", "data",
                        rows.stream().map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    /** 能源结构结果（与行业结构同构：明细进 summary，图表供可视化） */
    private Map<String, Object> energyResult(List<EnergyStructureVO> rows, String title) {
        Map<String, Object> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            result.put("summary", "暂无数据");
            return result;
        }
        BigDecimal total = rows.stream().map(EnergyStructureVO::getEmission).reduce(BigDecimal.ZERO, BigDecimal::add);
        String detail = rows.stream().map(r -> {
            BigDecimal yi = r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal pct = total.signum() > 0
                    ? r.getEmission().multiply(BigDecimal.valueOf(100)).divide(total, 1, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return r.getEnergyName() + " " + yi + " 亿吨（占 " + pct + "%）";
        }).collect(java.util.stream.Collectors.joining("，"));
        result.put("summary", title + "：" + detail);
        result.put("chart", Map.of("type", "pie", "title", title,
                "x", rows.stream().map(EnergyStructureVO::getEnergyName).toList(),
                "series", List.of(Map.of("name", "排放(亿吨)", "data",
                        rows.stream().map(r -> r.getEmission().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    private Map<String, Object> predictResult(PredictVO p) {
        Map<String, Object> result = new HashMap<>();
        String s = String.format("预测模型：%s；", "lstm".equals(p.getMethod()) ? "LSTM" : "趋势模型");
        if (p.getPeakYear() != null) {
            s += String.format("%d 年达峰（峰值 %s 亿吨）；", p.getPeakYear(),
                    p.getPeakValue().divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP));
        } else {
            s += "预测期内未达峰；";
        }
        // 逐年明细（LLM 可回答任意年份）
        for (int i = 0; i < p.getYears().size(); i++) {
            s += p.getYears().get(i) + "年" + p.getValues().get(i)
                    .divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP) + "亿吨";
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
                        vals.stream().map(v -> v.divide(BigDecimal.valueOf(1e8), 2, java.math.RoundingMode.HALF_UP)).toList()))));
        return result;
    }

    // ============ 辅助 ============

    private int latestFullYear() {
        try {
            // 完整年：数据最晚年若不足 12 个月则回退上一年
            com.smart.vo.DataStatusVO status = energyMonthMapper.selectStatus();
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

    private Map<String, Object> parseArgs(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return new HashMap<>();
        }
        try {
            return JSONUtil.parseObj(arguments).toBean(Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private int intArg(Map<String, Object> args, String key, int def) {
        Object v = args.get(key);
        return v == null ? def : Integer.parseInt(String.valueOf(v));
    }

    private double numArg(Map<String, Object> args, String key, double def) {
        Object v = args.get(key);
        return v == null ? def : Double.parseDouble(String.valueOf(v));
    }

    private void sendEvent(SseEmitter emitter, String event, Map<String, Object> data) {
        try {
            // 事件类型同时写入 event 行与 data 负载（双保险：前端任一路径解析成功即可）
            Map<String, Object> payload = new HashMap<>(data);
            payload.put("event", event);
            emitter.send(SseEmitter.event().name(event).data(payload, org.springframework.http.MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("SSE 推送失败（客户端断开？）: {}", e.getMessage());
        }
    }

    private long saveSession(Long sessionId, String question, String finalAnswer,
                             List<Map<String, Object>> messages, List<Map<String, Object>> steps, Long userId) {
        AgentSession session;
        if (sessionId != null) {
            session = sessionMapper.selectById(sessionId);
            if (session == null) {
                return 0;
            }
        } else {
            session = new AgentSession();
            session.setUserId(userId);
            session.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
            sessionMapper.insert(session);
        }
        // 历史问答 + 本轮问答（assistant 消息附带本轮执行链路 steps）
        List<Map<String, Object>> saved = new ArrayList<>();
        if (StrUtil.isNotBlank(session.getMessagesJson())) {
            JSONArray old = JSONUtil.parseArray(session.getMessagesJson());
            for (int i = 0; i < old.size(); i++) {
                JSONObject m = old.getJSONObject(i);
                Map<String, Object> savedMsg = new HashMap<>();
                savedMsg.put("role", m.getStr("role", "user"));
                savedMsg.put("content", m.getStr("content", ""));
                // 保留历史消息的执行链路（"查看执行链路"入口依赖）
                if (m.get("steps") != null) {
                    savedMsg.put("steps", m.get("steps"));
                }
                saved.add(savedMsg);
            }
        }
        saved.add(Map.of("role", "user", "content", question));
        Map<String, Object> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", finalAnswer);
        assistantMsg.put("steps", steps);
        saved.add(assistantMsg);
        session.setMessagesJson(JSONUtil.toJsonStr(saved));
        sessionMapper.updateById(session);
        return session.getId();
    }
}
