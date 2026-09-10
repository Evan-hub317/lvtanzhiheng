package com.smart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.entity.SimResult;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SimResultMapper extends BaseMapper<SimResult> {

    /** 批量插入仿真轨迹（情景保存用） */
    @Insert("<script>" +
            "INSERT INTO sim_result (scenario_id, year, emission, lower_bound, upper_bound, create_time) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.scenarioId}, #{r.year}, #{r.emission}, #{r.lowerBound}, #{r.upperBound}, NOW())" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<SimResult> list);
}
