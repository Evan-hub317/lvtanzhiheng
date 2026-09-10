package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 年度核算聚合结果表（大屏趋势图/KPI 主要数据源） */
@Data
@TableName("fact_emission_year")
public class FactEmissionYear {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer regionId;

    private Integer industryId;

    private Integer energyId;

    private Integer year;

    /** 年度CO2排放量（tCO2） */
    private BigDecimal emission;

    /** 同比增速（%） */
    private BigDecimal yoyRate;

    private LocalDateTime createTime;
}
