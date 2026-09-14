package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 异常检测数据点（区县×行业×能源×月）
 */
@Data
public class AnomalyPointVO {

    private Integer regionId;

    private Integer industryId;

    private Integer energyId;

    private Integer year;

    private Integer month;

    private BigDecimal emission;
}
