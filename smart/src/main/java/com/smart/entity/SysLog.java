package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作日志表 */
@Data
@TableName("sys_log")
public class SysLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** 操作内容，如：执行核算、上传知识库文档 */
    private String operation;

    private String method;

    /** 请求参数（截断） */
    private String params;

    private String ip;

    /** 耗时（毫秒） */
    private Integer durationMs;

    /** 0成功 1失败 */
    private Integer status;

    private LocalDateTime createTime;
}
