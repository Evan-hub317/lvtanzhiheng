package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 区域维度表（省/市两级） */
@Data
@TableName("dim_region")
public class DimRegion {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String regionCode;

    private String regionName;

    /** 父级ID，0为省级 */
    private Integer parentId;

    /** 1省 2市 */
    private Integer level;

    /** GDP（亿元），用于碳强度计算 */
    private BigDecimal gdp;

    private Integer sortOrder;
}
