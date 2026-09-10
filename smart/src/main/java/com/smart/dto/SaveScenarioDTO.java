package com.smart.dto;

import lombok.Data;

/**
 * 保存情景参数（后端重算轨迹落库）
 */
@Data
public class SaveScenarioDTO {

    private String scenarioName;

    private int regionId = 1;

    /** 0自定义 1基准 2低碳 3强化低碳 */
    private Integer presetType = 0;

    private double coalRatio;

    private double industryRatio;

    private double techEfficiency;

    private double gdpGrowth = 5.0;

    private int years = 10;
}
