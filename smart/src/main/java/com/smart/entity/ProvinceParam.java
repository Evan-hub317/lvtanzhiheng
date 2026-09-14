package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 省级经济能源特征参数（公开统计近似值）
 */
@Data
@TableName("province_param")
public class ProvinceParam {

    /** 省级区域ID */
    @TableId(type = IdType.INPUT)
    private Integer regionId;

    /** GDP（亿元） */
    private BigDecimal gdp;

    /** 第二产业占 GDP 比重（%） */
    private BigDecimal secondaryRatio;

    /** 煤炭占能源消费比重（%） */
    private BigDecimal coalRatio;

    /** 单位 GDP 能耗（吨标煤/万元） */
    private BigDecimal energyIntensity;

    /** 电网区域：HB/DB/HD/HZ/XB/NF */
    private String gridCode;

    /** 是否供暖省份：1是 0否 */
    private Integer heating;

    /** GDP 年均增速（%） */
    private BigDecimal gdpGrowth;
}
