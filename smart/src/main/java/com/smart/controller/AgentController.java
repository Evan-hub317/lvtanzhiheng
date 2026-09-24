package com.smart.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.smart.common.Result;
import com.smart.entity.AgentSession;
import com.smart.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 分析助手（对话式分析 Agent）
 * 问答走 SSE 流式：tool_call / tool_result / answer / done / error 事件
 */
@Tag(name = "AI 分析助手")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Data
    public static class ChatDTO {
        private Long sessionId;
        private String question;
    }

    @Operation(summary = "对话式分析（SSE 流式，工具调用过程实时推送）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatDTO dto) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        Long userId = StpUtil.getLoginIdAsLong();
        // 异步执行 Agent 循环，避免阻塞请求线程
        new Thread(() -> agentService.chat(dto.getSessionId(), dto.getQuestion(), userId, emitter)).start();
        return emitter;
    }

    @Operation(summary = "会话列表")
    @GetMapping("/sessions")
    public Result<List<AgentSession>> sessions() {
        return Result.ok(agentService.listSessions(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "会话详情（消息与执行链路）")
    @GetMapping("/sessions/{id}")
    public Result<AgentSession> detail(@PathVariable long id) {
        return Result.ok(agentService.detail(id));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> delete(@PathVariable long id) {
        agentService.delete(id, StpUtil.getLoginIdAsLong());
        return Result.ok();
    }
}
