package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 年度核心指标
 */
@Data
public class KpiVO {

    private Integer year;

    /** 排放总量（tCO2） */
    private BigDecimal totalEmission;

    /** 同比增速（%） */
    private BigDecimal yoyRate;

    /** 碳强度（tCO2/万元GDP） */
    private BigDecimal intensity;
}
