package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 年度排放趋势点
 */
@Data
public class TrendVO {

    private Integer year;

    /** 年度排放量（tCO2） */
    private BigDecimal emission;
}
