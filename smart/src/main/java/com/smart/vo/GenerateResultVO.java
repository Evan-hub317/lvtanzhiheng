package com.smart.vo;

import lombok.Data;

/**
 * 模拟数据生成结果
 */
@Data
public class GenerateResultVO {

    /** 生成活动数据条数 */
    private long totalCount;

    /** 市级区域数量 */
    private int countyCount;

    /** 注入的异常记录数（供异常检测演示对照） */
    private int anomalyCount;

    /** 数据年份范围 */
    private int startYear;
    private int endYear;

    /** 耗时（秒） */
    private long seconds;
}
