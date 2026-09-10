package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库文档表（政策文件） */
@Data
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String docName;

    /** pdf/docx/txt/md */
    private String docType;

    private String filePath;

    /** 1启用 0停用 */
    private Integer status;

    private Integer chunkCount;

    private Long uploaderId;

    private LocalDateTime uploadTime;
}
