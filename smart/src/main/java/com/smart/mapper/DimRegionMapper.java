package com.smart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.entity.DimRegion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DimRegionMapper extends BaseMapper<DimRegion> {

    /** 批量插入（模拟数据生成器扩展区县用） */
    @Insert("<script>" +
            "INSERT INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.regionCode}, #{r.regionName}, #{r.parentId}, #{r.level}, #{r.gdp}, #{r.sortOrder})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<DimRegion> list);
}
