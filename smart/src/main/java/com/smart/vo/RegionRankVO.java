package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 城市排放排行项
 */
@Data
public class RegionRankVO {

    private Integer regionId;

    private String regionName;

    /** 年度排放量（tCO2） */
    private BigDecimal emission;
}
