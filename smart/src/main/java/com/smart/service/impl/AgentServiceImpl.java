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
import com.smart.service.AgentToolService;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    /** 仅用于启动前的配置检查；实际调用走 Spring AI DeepSeekChatModel（spring.ai.deepseek.*） */
    @Value("${spring.ai.deepseek.api-key:}")
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
    private final ResourceLoader resourceLoader;
    private final ChatClient.Builder chatClientBuilder;
    /** 14 个分析工具（@Tool 声明式注册，schema 由注解生成；执行仍在手工循环内逐轮调用） */
    private final AgentToolService toolService;

    /** ChatClient 懒构建（Builder 由 Spring AI 自动配置注入） */
    private volatile ChatClient chatClient;

    private ChatClient chatClient() {
        if (chatClient == null) {
            synchronized (this) {
                if (chatClient == null) {
                    chatClient = chatClientBuilder.build();
                }
            }
        }
        return chatClient;
    }

    // ============ 系统提示词与工具注册 ============
    // 提示词统一放在 resources/prompts/*.st，通过 Spring AI PromptTemplate 渲染（便于调整与复用）

    /** 渲染提示词模板（显式 UTF-8，避免中文乱码）。
     * 加载顺序：工作目录下的外部 prompts/ 目录（部署后可热改，每次请求实时读取，无需重启/重打包）
     * → jar 内置 classpath 资源（本地开发/单 jar 部署零配置兜底） */
    private String renderPrompt(String fileName, Map<String, Object> vars) {
        try {
            Path external = Paths.get("prompts", fileName);
            String template = Files.exists(external)
                    ? Files.readString(external, StandardCharsets.UTF_8)
                    : resourceLoader.getResource("classpath:prompts/" + fileName)
                            .getContentAsString(StandardCharsets.UTF_8);
            return new PromptTemplate(template).render(vars);
        } catch (IOException e) {
            throw new BizException("提示词模板加载失败：" + fileName);
        }
    }

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
            // 模板内注入当前日期：LLM 需据此正确理解"明年/今年/上月"等相对时间
            messages.add(Map.of("role", "system", "content",
                    renderPrompt("agent-system.st", Map.of("today", LocalDate.now()))));
            if (sessionId != null) {
                AgentSession old = sessionMapper.selectById(sessionId);
                if (old != null && StrUtil.isNotBlank(old.getMessagesJson())) {
                    JSONArray history = JSONUtil.parseArray(old.getMessagesJson());
                    int from = Math.max(0, history.size() - 6);
                    for (int i = from; i < history.size(); i++) {
                        JSONObject m = history.getJSONObject(i);
                        // 完整回放原始消息对象：思考模式要求 reasoning_content 等字段原样回传
                        messages.add(m.toBean(Map.class));
                    }
                }
            }
            messages.add(Map.of("role", "user", "content", question));

            // 2. 阶段一：分析规划（独立指令副本，强制只输出计划，防止直接回答/污染上下文）
            try {
                List<Map<String, Object>> planMessages = new ArrayList<>(messages);
                planMessages.add(Map.of("role", "user", "content", renderPrompt("agent-plan.st", Map.of())));
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
                        // 完整保留模型返回的 message 对象：思考模式下 reasoning_content 必须原样回传，
                        // 否则下一轮请求会被 API 拒绝
                        messages.add(planResp.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").toBean(Map.class));
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
                    conclMessages.add(Map.of("role", "user", "content", renderPrompt("agent-conclusion.st", Map.of())));
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
        try {
            ChatResponse resp = chatClient()
                    .prompt()
                    .messages(toSpringMessages(messages))
                    .options(buildOptions(withTools))
                    .call()
                    .chatResponse();
            Message out = resp.getResult().getOutput();
            if (out == null) {
                log.error("大模型响应为空");
                return null;
            }
            // 还原为 OpenAI 兼容 JSON（与旧实现同构）：上层解析、会话持久化零改动
            JSONObject json = new JSONObject();
            JSONObject msg = new JSONObject();
            msg.set("role", "assistant");
            msg.set("content", out.getText() == null ? "" : out.getText());
            // 思考模式：reasoning_content 必须随消息原样回传（多轮硬约束），一并落库
            if (out instanceof DeepSeekAssistantMessage dsMsg && StrUtil.isNotBlank(dsMsg.getReasoningContent())) {
                msg.set("reasoning_content", dsMsg.getReasoningContent());
            }
            if (out instanceof AssistantMessage am && am.hasToolCalls()) {
                JSONArray calls = new JSONArray();
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    JSONObject fn = new JSONObject();
                    fn.set("name", tc.name());
                    fn.set("arguments", tc.arguments());
                    calls.add(new JSONObject()
                            .set("id", tc.id())
                            .set("type", StrUtil.isBlank(tc.type()) ? "function" : tc.type())
                            .set("function", fn));
                }
                msg.set("tool_calls", calls);
            }
            JSONArray choices = new JSONArray();
            choices.add(new JSONObject().set("message", msg));
            json.set("choices", choices);
            return json;
        } catch (Exception e) {
            log.error("大模型调用失败", e);
            return null;
        }
    }

    /** 业务消息列表（Map，含 reasoning_content/tool_calls 原始结构）→ Spring AI Message */
    private List<Message> toSpringMessages(List<Map<String, Object>> messages) {
        List<Message> out = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            String role = String.valueOf(m.get("role"));
            String content = String.valueOf(m.getOrDefault("content", ""));
            switch (role) {
                case "system" -> out.add(new SystemMessage(content));
                case "user" -> out.add(new UserMessage(content));
                case "tool" -> out.add(new ToolResponseMessage(List.of(
                        new ToolResponseMessage.ToolResponse(
                                String.valueOf(m.getOrDefault("tool_call_id", "")), "", content))));
                case "assistant" -> {
                    String reasoning = m.get("reasoning_content") == null
                            ? null : String.valueOf(m.get("reasoning_content"));
                    List<AssistantMessage.ToolCall> calls = new ArrayList<>();
                    if (m.get("tool_calls") instanceof JSONArray arr) {
                        for (int i = 0; i < arr.size(); i++) {
                            JSONObject c = arr.getJSONObject(i);
                            JSONObject fn = c.getJSONObject("function");
                            calls.add(new AssistantMessage.ToolCall(c.getStr("id"), c.getStr("type"),
                                    fn.getStr("name"), fn.getStr("arguments")));
                        }
                    }
                    if (calls.isEmpty()) {
                        out.add(new DeepSeekAssistantMessage(content, reasoning));
                    } else {
                        out.add(new DeepSeekAssistantMessage(content, reasoning, Map.of(), calls));
                    }
                }
                default -> log.warn("未知消息角色被跳过: {}", role);
            }
        }
        return out;
    }

    private DeepSeekChatOptions buildOptions(boolean withTools) {
        DeepSeekChatOptions.Builder b = DeepSeekChatOptions.builder().temperature(0.3);
        if (withTools) {
            b.toolCallbacks(buildToolCallbacks());
        }
        // 关键：关闭框架内部工具执行——工具由业务层手工执行并逐轮推送 SSE 事件（执行链路可视化）
        b.internalToolExecutionEnabled(false);
        return b.build();
    }

    /** 工具回调由 AgentToolService 的 @Tool 注解生成（schema 唯一来源），执行仍在业务层手工完成 */
    private List<ToolCallback> buildToolCallbacks() {
        return List.of(MethodToolCallbackProvider.builder().toolObjects(toolService).build().getToolCallbacks());
    }

    // ============ 9 个工具 ============

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeTool(String name, Map<String, Object> args) {
        switch (name) {
            case "query_kpi" -> {
                return toolService.queryKpi(strArg(args, "region"), intArg(args, "year"));
            }
            case "query_trend" -> {
                return toolService.queryTrend(strArg(args, "region"), intArg(args, "start_year"), intArg(args, "end_year"));
            }
            case "query_structure" -> {
                return toolService.queryStructure(strArg(args, "region"), intArg(args, "year"));
            }
            case "run_calc" -> {
                return toolService.runCalc();
            }
            case "predict_emission" -> {
                return toolService.predictEmission(strArg(args, "region"));
            }
            case "detect_anomaly" -> {
                return toolService.detectAnomaly();
            }
            case "simulate_policy" -> {
                return toolService.simulatePolicy(strArg(args, "region"),
                        dblArg(args, "coal_ratio"), dblArg(args, "industry_ratio"), dblArg(args, "tech_efficiency"));
            }
            case "generate_report" -> {
                return toolService.generateReport(strArg(args, "region"),
                        intArg(args, "period_type"), intArg(args, "year"), intArg(args, "month"));
            }
            case "query_energy_structure" -> {
                return toolService.queryEnergyStructure(strArg(args, "region"), intArg(args, "year"));
            }
            case "query_region_ranking" -> {
                return toolService.queryRegionRanking(strArg(args, "region"), intArg(args, "year"));
            }
            case "query_monthly_trend" -> {
                return toolService.queryMonthlyTrend(strArg(args, "region"), intArg(args, "year"));
            }
            case "query_industry_trend" -> {
                return toolService.queryIndustryTrend(strArg(args, "region"), strArg(args, "industry"),
                        intArg(args, "start_year"), intArg(args, "end_year"));
            }
            case "query_alerts" -> {
                return toolService.queryAlerts();
            }
            case "check_threshold" -> {
                return toolService.checkThreshold(strArg(args, "region"), intArg(args, "year"), dblArg(args, "threshold"));
            }
            default -> throw new BizException("未知工具：" + name);
        }
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

    private String strArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private Integer intArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : Integer.parseInt(String.valueOf(v));
    }

    private Double dblArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : Double.parseDouble(String.valueOf(v));
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
