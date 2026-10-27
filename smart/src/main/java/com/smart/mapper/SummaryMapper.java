package com.smart.mapper;

import com.smart.vo.EnergyStructureVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.StructureVO;
import com.smart.vo.TrendVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模块化汇总表 Mapper（查询提速）：
 * 全国/省级口径的年度趋势、行业/能源结构、月度趋势、月报结构、城市排行
 * 均由核算完成后重建的小型汇总表提供，避免对百万行明细实时聚合。
 */
public interface SummaryMapper {

    // ===== 重建（核算完成后全量调用，顺序：清空 → 全国 → 各省） =====

    @Delete("DELETE FROM stat_year_total")
    int deleteYearTotal();

    @Insert("INSERT INTO stat_year_total (region_id, year, emission) " +
            "SELECT 1, year, ROUND(SUM(emission), 2) FROM fact_emission_year WHERE energy_id <= 6 GROUP BY year")
    int insertYearTotalNational();

    @Insert("INSERT INTO stat_year_total (region_id, year, emission) " +
            "SELECT IF(r.parent_id = 0, r.id, r.parent_id), y.year, ROUND(SUM(y.emission), 2) " +
            "FROM fact_emission_year y JOIN dim_region r ON r.id = y.region_id " +
            "WHERE y.energy_id <= 6 GROUP BY IF(r.parent_id = 0, r.id, r.parent_id), y.year")
    int insertYearTotalProvinces();

    @Delete("DELETE FROM stat_year_industry")
    int deleteYearIndustry();

    @Insert("INSERT INTO stat_year_industry (region_id, industry_id, year, emission) " +
            "SELECT 1, industry_id, year, ROUND(SUM(emission), 2) FROM fact_emission_year WHERE energy_id <= 6 GROUP BY industry_id, year")
    int insertYearIndustryNational();

    @Insert("INSERT INTO stat_year_industry (region_id, industry_id, year, emission) " +
            "SELECT IF(r.parent_id = 0, r.id, r.parent_id), y.industry_id, y.year, ROUND(SUM(y.emission), 2) " +
            "FROM fact_emission_year y JOIN dim_region r ON r.id = y.region_id " +
            "WHERE y.energy_id <= 6 GROUP BY IF(r.parent_id = 0, r.id, r.parent_id), y.industry_id, y.year")
    int insertYearIndustryProvinces();

    @Delete("DELETE FROM stat_year_energy")
    int deleteYearEnergy();

    @Insert("INSERT INTO stat_year_energy (region_id, energy_id, year, emission) " +
            "SELECT 1, energy_id, year, ROUND(SUM(emission), 2) FROM fact_emission_year GROUP BY energy_id, year")
    int insertYearEnergyNational();

    @Insert("INSERT INTO stat_year_energy (region_id, energy_id, year, emission) " +
            "SELECT IF(r.parent_id = 0, r.id, r.parent_id), y.energy_id, y.year, ROUND(SUM(y.emission), 2) " +
            "FROM fact_emission_year y JOIN dim_region r ON r.id = y.region_id " +
            "GROUP BY IF(r.parent_id = 0, r.id, r.parent_id), y.energy_id, y.year")
    int insertYearEnergyProvinces();

    @Delete("DELETE FROM stat_month_total")
    int deleteMonthTotal();

    @Insert("INSERT INTO stat_month_total (region_id, year, month, emission) " +
            "SELECT 1, year, month, ROUND(SUM(emission), 2) FROM fact_emission_month WHERE energy_id <= 6 GROUP BY year, month")
    int insertMonthTotalNational();

    @Insert("INSERT INTO stat_month_total (region_id, year, month, emission) " +
            "SELECT IF(r.parent_id = 0, r.id, r.parent_id), m.year, m.month, ROUND(SUM(m.emission), 2) " +
            "FROM fact_emission_month m JOIN dim_region r ON r.id = m.region_id " +
            "WHERE m.energy_id <= 6 GROUP BY IF(r.parent_id = 0, r.id, r.parent_id), m.year, m.month")
    int insertMonthTotalProvinces();

    @Delete("DELETE FROM stat_month_industry")
    int deleteMonthIndustry();

    @Insert("INSERT INTO stat_month_industry (region_id, industry_id, year, month, emission) " +
            "SELECT 1, industry_id, year, month, ROUND(SUM(emission), 2) FROM fact_emission_month WHERE energy_id <= 6 GROUP BY industry_id, year, month")
    int insertMonthIndustryNational();

    @Insert("INSERT INTO stat_month_industry (region_id, industry_id, year, month, emission) " +
            "SELECT IF(r.parent_id = 0, r.id, r.parent_id), m.industry_id, m.year, m.month, ROUND(SUM(m.emission), 2) " +
            "FROM fact_emission_month m JOIN dim_region r ON r.id = m.region_id " +
            "WHERE m.energy_id <= 6 GROUP BY IF(r.parent_id = 0, r.id, r.parent_id), m.industry_id, m.year, m.month")
    int insertMonthIndustryProvinces();

    @Delete("DELETE FROM stat_month_energy")
    int deleteMonthEnergy();

    @Insert("INSERT INTO stat_month_energy (region_id, energy_id, year, month, emission) " +
            "SELECT 1, energy_id, year, month, ROUND(SUM(emission), 2) FROM fact_emission_month GROUP BY energy_id, year, month")
    int insertMonthEnergyNational();

    @Insert("INSERT INTO stat_month_energy (region_id, energy_id, year, month, emission) " +
            "SELECT IF(r.parent_id = 0, r.id, r.parent_id), m.energy_id, m.year, m.month, ROUND(SUM(m.emission), 2) " +
            "FROM fact_emission_month m JOIN dim_region r ON r.id = m.region_id " +
            "GROUP BY IF(r.parent_id = 0, r.id, r.parent_id), m.energy_id, m.year, m.month")
    int insertMonthEnergyProvinces();

    @Delete("DELETE FROM stat_city_year")
    int deleteCityYear();

    @Insert("INSERT INTO stat_city_year (city_id, year, emission) " +
            "SELECT region_id, year, ROUND(SUM(emission), 2) FROM fact_emission_year WHERE energy_id <= 6 GROUP BY region_id, year")
    int insertCityYear();

    // ===== 查询（全国/省级口径） =====

    /** 年度总量趋势（全国或省，regionId=1 表示全国） */
    @Select("SELECT year, emission FROM stat_year_total " +
            "WHERE region_id = #{regionId} AND year BETWEEN #{startYear} AND #{endYear} ORDER BY year")
    List<TrendVO> selectTrend(@Param("regionId") int regionId,
                              @Param("startYear") int startYear,
                              @Param("endYear") int endYear);

    /** 年度行业结构（含占比由服务层计算） */
    @Select("SELECT i.industry_name AS industryName, i.id AS industryId, s.emission " +
            "FROM stat_year_industry s JOIN dim_industry i ON i.id = s.industry_id " +
            "WHERE s.region_id = #{regionId} AND s.year = #{year} ORDER BY s.industry_id")
    List<StructureVO> selectStructure(@Param("regionId") int regionId, @Param("year") int year);

    /** 年度能源结构（energyMax 用于口径过滤：6=直接口径；8=全部） */
    @Select("<script>" +
            "SELECT e.energy_name AS energyName, e.id AS energyId, s.emission " +
            "FROM stat_year_energy s JOIN dim_energy e ON e.id = s.energy_id " +
            "WHERE s.region_id = #{regionId} AND s.year = #{year} AND s.energy_id &lt;= #{energyMax} " +
            "ORDER BY s.energy_id" +
            "</script>")
    List<EnergyStructureVO> selectEnergy(@Param("regionId") int regionId,
                                         @Param("year") int year,
                                         @Param("energyMax") int energyMax);

    /** 月度总量趋势（全国或省） */
    @Select("SELECT month, emission FROM stat_month_total " +
            "WHERE region_id = #{regionId} AND year = #{year} ORDER BY month")
    List<MonthlyVO> selectMonthly(@Param("regionId") int regionId, @Param("year") int year);

    /** 月度行业结构（月报附图） */
    @Select("SELECT i.industry_name AS industryName, i.id AS industryId, s.emission " +
            "FROM stat_month_industry s JOIN dim_industry i ON i.id = s.industry_id " +
            "WHERE s.region_id = #{regionId} AND s.year = #{year} AND s.month = #{month} ORDER BY s.industry_id")
    List<StructureVO> selectStructureMonthly(@Param("regionId") int regionId,
                                             @Param("year") int year,
                                             @Param("month") int month);

    /** 全国地级行政区排行（直辖市不参与，仅参与省级排行；含人均） */
    @Select("SELECT c.id AS regionId, c.region_name AS regionName, s.emission, " +
            "ROUND(s.emission / c.population / 10000, 2) AS perCapita, c.is_municipality AS isMunicipality " +
            "FROM stat_city_year s JOIN dim_region c ON c.id = s.city_id " +
            "WHERE s.year = #{year} AND c.level = 2 ORDER BY s.emission DESC")
    List<RegionRankVO> selectCityRanking(@Param("year") int year);

    /** 省内各地级行政区排行（直辖市无下辖地级行政区，返回空；含人均） */
    @Select("SELECT c.id AS regionId, c.region_name AS regionName, s.emission, " +
            "ROUND(s.emission / c.population / 10000, 2) AS perCapita, c.is_municipality AS isMunicipality " +
            "FROM stat_city_year s JOIN dim_region c ON c.id = s.city_id " +
            "WHERE s.year = #{year} AND c.level = 2 AND c.parent_id = #{provinceId} ORDER BY s.emission DESC")
    List<RegionRankVO> selectCityRankingByProvince(@Param("provinceId") int provinceId,
                                                   @Param("year") int year);

    /** 省级排行（全国口径下各省年度总量，含直辖市标注与人均） */
    @Select("SELECT s.region_id AS regionId, r.region_name AS regionName, s.emission, " +
            "ROUND(s.emission / r.population / 10000, 2) AS perCapita, r.is_municipality AS isMunicipality " +
            "FROM stat_year_total s JOIN dim_region r ON r.id = s.region_id " +
            "WHERE s.year = #{year} AND s.region_id != 1 ORDER BY s.emission DESC")
    List<RegionRankVO> selectProvinceRanking(@Param("year") int year);

    /** 跨年连续月度总量序列（供 LSTM 预测；t 为连续月份序号） */
    @Select("SELECT (year - #{startYear}) * 12 + month AS t, emission " +
            "FROM stat_month_total WHERE region_id = #{regionId} " +
            "AND year BETWEEN #{startYear} AND #{endYear} ORDER BY year, month")
    List<com.smart.vo.MonthPointVO> selectMonthlyRange(@Param("regionId") int regionId,
                                                       @Param("startYear") int startYear,
                                                       @Param("endYear") int endYear);

    // ===== 大屏时段聚合（碳排放热力图：起止月口径） =====

    /** 时段总量（全国/省，直接口径） */
    @Select("SELECT COALESCE(SUM(emission), 0) FROM stat_month_total " +
            "WHERE region_id = #{regionId} " +
            "AND (year > #{sy} OR (year = #{sy} AND month >= #{sm})) " +
            "AND (year < #{ey} OR (year = #{ey} AND month <= #{em}))")
    BigDecimal selectPeriodTotal(@Param("regionId") int regionId,
                                 @Param("sy") int sy, @Param("sm") int sm,
                                 @Param("ey") int ey, @Param("em") int em);

    /** 各省时段总量排行（地图热力 + TOP10 数据源） */
    @Select("SELECT s.region_id AS regionId, r.region_name AS regionName, ROUND(SUM(s.emission), 2) AS emission " +
            "FROM stat_month_total s JOIN dim_region r ON r.id = s.region_id " +
            "WHERE s.region_id != 1 " +
            "AND (s.year > #{sy} OR (s.year = #{sy} AND s.month >= #{sm})) " +
            "AND (s.year < #{ey} OR (s.year = #{ey} AND s.month <= #{em})) " +
            "GROUP BY s.region_id, r.region_name ORDER BY emission DESC")
    List<RegionRankVO> selectPeriodProvinces(@Param("sy") int sy, @Param("sm") int sm,
                                             @Param("ey") int ey, @Param("em") int em);

    /** 时段能源结构（energyMin/Max 决定口径：1-6 直接、7-8 间接） */
    @Select("SELECT e.id AS energyId, e.energy_name AS energyName, ROUND(SUM(s.emission), 2) AS emission " +
            "FROM stat_month_energy s JOIN dim_energy e ON e.id = s.energy_id " +
            "WHERE s.region_id = #{regionId} AND s.energy_id BETWEEN #{energyMin} AND #{energyMax} " +
            "AND (s.year > #{sy} OR (s.year = #{sy} AND s.month >= #{sm})) " +
            "AND (s.year < #{ey} OR (s.year = #{ey} AND s.month <= #{em})) " +
            "GROUP BY e.id, e.energy_name ORDER BY e.id")
    List<EnergyStructureVO> selectPeriodEnergy(@Param("regionId") int regionId,
                                               @Param("energyMin") int energyMin, @Param("energyMax") int energyMax,
                                               @Param("sy") int sy, @Param("sm") int sm,
                                               @Param("ey") int ey, @Param("em") int em);

    /** 时段行业结构（直接口径） */
    @Select("SELECT i.id AS industryId, i.industry_name AS industryName, ROUND(SUM(s.emission), 2) AS emission " +
            "FROM stat_month_industry s JOIN dim_industry i ON i.id = s.industry_id " +
            "WHERE s.region_id = #{regionId} " +
            "AND (s.year > #{sy} OR (s.year = #{sy} AND s.month >= #{sm})) " +
            "AND (s.year < #{ey} OR (s.year = #{ey} AND s.month <= #{em})) " +
            "GROUP BY i.id, i.industry_name ORDER BY i.id")
    List<StructureVO> selectPeriodIndustry(@Param("regionId") int regionId,
                                           @Param("sy") int sy, @Param("sm") int sm,
                                           @Param("ey") int ey, @Param("em") int em);
}
