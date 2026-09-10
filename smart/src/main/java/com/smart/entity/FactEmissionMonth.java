package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 月度核算聚合结果表 */
@Data
@TableName("fact_emission_month")
public class FactEmissionMonth {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer regionId;

    private Integer industryId;

    private Integer energyId;

    private Integer year;

    private Integer month;

    /** CO2排放量（tCO2） */
    private BigDecimal emission;

    /** 核算所用因子（留痕） */
    private BigDecimal factorValue;

    /** 核算所用氧化率（留痕） */
    private BigDecimal oxidRate;

    /** 碳强度（tCO2/万元GDP） */
    private BigDecimal intensity;

    /** 同比增速（%） */
    private BigDecimal yoyRate;

    private LocalDateTime createTime;
}
