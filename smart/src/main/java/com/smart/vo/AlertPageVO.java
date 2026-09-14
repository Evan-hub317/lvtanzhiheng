package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警列表项（含区域/行业名称）
 */
@Data
public class AlertPageVO {

    private Long id;

    /** 1阈值规则 2孤立森林AI检测 */
    private Integer detectType;

    private String ruleName;

    private Integer regionId;
    private String regionName;

    private Integer industryId;
    private String industryName;

    private Integer year;

    private Integer month;

    /** 实际值（排放量 tCO2 或增幅%） */
    private BigDecimal actualValue;

    /** 阈值 */
    private BigDecimal thresholdValue;

    /** 异常分数（AI检测，0~1 越接近 1 越异常） */
    private BigDecimal anomalyScore;

    /** 0待确认 1已确认 */
    private Integer status;

    private String handler;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
