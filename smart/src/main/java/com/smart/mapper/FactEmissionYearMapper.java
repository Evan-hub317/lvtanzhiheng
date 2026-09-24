package com.smart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.entity.FactEmissionYear;
import com.smart.vo.DetailVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.RegionRankVO;

import java.math.BigDecimal;
import com.smart.vo.StructureVO;
import com.smart.vo.TrendVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 年度核算结果 Mapper：分析查询 SQL
 */
public interface FactEmissionYearMapper extends BaseMapper<FactEmissionYear> {

    /** 清空全部年度聚合 */
    @Delete("DELETE FROM fact_emission_year")
    int deleteAll();

    /** 清空指定年份范围年度聚合（重算幂等） */
    @Delete("DELETE FROM fact_emission_year WHERE year BETWEEN #{startYear} AND #{endYear}")
    int deleteYearRange(@Param("startYear") int startYear, @Param("endYear") int endYear);

    /** 全省（regionId=1）：年度排放总量趋势（可选能源筛选） */
    @Select("<script>" +
            "SELECT year, ROUND(SUM(emission), 2) AS emission FROM fact_emission_year " +
            "WHERE year BETWEEN #{startYear} AND #{endYear} " +
            "<if test='energyId != null'> AND energy_id = #{energyId}</if> " +
            "GROUP BY year ORDER BY year" +
            "</script>")
    List<TrendVO> selectTrendAll(@Param("startYear") int startYear,
                                 @Param("endYear") int endYear,
                                 @Param("energyId") Integer energyId);

    /** 指定市：该市及所辖区县年度排放总量趋势（可选能源筛选） */
    @Select("<script>" +
            "SELECT year, ROUND(SUM(emission), 2) AS emission FROM fact_emission_year " +
            "WHERE region_id IN (SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId}) " +
            "AND year BETWEEN #{startYear} AND #{endYear} " +
            "<if test='energyId != null'> AND energy_id = #{energyId}</if> " +
            "GROUP BY year ORDER BY year" +
            "</script>")
    List<TrendVO> selectTrendByRegion(@Param("regionId") int regionId,
                                      @Param("startYear") int startYear,
                                      @Param("endYear") int endYear,
                                      @Param("energyId") Integer energyId);

    /** 全省（regionId=1）：某年行业排放结构 */
    @Select("SELECT i.id AS industryId, i.industry_name AS industryName, ROUND(SUM(e.emission), 2) AS emission " +
            "FROM fact_emission_year e JOIN dim_industry i ON e.industry_id = i.id " +
            "WHERE e.year = #{year} GROUP BY i.id, i.industry_name ORDER BY i.id")
    List<StructureVO> selectStructureAll(@Param("year") int year);

    /** 指定市：某年行业排放结构 */
    @Select("SELECT i.id AS industryId, i.industry_name AS industryName, ROUND(SUM(e.emission), 2) AS emission " +
            "FROM fact_emission_year e JOIN dim_industry i ON e.industry_id = i.id " +
            "WHERE e.year = #{year} AND e.region_id IN " +
            "(SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId}) " +
            "GROUP BY i.id, i.industry_name ORDER BY i.id")
    List<StructureVO> selectStructureByRegion(@Param("regionId") int regionId, @Param("year") int year);

    /** 全国年度排放总量（tCO2，校准校验用；单位换算由调用方负责） */
    @Select("SELECT ROUND(SUM(emission), 2) FROM fact_emission_year WHERE year = #{year}")
    BigDecimal selectNationalTotal(@Param("year") int year);

    /** 某省下辖各市年度排放排行（降序） */
    @Select("SELECT c.id AS regionId, c.region_name AS regionName, ROUND(SUM(y.emission), 2) AS emission " +
            "FROM fact_emission_year y " +
            "JOIN dim_region c ON c.id = y.region_id " +
            "WHERE y.year = #{year} AND c.level = 2 AND c.parent_id = #{provinceId} " +
            "GROUP BY c.id, c.region_name ORDER BY emission DESC")
    List<RegionRankVO> selectCityRankingByProvince(@Param("provinceId") int provinceId, @Param("year") int year);

    /** 全国：某行业年度排放趋势 */
    @Select("<script>" +
            "SELECT year, ROUND(SUM(emission), 2) AS emission FROM fact_emission_year " +
            "WHERE year BETWEEN #{startYear} AND #{endYear} AND industry_id = #{industryId} " +
            "GROUP BY year ORDER BY year" +
            "</script>")
    List<TrendVO> selectTrendAllByIndustry(@Param("startYear") int startYear,
                                           @Param("endYear") int endYear,
                                           @Param("industryId") int industryId);

    /** 指定区域：某行业年度排放趋势 */
    @Select("<script>" +
            "SELECT year, ROUND(SUM(emission), 2) AS emission FROM fact_emission_year " +
            "WHERE region_id IN (SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId}) " +
            "AND year BETWEEN #{startYear} AND #{endYear} AND industry_id = #{industryId} " +
            "GROUP BY year ORDER BY year" +
            "</script>")
    List<TrendVO> selectTrendByRegionIndustry(@Param("regionId") int regionId,
                                              @Param("startYear") int startYear,
                                              @Param("endYear") int endYear,
                                              @Param("industryId") int industryId);

    /** 各省年度排放排行（tCO2，市级数据归并到省；单位换算由调用方负责） */
    @Select("SELECT p.id AS regionId, p.region_name AS regionName, ROUND(SUM(y.emission), 2) AS emission " +
            "FROM fact_emission_year y " +
            "JOIN dim_region r ON r.id = y.region_id " +
            "JOIN dim_region p ON p.id = IF(r.level = 2, r.parent_id, r.id) " +
            "WHERE y.year = #{year} GROUP BY p.id, p.region_name ORDER BY emission DESC")
    List<RegionRankVO> selectProvinceRanking(@Param("year") int year);

    /** 全省（regionId=1）：某年能源结构 */
    @Select("SELECT e.id AS energyId, e.energy_name AS energyName, ROUND(SUM(y.emission), 2) AS emission " +
            "FROM fact_emission_year y JOIN dim_energy e ON y.energy_id = e.id " +
            "WHERE y.year = #{year} GROUP BY e.id, e.energy_name ORDER BY e.id")
    List<EnergyStructureVO> selectEnergyStructureAll(@Param("year") int year);

    /** 指定市：某年能源结构 */
    @Select("SELECT e.id AS energyId, e.energy_name AS energyName, ROUND(SUM(y.emission), 2) AS emission " +
            "FROM fact_emission_year y JOIN dim_energy e ON y.energy_id = e.id " +
            "WHERE y.year = #{year} AND y.region_id IN " +
            "(SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId}) " +
            "GROUP BY e.id, e.energy_name ORDER BY e.id")
    List<EnergyStructureVO> selectEnergyStructureByRegion(@Param("regionId") int regionId, @Param("year") int year);

    /** 某年各市排放排行（数据粒度为市级，直接归并，降序） */
    @Select("SELECT c.id AS regionId, c.region_name AS regionName, ROUND(SUM(y.emission), 2) AS emission " +
            "FROM fact_emission_year y " +
            "JOIN dim_region c ON c.id = y.region_id " +
            "WHERE y.year = #{year} AND c.level = 2 " +
            "GROUP BY c.id, c.region_name ORDER BY emission DESC")
    List<RegionRankVO> selectRegionRanking(@Param("year") int year);

    /** 全省：排放明细分页（可选行业/能源/年份筛选） */
    @Select("<script>" +
            "SELECT r.region_name AS regionName, i.industry_name AS industryName, " +
            "e.energy_name AS energyName, y.year, y.emission, y.yoy_rate AS yoyRate " +
            "FROM fact_emission_year y " +
            "JOIN dim_region r ON r.id = y.region_id " +
            "JOIN dim_industry i ON i.id = y.industry_id " +
            "JOIN dim_energy e ON e.id = y.energy_id " +
            "WHERE 1=1 " +
            "<if test='year != null'> AND y.year = #{year}</if> " +
            "<if test='industryId != null'> AND y.industry_id = #{industryId}</if> " +
            "<if test='energyId != null'> AND y.energy_id = #{energyId}</if> " +
            "ORDER BY y.year DESC, y.emission DESC" +
            "</script>")
    IPage<DetailVO> selectDetailAll(Page<DetailVO> page,
                                    @Param("year") Integer year,
                                    @Param("industryId") Integer industryId,
                                    @Param("energyId") Integer energyId);

    /** 指定市：排放明细分页 */
    @Select("<script>" +
            "SELECT r.region_name AS regionName, i.industry_name AS industryName, " +
            "e.energy_name AS energyName, y.year, y.emission, y.yoy_rate AS yoyRate " +
            "FROM fact_emission_year y " +
            "JOIN dim_region r ON r.id = y.region_id " +
            "JOIN dim_industry i ON i.id = y.industry_id " +
            "JOIN dim_energy e ON e.id = y.energy_id " +
            "WHERE y.region_id IN " +
            "(SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId}) " +
            "<if test='year != null'> AND y.year = #{year}</if> " +
            "<if test='industryId != null'> AND y.industry_id = #{industryId}</if> " +
            "<if test='energyId != null'> AND y.energy_id = #{energyId}</if> " +
            "ORDER BY y.year DESC, y.emission DESC" +
            "</script>")
    IPage<DetailVO> selectDetailByRegion(Page<DetailVO> page,
                                         @Param("regionId") int regionId,
                                         @Param("year") Integer year,
                                         @Param("industryId") Integer industryId,
                                         @Param("energyId") Integer energyId);
}
