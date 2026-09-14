package com.smart.vo;

import lombok.Data;

/**
 * 核算执行结果
 */
@Data
public class CalcResultVO {

    /** 月度聚合行数 */
    private long monthRows;

    /** 年度聚合行数 */
    private long yearRows;

    /** 耗时（秒） */
    private long seconds;

    /** 全国数据校准校验报告（文本），全国口径生成时输出 */
    private String checkReport;
}
