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

    /** 月度粒度时的月份（年度粒度下为 null） */
    private Integer month;

    /** 总排放量（tCO2） */
    private BigDecimal emission;

    /** 人均排放量（tCO2/人，按区域常住人口；人口固定口径下人均同比与总量同比一致） */
    private BigDecimal perCapita;

    /** 同比增速（%） */
    private BigDecimal yoyRate;
}
