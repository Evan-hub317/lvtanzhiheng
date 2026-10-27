package com.smart.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 排放明细查询条件（年度/月度粒度 + 区间筛选 + 排序）
 */
@Data
public class DetailQueryDTO {

    private int pageNum = 1;

    private int pageSize = 10;

    /** 区域ID：1=全国，省ID=该省（含下辖），市ID=该市（含直辖县级） */
    private int regionId = 1;

    /** 粒度：year 年度 / month 月度 */
    private String granularity = "year";

    private Integer year;

    /** 月度粒度时的月份（1-12） */
    private Integer month;

    private Integer industryId;

    private Integer energyId;

    /** 总排放量区间（tCO2，含边界） */
    private BigDecimal minEmission;

    private BigDecimal maxEmission;

    /** 人均排放量区间（tCO2/人，含边界） */
    private BigDecimal minPerCapita;

    private BigDecimal maxPerCapita;

    /** 排序字段：time 时间 / emission 总排放量 / perCapita 人均排放量 / yoyRate 同比 */
    private String sortField = "time";

    /** 排序方向：asc / desc */
    private String sortOrder = "desc";
}
