package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 分析助手会话（对话与执行链路留痕）
 */
@Data
@TableName("agent_session")
public class AgentSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 会话标题（首问截取） */
    private String title;

    /** 问答消息数组 JSON：[{role, content}] */
    private String messagesJson;

    /** 执行链路步骤数组 JSON：[{name, args, summary, durationMs, chart}] */
    private String stepsJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
