package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 月度活动数据明细表（模拟数据主表） */
@Data
@TableName("fact_energy_month")
public class FactEnergyMonth {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer regionId;

    private Integer industryId;

    private Integer energyId;

    private Integer year;

    private Integer month;

    /** 活动数据量（单位与能源品种一致） */
    private BigDecimal consumption;

    /** 1模拟生成 2Excel导入 */
    private Integer dataSource;

    private LocalDateTime createTime;
}
