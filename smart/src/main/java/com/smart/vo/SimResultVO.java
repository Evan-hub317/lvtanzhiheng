package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 情景仿真结果
 */
@Data
public class SimResultVO {

    private Integer baseYear;

    private BigDecimal baseEmission;

    private List<Integer> years;
    private List<BigDecimal> values;
    private List<BigDecimal> lower;
    private List<BigDecimal> upper;

    /** 达峰年份（null 表示未达峰） */
    private Integer peakYear;
    private BigDecimal peakValue;
}
