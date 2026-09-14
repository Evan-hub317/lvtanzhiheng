package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 排放因子库（核算公式：排放量 = 活动数据 × 因子 × 氧化率） */
@Data
@TableName("factor_emission")
public class FactorEmission {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer energyId;

    /** 电网区域编码（仅电力因子使用）：HB/DB/HD/HZ/XB/NF */
    private String gridCode;

    /** tCO2/单位 */
    private BigDecimal factorValue;

    private BigDecimal oxidRate;

    /** 数据来源（指南名称/年份） */
    private String dataSource;

    /** 生效年份 */
    private Integer effectiveYear;

    private String remark;

    private LocalDateTime createTime;
}
