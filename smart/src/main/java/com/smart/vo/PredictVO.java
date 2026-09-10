package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 排放预测结果（历史 + 未来 + 置信区间 + 达峰）
 */
@Data
public class PredictVO {

    /** 历史年度序列 */
    private List<Integer> historyYears;
    private List<BigDecimal> historyValues;

    /** 预测年度序列（与置信区间等长） */
    private List<Integer> years;
    private List<BigDecimal> values;
    private List<BigDecimal> lower;
    private List<BigDecimal> upper;

    /** 达峰年份（null 表示预测期内未达峰） */
    private Integer peakYear;
    private BigDecimal peakValue;

    /** lstm / linear */
    private String method;
}
