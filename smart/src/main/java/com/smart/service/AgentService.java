package com.smart.service;

import com.smart.entity.AgentSession;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AgentService {

    /**
     * 对话式分析：LLM Function Calling 自主编排工具，SSE 推送执行链路与最终回答
     *
     * @param sessionId 会话ID（null 则新建）
     * @param question  用户自然语言问题
     */
    void chat(Long sessionId, String question, Long userId, SseEmitter emitter);

    /**
     * 会话列表（当前用户）
     */
    List<AgentSession> listSessions(Long userId);

    /**
     * 会话详情（含消息与执行链路）
     */
    AgentSession detail(long id);

    /**
     * 删除会话
     */
    void delete(long id, Long userId);
}
