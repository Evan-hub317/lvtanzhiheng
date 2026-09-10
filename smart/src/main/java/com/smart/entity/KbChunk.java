package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库分块表（RAG 检索单位） */
@Data
@TableName("kb_chunk")
public class KbChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    /** 分块序号（从1开始） */
    private Integer chunkIndex;

    /** 分块文本（500字符，重叠50） */
    private String content;

    /** 文本向量（512维float32数组的 JSON 字符串，算法服务写入） */
    private String vector;

    private Integer tokenCount;

    private LocalDateTime createTime;
}
