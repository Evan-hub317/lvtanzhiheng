package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 情景记录（详情含仿真轨迹）
 */
@Data
public class ScenarioVO {

    private Long id;

    private String scenarioName;

    /** 0自定义 1基准 2低碳 3强化低碳 */
    private Integer presetType;

    private double coalRatio;

    private double industryRatio;

    private double techEfficiency;

    private double gdpGrowth;

    private Integer peakYear;

    private BigDecimal peakEmission;

    private LocalDateTime createTime;

    /** 详情：仿真轨迹 */
    private List<Integer> years;
    private List<BigDecimal> values;
    private List<BigDecimal> lower;
    private List<BigDecimal> upper;
}
