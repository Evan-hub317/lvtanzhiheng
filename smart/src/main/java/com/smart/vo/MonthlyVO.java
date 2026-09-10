package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度排放点
 */
@Data
public class MonthlyVO {

    private Integer month;

    /** 月度排放量（tCO2） */
    private BigDecimal emission;
}
