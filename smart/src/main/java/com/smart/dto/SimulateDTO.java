package com.smart.dto;

import lombok.Data;

/**
 * 情景仿真参数
 */
@Data
public class SimulateDTO {

    private int regionId = 1;

    /** 煤炭占能源消费比重（%） */
    private double coalRatio;

    /** 工业占 GDP 比重（%） */
    private double industryRatio;

    /** 单位能耗年均下降率（%） */
    private double techEfficiency;

    /** GDP 年均增速（%） */
    private double gdpGrowth = 5.0;

    /** 仿真年数 */
    private int years = 10;

    /** 蒙特卡洛抽样次数 */
    private int mcIters = 300;
}
