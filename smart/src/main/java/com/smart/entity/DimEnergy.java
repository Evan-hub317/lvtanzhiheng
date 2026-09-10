package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 能源品种维度表 */
@Data
@TableName("dim_energy")
public class DimEnergy {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String energyCode;

    private String energyName;

    /** 计量单位：万吨/万立方米/万千瓦时等 */
    private String unit;

    /** 1化石燃料 2电力 3热力 */
    private Integer energyType;

    private Integer sortOrder;
}
