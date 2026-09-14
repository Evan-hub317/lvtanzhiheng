package com.smart.dto;

import lombok.Data;

/**
 * 报告生成参数
 */
@Data
public class ReportGenerateDTO {

    private int regionId = 1;

    /** 1月报 2年报 */
    private int periodType = 2;

    private int year;

    /** 月报必填 */
    private Integer month;
}
