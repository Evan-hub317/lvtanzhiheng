package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 能源结构项
 */
@Data
public class EnergyStructureVO {

    private Integer energyId;

    private String energyName;

    /** 年度排放量（tCO2） */
    private BigDecimal emission;
}
