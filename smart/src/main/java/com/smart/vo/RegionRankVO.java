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

    /** 人均碳排放（tCO2/人，总量/常住人口；人口缺失为 null） */
    private BigDecimal perCapita;

    /** 是否直辖市（1=是，前端标注用） */
    private Integer isMunicipality;
}
