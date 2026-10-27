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

    /** 是否直辖市：1=既是省也是市（其下无区县，自身参与省/市两级口径） */
    private Integer isMunicipality;

    /** GDP（亿元），用于碳强度计算 */
    private BigDecimal gdp;

    /** 常住人口（万人），人均碳排放口径 */
    private BigDecimal population;

    private Integer sortOrder;
}
