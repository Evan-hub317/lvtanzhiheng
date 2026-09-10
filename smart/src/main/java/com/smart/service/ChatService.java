package com.smart.service;

import com.smart.entity.ChatMessage;
import com.smart.entity.ChatSession;

import java.util.List;
import java.util.Map;

public interface ChatService {

    /**
     * 保存一轮问答（sessionId 为空时自动创建会话），返回会话ID
     */
    long saveMessage(Long sessionId, Long userId, String question, String answer, List<Map<String, Object>> sources);

    /**
     * 当前用户会话列表
     */
    List<ChatSession> listSessions(Long userId);

    /**
     * 会话消息列表
     */
    List<ChatMessage> listMessages(long sessionId);

    /**
     * 删除会话及消息
     */
    void deleteSession(long sessionId, Long userId);
}
