package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 行业排放结构项
 */
@Data
public class StructureVO {

    private Integer industryId;

    private String industryName;

    /** 年度排放量（tCO2） */
    private BigDecimal emission;
}
