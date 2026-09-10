package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 排放明细行（区域×行业×能源×年度）
 */
@Data
public class DetailVO {

    private String regionName;

    private String industryName;

    private String energyName;

    private Integer year;

    /** 排放量（tCO2） */
    private BigDecimal emission;

    /** 同比增速（%） */
    private BigDecimal yoyRate;
}
