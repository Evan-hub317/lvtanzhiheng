package com.smart.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.common.BizException;
import com.smart.entity.ChatMessage;
import com.smart.entity.ChatSession;
import com.smart.mapper.ChatMessageMapper;
import com.smart.mapper.ChatSessionMapper;
import com.smart.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 对话会话与消息持久化（AI 问答留痕）
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int TITLE_LENGTH = 24;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    @Override
    public long saveMessage(Long sessionId, Long userId, String question, String answer, List<Map<String, Object>> sources) {
        ChatSession session;
        if (sessionId != null) {
            session = sessionMapper.selectById(sessionId);
            if (session == null) {
                throw new BizException("会话不存在");
            }
        } else {
            session = new ChatSession();
            session.setUserId(userId);
            session.setTitle(question.length() > TITLE_LENGTH ? question.substring(0, TITLE_LENGTH) : question);
            sessionMapper.insert(session);
        }

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("user");
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(session.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        assistantMsg.setSources(sources == null || sources.isEmpty() ? null : JSONUtil.toJsonStr(sources));
        messageMapper.insert(assistantMsg);
        return session.getId();
    }

    @Override
    public List<ChatSession> listSessions(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdateTime));
    }

    @Override
    public List<ChatMessage> listMessages(long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
    }

    @Override
    public void deleteSession(long sessionId, Long userId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        if (!session.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作该会话");
        }
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }
}
