package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI对话消息表（RAG问答留痕） */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** user/assistant */
    private String role;

    private String content;

    /** 引用来源 JSON 字符串：[{doc_name, chunk_index, similarity}] */
    private String sources;

    private LocalDateTime createTime;
}
