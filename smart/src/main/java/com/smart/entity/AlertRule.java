package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预警规则表（定时任务扫描） */
@Data
@TableName("alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String ruleName;

    /** 1环比超限 2同比超限 3总量上限 */
    private Integer ruleType;

    /** 监测维度：region区域/industry行业 */
    private String dimension;

    /** 维度ID，NULL表示全部 */
    private Integer dimensionId;

    /** 阈值（1/2为增幅%，3为总量tCO2） */
    private BigDecimal thresholdValue;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;
}
