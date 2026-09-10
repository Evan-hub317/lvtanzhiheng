package com.smart.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.smart.common.Result;
import com.smart.entity.ChatMessage;
import com.smart.entity.ChatSession;
import com.smart.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 碳管家对话接口（RAG 检索与生成在 Python 算法服务，此处负责会话与留痕）
 */
@Tag(name = "AI 碳管家")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Data
    public static class SaveMessageDTO {
        private Long sessionId;
        private String question;
        private String answer;
        private List<Map<String, Object>> sources;
    }

    @Operation(summary = "保存一轮问答（自动创建/复用会话）")
    @PostMapping("/messages")
    public Result<Long> save(@RequestBody SaveMessageDTO dto) {
        long sessionId = chatService.saveMessage(dto.getSessionId(), StpUtil.getLoginIdAsLong(),
                dto.getQuestion(), dto.getAnswer(), dto.getSources());
        return Result.ok(sessionId);
    }

    @Operation(summary = "当前用户会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSession>> sessions() {
        return Result.ok(chatService.listSessions(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "会话消息列表")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable long id) {
        return Result.ok(chatService.listMessages(id));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> delete(@PathVariable long id) {
        chatService.deleteSession(id, StpUtil.getLoginIdAsLong());
        return Result.ok();
    }
}
