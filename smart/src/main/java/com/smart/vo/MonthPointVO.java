package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度排放序列点（t 为连续月份序号，从 1 开始）
 */
@Data
public class MonthPointVO {

    private Integer t;

    private BigDecimal emission;
}
