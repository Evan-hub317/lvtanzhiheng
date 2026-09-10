package com.smart.vo;

import lombok.Data;

/**
 * 数据现状概览
 */
@Data
public class DataStatusVO {

    /** 活动数据总条数 */
    private long totalCount;

    /** 年份范围 */
    private Integer minYear;
    private Integer maxYear;

    private long cityCount;

    private long countyCount;

    /** 核算结果条数（年度表） */
    private long emissionCount;
}
