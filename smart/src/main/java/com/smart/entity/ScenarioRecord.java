package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 情景仿真记录表 */
@Data
@TableName("scenario_record")
public class ScenarioRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scenarioName;

    private Integer regionId;

    /** 0自定义 1基准 2低碳 3强化低碳 */
    private Integer presetType;

    /** 煤炭占能源消费比重（%） */
    private BigDecimal coalRatio;

    /** 工业占GDP比重（%） */
    private BigDecimal industryRatio;

    /** 单位能耗年均下降率（%） */
    private BigDecimal techEfficiency;

    /** 预测达峰年份 */
    private Integer peakYear;

    /** 预测峰值排放（tCO2） */
    private BigDecimal peakEmission;

    /** 完整参数组合 JSON 字符串 */
    private String paramsJson;

    /** 仿真结果摘要 JSON 字符串 */
    private String resultJson;

    private Long creatorId;

    private LocalDateTime createTime;
}
