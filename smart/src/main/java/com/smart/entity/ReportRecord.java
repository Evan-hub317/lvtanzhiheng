package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AIGC监测报告记录表 */
@Data
@TableName("report_record")
public class ReportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Integer regionId;

    /** 1月报 2年报 */
    private Integer periodType;

    private Integer reportYear;

    /** 年报为NULL */
    private Integer reportMonth;

    /** 报告正文（AI生成，含图表占位符） */
    private String content;

    /** 0生成中 1成功 2失败 */
    private Integer status;

    private Long creatorId;

    private LocalDateTime createTime;
}
