package com.smart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.entity.FactEmissionMonth;
import com.smart.vo.AnomalyPointVO;
import com.smart.vo.MonthPointVO;
import com.smart.vo.MonthlyVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 月度核算结果 Mapper：核算引擎 SQL（排放量 = 活动数据 × 因子 × 氧化率）
 */
public interface FactEmissionMonthMapper extends BaseMapper<FactEmissionMonth> {

    /** 清空全部核算结果（生成新数据时调用） */
    @Delete("DELETE FROM fact_emission_month")
    int deleteAll();

    /** 清空指定年份范围核算结果（重算幂等） */
    @Delete("DELETE FROM fact_emission_month WHERE year BETWEEN #{startYear} AND #{endYear}")
    int deleteYearRange(@Param("startYear") int startYear, @Param("endYear") int endYear);

    /**
     * 按能源品种核算：INSERT...SELECT 聚合，单能源一次 SQL，因子值内联留痕
     * 单位换算：燃料类（energy_id 1~5）消费单位为万吨、因子为 tCO2/吨，需 ×10000；
     * 天然气（万m³）/电力（万kWh）/热力（万GJ）与因子单位一致，直接相乘
     */
    @Insert("INSERT INTO fact_emission_month " +
            "(region_id, industry_id, energy_id, year, month, emission, factor_value, oxid_rate, create_time) " +
            "SELECT region_id, industry_id, energy_id, year, month, " +
            "ROUND(SUM(consumption) * IF(energy_id <= 5, 10000, 1) * #{factor} * #{oxid}, 4), " +
            "#{factor}, #{oxid}, NOW() " +
            "FROM fact_energy_month " +
            "WHERE energy_id = #{energyId} AND year BETWEEN #{startYear} AND #{endYear} " +
            "GROUP BY region_id, industry_id, year, month")
    int insertCalcMonth(@Param("energyId") int energyId,
                        @Param("factor") BigDecimal factor,
                        @Param("oxid") BigDecimal oxid,
                        @Param("startYear") int startYear,
                        @Param("endYear") int endYear);

    /** 计算碳强度：月排放 / (区域年GDP/12 × 10000)，单位 tCO2/万元 */
    @Update("UPDATE fact_emission_month m JOIN dim_region r ON m.region_id = r.id " +
            "SET m.intensity = ROUND(m.emission / (r.gdp / 12 * 10000), 6) " +
            "WHERE r.gdp IS NOT NULL AND r.gdp > 0 AND m.year BETWEEN #{startYear} AND #{endYear}")
    int updateIntensity(@Param("startYear") int startYear, @Param("endYear") int endYear);

    /** 年度聚合：月度表汇总落库 */
    @Insert("INSERT INTO fact_emission_year (region_id, industry_id, energy_id, year, emission, create_time) " +
            "SELECT region_id, industry_id, energy_id, year, ROUND(SUM(emission), 4), NOW() " +
            "FROM fact_emission_month WHERE year BETWEEN #{startYear} AND #{endYear} " +
            "GROUP BY region_id, industry_id, energy_id, year")
    int insertYearAgg(@Param("startYear") int startYear, @Param("endYear") int endYear);

    /**
     * 电力核算（区域电网因子）：按省份所属电网区域取对应因子
     * 市级区域 → 上级省 → province_param.grid_code → 六大区域电网因子
     */
    @Insert("INSERT INTO fact_emission_month " +
            "(region_id, industry_id, energy_id, year, month, emission, factor_value, oxid_rate, create_time) " +
            "SELECT f.region_id, f.industry_id, 7, f.year, f.month, " +
            "ROUND(SUM(f.consumption) * fe.factor_value * fe.oxid_rate, 4), fe.factor_value, fe.oxid_rate, NOW() " +
            "FROM fact_energy_month f " +
            "JOIN dim_region r ON r.id = f.region_id " +
            "JOIN dim_region p ON p.id = IF(r.level = 2, r.parent_id, r.id) " +
            "JOIN province_param pp ON pp.region_id = p.id " +
            "JOIN factor_emission fe ON fe.energy_id = 7 AND fe.grid_code = pp.grid_code " +
            "WHERE f.energy_id = 7 AND f.year BETWEEN #{startYear} AND #{endYear} " +
            "GROUP BY f.region_id, f.industry_id, f.year, f.month, fe.factor_value, fe.oxid_rate")
    int insertCalcMonthPower(@Param("startYear") int startYear, @Param("endYear") int endYear);

    /** 年度同比增速：与上一年度对比（%） */
    @Update("UPDATE fact_emission_year a " +
            "LEFT JOIN fact_emission_year b " +
            "ON a.region_id = b.region_id AND a.industry_id = b.industry_id " +
            "AND a.energy_id = b.energy_id AND a.year = b.year + 1 " +
            "SET a.yoy_rate = ROUND((a.emission - b.emission) / b.emission * 100, 4) " +
            "WHERE b.emission IS NOT NULL AND b.emission > 0 " +
            "AND a.year BETWEEN #{startYear} AND #{endYear}")
    int updateYoy(@Param("startYear") int startYear, @Param("endYear") int endYear);

    /** 全省：某年月度排放趋势（可选行业/能源筛选，观察季节规律） */
    @Select("<script>" +
            "SELECT month, ROUND(SUM(emission), 2) AS emission FROM fact_emission_month " +
            "WHERE year = #{year} " +
            "<if test='industryId != null'> AND industry_id = #{industryId}</if> " +
            "<if test='energyId != null'> AND energy_id = #{energyId}</if> " +
            "GROUP BY month ORDER BY month" +
            "</script>")
    List<MonthlyVO> selectMonthlyAll(@Param("year") int year,
                                     @Param("industryId") Integer industryId,
                                     @Param("energyId") Integer energyId);

    /** 指定市：某年月度排放趋势（可选行业/能源筛选） */
    @Select("<script>" +
            "SELECT month, ROUND(SUM(emission), 2) AS emission FROM fact_emission_month " +
            "WHERE year = #{year} AND region_id IN " +
            "(SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId}) " +
            "<if test='industryId != null'> AND industry_id = #{industryId}</if> " +
            "<if test='energyId != null'> AND energy_id = #{energyId}</if> " +
            "GROUP BY month ORDER BY month" +
            "</script>")
    List<MonthlyVO> selectMonthlyByRegion(@Param("regionId") int regionId,
                                          @Param("year") int year,
                                          @Param("industryId") Integer industryId,
                                          @Param("energyId") Integer energyId);

    /** 全省：跨年连续月度排放序列（t 为连续月份序号，供 LSTM 预测） */
    @Select("SELECT (year - #{startYear}) * 12 + month AS t, ROUND(SUM(emission), 2) AS emission " +
            "FROM fact_emission_month WHERE year BETWEEN #{startYear} AND #{endYear} " +
            "GROUP BY year, month ORDER BY year, month")
    List<MonthPointVO> selectMonthlyRangeAll(@Param("startYear") int startYear, @Param("endYear") int endYear);

    /** 全量检测数据点：区县×行业×能源×月（供孤立森林批量检测） */
    @Select("SELECT region_id AS regionId, industry_id AS industryId, energy_id AS energyId, " +
            "year, month, ROUND(SUM(emission), 2) AS emission " +
            "FROM fact_emission_month GROUP BY region_id, industry_id, energy_id, year, month " +
            "ORDER BY region_id, industry_id, energy_id, year, month")
    List<AnomalyPointVO> selectAnomalyPoints();

    /** 行业月度排放（阈值环比扫描用） */
    @Select("SELECT industry_id AS industryId, year, month, ROUND(SUM(emission), 2) AS emission " +
            "FROM fact_emission_month GROUP BY industry_id, year, month " +
            "ORDER BY industry_id, year, month")
    List<AnomalyPointVO> selectIndustryMonthly();

    /** 指定市：跨年连续月度排放序列 */
    @Select("SELECT (year - #{startYear}) * 12 + month AS t, ROUND(SUM(emission), 2) AS emission " +
            "FROM fact_emission_month WHERE year BETWEEN #{startYear} AND #{endYear} " +
            "AND region_id IN (SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId}) " +
            "GROUP BY year, month ORDER BY year, month")
    List<MonthPointVO> selectMonthlyRangeByRegion(@Param("regionId") int regionId,
                                                  @Param("startYear") int startYear,
                                                  @Param("endYear") int endYear);
}
