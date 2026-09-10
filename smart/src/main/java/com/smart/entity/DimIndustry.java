package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 行业维度表 */
@Data
@TableName("dim_industry")
public class DimIndustry {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String industryCode;

    private String industryName;

    private Integer sortOrder;
}
