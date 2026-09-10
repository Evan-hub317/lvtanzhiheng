package com.smart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.entity.FactEnergyMonth;
import com.smart.vo.DataStatusVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

public interface FactEnergyMonthMapper extends BaseMapper<FactEnergyMonth> {

    /** 清除模拟生成的活动数据（Excel 导入数据保留） */
    @Delete("DELETE FROM fact_energy_month WHERE data_source = 1")
    int deleteSimulated();

    /** 数据现状统计 */
    @Select("SELECT COUNT(*) AS totalCount, MIN(year) AS minYear, MAX(year) AS maxYear FROM fact_energy_month")
    DataStatusVO selectStatus();
}
