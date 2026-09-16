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

    /** 最后一个完整年（当年未过完时 = maxYear - 1），趋势/KPI 等年度分析用 */
    private Integer maxFullYear;

    /** 数据最晚年已生成的最大月份（判断当前年可生成月报的月份上限） */
    private Integer maxMonth;

    private long cityCount;

    private long countyCount;

    /** 核算结果条数（年度表） */
    private long emissionCount;
}
