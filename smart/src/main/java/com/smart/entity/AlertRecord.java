package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预警记录表 */
@Data
@TableName("alert_record")
public class AlertRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 触发规则ID（AI检测为NULL） */
    private Integer ruleId;

    private String ruleName;

    /** 1阈值规则 2孤立森林AI检测 */
    private Integer detectType;

    private Integer regionId;

    private Integer industryId;

    private Integer year;

    /** 年度预警为NULL */
    private Integer month;

    private BigDecimal actualValue;

    private BigDecimal thresholdValue;

    /** 异常分数（AI检测，越接近1越异常） */
    private BigDecimal anomalyScore;

    /** 0待确认 1已确认 */
    private Integer status;

    private String handler;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
