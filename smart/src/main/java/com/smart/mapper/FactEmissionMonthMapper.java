package com.smart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.dto.DetailQueryDTO;
import com.smart.entity.FactEmissionMonth;
import com.smart.vo.DetailVO;
import com.smart.vo.AnomalyPointVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.MonthPointVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.StructureVO;
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
            "JOIN dim_region p ON p.id = IF(r.level = 1, r.id, r.parent_id) " +
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
            "<if test='energyId == null'> AND energy_id &lt;= 6</if>" +
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
            "<if test='energyId == null'> AND energy_id &lt;= 6</if>" +
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
            "FROM fact_emission_month WHERE year BETWEEN #{startYear} AND #{endYear} AND energy_id <= 6 " +
            "GROUP BY year, month ORDER BY year, month")
    List<MonthPointVO> selectMonthlyRangeAll(@Param("startYear") int startYear, @Param("endYear") int endYear);

    /** 全省：某月行业排放结构（月报附图用） */
    @Select("SELECT i.id AS industryId, i.industry_name AS industryName, ROUND(SUM(m.emission), 2) AS emission " +
            "FROM fact_emission_month m JOIN dim_industry i ON m.industry_id = i.id " +
            "WHERE m.year = #{year} AND m.month = #{month} AND m.energy_id <= 6 " +
            "GROUP BY i.id, i.industry_name ORDER BY i.id")
    List<StructureVO> selectStructureMonthlyAll(@Param("year") int year, @Param("month") int month);

    /** 指定区域：某月行业排放结构 */
    @Select("SELECT i.id AS industryId, i.industry_name AS industryName, ROUND(SUM(m.emission), 2) AS emission " +
            "FROM fact_emission_month m JOIN dim_industry i ON m.industry_id = i.id " +
            "WHERE m.year = #{year} AND m.month = #{month} AND m.region_id IN " +
            "(SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId}) " +
            "AND m.energy_id <= 6 GROUP BY i.id, i.industry_name ORDER BY i.id")
    List<StructureVO> selectStructureMonthlyByRegion(@Param("regionId") int regionId,
                                                     @Param("year") int year,
                                                     @Param("month") int month);

    /** 全量检测数据点：区县×行业×能源×月（供孤立森林批量检测；仅直接能源 energy_id≤6——
     *  电力/热力为间接排放单列展示、不计总量，不参与异常检测，与阈值规则口径一致） */
    @Select("SELECT region_id AS regionId, industry_id AS industryId, energy_id AS energyId, " +
            "year, month, ROUND(SUM(emission), 2) AS emission " +
            "FROM fact_emission_month WHERE energy_id <= 6 " +
            "GROUP BY region_id, industry_id, energy_id, year, month " +
            "ORDER BY region_id, industry_id, energy_id, year, month")
    List<AnomalyPointVO> selectAnomalyPoints();

    /** 行业×能源月度排放（阈值扫描用，全国口径；directOnly=true 仅直接能源 energy_id≤6，与平台总量口径一致） */
    @Select("<script>" +
            "SELECT industry_id AS industryId, energy_id AS energyId, year, month, ROUND(SUM(emission), 2) AS emission " +
            "FROM fact_emission_month WHERE 1=1 " +
            "<if test='directOnly'> AND energy_id &lt;= 6</if> " +
            "GROUP BY industry_id, energy_id, year, month " +
            "ORDER BY industry_id, energy_id, year, month" +
            "</script>")
    List<AnomalyPointVO> selectIndustryEnergyMonthly(@Param("directOnly") boolean directOnly);

    /** 指定市：跨年连续月度排放序列 */
    @Select("SELECT (year - #{startYear}) * 12 + month AS t, ROUND(SUM(emission), 2) AS emission " +
            "FROM fact_emission_month WHERE year BETWEEN #{startYear} AND #{endYear} " +
            "AND region_id IN (SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId}) " +
            "AND energy_id <= 6 GROUP BY year, month ORDER BY year, month")
    List<MonthPointVO> selectMonthlyRangeByRegion(@Param("regionId") int regionId,
                                                  @Param("startYear") int startYear,
                                                  @Param("endYear") int endYear);

    // ===== 大屏时段聚合（碳排放热力图：起止月口径；regionId 为空=全国） =====

    /** 时段总量（直接口径） */
    @Select("<script>" +
            "SELECT COALESCE(SUM(emission), 0) FROM fact_emission_month " +
            "WHERE energy_id &lt;= 6 " +
            "<if test='regionId != null'> AND region_id IN (SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId})</if> " +
            "AND (year &gt; #{sy} OR (year = #{sy} AND month &gt;= #{sm})) " +
            "AND (year &lt; #{ey} OR (year = #{ey} AND month &lt;= #{em}))" +
            "</script>")
    BigDecimal selectPeriodTotal(@Param("regionId") Integer regionId,
                                 @Param("sy") int sy, @Param("sm") int sm,
                                 @Param("ey") int ey, @Param("em") int em);

    /** 时段能源结构（energyMin/Max 决定口径：1-6 直接、7-8 间接） */
    @Select("<script>" +
            "SELECT e.id AS energyId, e.energy_name AS energyName, ROUND(SUM(m.emission), 2) AS emission " +
            "FROM fact_emission_month m JOIN dim_energy e ON e.id = m.energy_id " +
            "WHERE m.energy_id BETWEEN #{energyMin} AND #{energyMax} " +
            "<if test='regionId != null'> AND m.region_id IN (SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId})</if> " +
            "AND (m.year &gt; #{sy} OR (m.year = #{sy} AND m.month &gt;= #{sm})) " +
            "AND (m.year &lt; #{ey} OR (m.year = #{ey} AND m.month &lt;= #{em})) " +
            "GROUP BY e.id, e.energy_name ORDER BY e.id" +
            "</script>")
    List<EnergyStructureVO> selectPeriodEnergy(@Param("regionId") Integer regionId,
                                               @Param("energyMin") int energyMin, @Param("energyMax") int energyMax,
                                               @Param("sy") int sy, @Param("sm") int sm,
                                               @Param("ey") int ey, @Param("em") int em);

    /** 时段行业结构（直接口径） */
    @Select("<script>" +
            "SELECT i.id AS industryId, i.industry_name AS industryName, ROUND(SUM(m.emission), 2) AS emission " +
            "FROM fact_emission_month m JOIN dim_industry i ON i.id = m.industry_id " +
            "WHERE m.energy_id &lt;= 6 " +
            "<if test='regionId != null'> AND m.region_id IN (SELECT id FROM dim_region WHERE id = #{regionId} OR parent_id = #{regionId})</if> " +
            "AND (m.year &gt; #{sy} OR (m.year = #{sy} AND m.month &gt;= #{sm})) " +
            "AND (m.year &lt; #{ey} OR (m.year = #{ey} AND m.month &lt;= #{em})) " +
            "GROUP BY i.id, i.industry_name ORDER BY i.id" +
            "</script>")
    List<StructureVO> selectPeriodIndustry(@Param("regionId") Integer regionId,
                                           @Param("sy") int sy, @Param("sm") int sm,
                                           @Param("ey") int ey, @Param("em") int em);

    /** 各省时段总量（汇总表为空时地图/TOP10 的回退数据源；直辖市 parent=0 归自身） */
    @Select("SELECT IF(r.parent_id = 0, r.id, r.parent_id) AS regionId, p.region_name AS regionName, ROUND(SUM(m.emission), 2) AS emission " +
            "FROM fact_emission_month m JOIN dim_region r ON r.id = m.region_id " +
            "JOIN dim_region p ON p.id = IF(r.parent_id = 0, r.id, r.parent_id) " +
            "WHERE m.energy_id <= 6 " +
            "AND (m.year > #{sy} OR (m.year = #{sy} AND m.month >= #{sm})) " +
            "AND (m.year < #{ey} OR (m.year = #{ey} AND m.month <= #{em})) " +
            "GROUP BY IF(r.parent_id = 0, r.id, r.parent_id), p.region_name ORDER BY emission DESC")
    List<RegionRankVO> selectPeriodProvinces(@Param("sy") int sy, @Param("sm") int sm,
                                             @Param("ey") int ey, @Param("em") int em);

    /** 全省：排放明细分页（月度粒度；同比=去年同月） */
    @Select("<script>" +
            "SELECT r.region_name AS regionName, i.industry_name AS industryName, " +
            "e.energy_name AS energyName, y.year, y.month, y.emission, " +
            "CASE WHEN p.emission IS NOT NULL AND p.emission &gt; 0 " +
            "THEN ROUND((y.emission - p.emission) / p.emission * 100, 2) END AS yoyRate, " +
            "ROUND(y.emission / r.population / 10000, 4) AS perCapita " +
            "FROM fact_emission_month y " +
            "JOIN dim_region r ON r.id = y.region_id " +
            "JOIN dim_industry i ON i.id = y.industry_id " +
            "JOIN dim_energy e ON e.id = y.energy_id " +
            "LEFT JOIN fact_emission_month p ON p.region_id = y.region_id AND p.industry_id = y.industry_id " +
            "AND p.energy_id = y.energy_id AND p.year = y.year - 1 AND p.month = y.month " +
            "WHERE 1=1 " +
            "<if test='q.year != null'> AND y.year = #{q.year}</if> " +
            "<if test='q.month != null'> AND y.month = #{q.month}</if> " +
            "<if test='q.industryId != null'> AND y.industry_id = #{q.industryId}</if> " +
            "<if test='q.energyId != null'> AND y.energy_id = #{q.energyId}</if> " +
            "<if test='q.minEmission != null'> AND y.emission &gt;= #{q.minEmission}</if> " +
            "<if test='q.maxEmission != null'> AND y.emission &lt;= #{q.maxEmission}</if> " +
            "<if test='q.minPerCapita != null'> AND (y.emission / r.population / 10000) &gt;= #{q.minPerCapita}</if> " +
            "<if test='q.maxPerCapita != null'> AND (y.emission / r.population / 10000) &lt;= #{q.maxPerCapita}</if> " +
            "ORDER BY ${sortSql}" +
            "</script>")
    IPage<DetailVO> selectDetailAllMonth(Page<DetailVO> page, @Param("q") DetailQueryDTO q, @Param("sortSql") String sortSql);

    /** 指定区域：排放明细分页（月度粒度；同比=去年同月） */
    @Select("<script>" +
            "SELECT r.region_name AS regionName, i.industry_name AS industryName, " +
            "e.energy_name AS energyName, y.year, y.month, y.emission, " +
            "CASE WHEN p.emission IS NOT NULL AND p.emission &gt; 0 " +
            "THEN ROUND((y.emission - p.emission) / p.emission * 100, 2) END AS yoyRate, " +
            "ROUND(y.emission / r.population / 10000, 4) AS perCapita " +
            "FROM fact_emission_month y " +
            "JOIN dim_region r ON r.id = y.region_id " +
            "JOIN dim_industry i ON i.id = y.industry_id " +
            "JOIN dim_energy e ON e.id = y.energy_id " +
            "LEFT JOIN fact_emission_month p ON p.region_id = y.region_id AND p.industry_id = y.industry_id " +
            "AND p.energy_id = y.energy_id AND p.year = y.year - 1 AND p.month = y.month " +
            "WHERE y.region_id IN " +
            "(SELECT id FROM dim_region WHERE id = #{q.regionId} OR parent_id = #{q.regionId}) " +
            "<if test='q.year != null'> AND y.year = #{q.year}</if> " +
            "<if test='q.month != null'> AND y.month = #{q.month}</if> " +
            "<if test='q.industryId != null'> AND y.industry_id = #{q.industryId}</if> " +
            "<if test='q.energyId != null'> AND y.energy_id = #{q.energyId}</if> " +
            "<if test='q.minEmission != null'> AND y.emission &gt;= #{q.minEmission}</if> " +
            "<if test='q.maxEmission != null'> AND y.emission &lt;= #{q.maxEmission}</if> " +
            "<if test='q.minPerCapita != null'> AND (y.emission / r.population / 10000) &gt;= #{q.minPerCapita}</if> " +
            "<if test='q.maxPerCapita != null'> AND (y.emission / r.population / 10000) &lt;= #{q.maxPerCapita}</if> " +
            "ORDER BY ${sortSql}" +
            "</script>")
    IPage<DetailVO> selectDetailByRegionMonth(Page<DetailVO> page, @Param("q") DetailQueryDTO q, @Param("sortSql") String sortSql);

    /** 某月省级行政区排行（含人均；可选行业/能源筛选） */
    @Select("<script>" +
            "SELECT p.id AS regionId, p.region_name AS regionName, ROUND(SUM(m.emission), 2) AS emission, " +
            "ROUND(SUM(m.emission) / p.population / 10000, 2) AS perCapita, p.is_municipality AS isMunicipality " +
            "FROM fact_emission_month m " +
            "JOIN dim_region r ON r.id = m.region_id " +
            "JOIN dim_region p ON p.id = IF(r.level = 1, r.id, r.parent_id) " +
            "WHERE m.year = #{year} AND m.month = #{month} " +
            "<if test='industryId != null'> AND m.industry_id = #{industryId}</if>" +
            "<if test='energyId != null'> AND m.energy_id = #{energyId}</if>" +
            "<if test='energyId == null'> AND m.energy_id &lt;= 6</if>" +
            "GROUP BY p.id, p.region_name, p.population, p.is_municipality ORDER BY emission DESC" +
            "</script>")
    List<RegionRankVO> selectMonthRankingProvince(@Param("year") int year, @Param("month") int month,
                                                  @Param("industryId") Integer industryId,
                                                  @Param("energyId") Integer energyId);

    /** 某月地级行政区排行（provinceId 为 null 时全国；含人均；可选行业/能源筛选） */
    @Select("<script>" +
            "SELECT c.id AS regionId, c.region_name AS regionName, ROUND(SUM(m.emission), 2) AS emission, " +
            "ROUND(SUM(m.emission) / c.population / 10000, 2) AS perCapita, c.is_municipality AS isMunicipality " +
            "FROM fact_emission_month m JOIN dim_region c ON c.id = m.region_id " +
            "WHERE m.year = #{year} AND m.month = #{month} AND c.level = 2 " +
            "<if test='provinceId != null'> AND c.parent_id = #{provinceId}</if>" +
            "<if test='industryId != null'> AND m.industry_id = #{industryId}</if>" +
            "<if test='energyId != null'> AND m.energy_id = #{energyId}</if>" +
            "<if test='energyId == null'> AND m.energy_id &lt;= 6</if>" +
            "GROUP BY c.id, c.region_name, c.population, c.is_municipality ORDER BY emission DESC" +
            "</script>")
    List<RegionRankVO> selectMonthRankingCity(@Param("year") int year, @Param("month") int month,
                                              @Param("provinceId") Integer provinceId,
                                              @Param("industryId") Integer industryId,
                                              @Param("energyId") Integer energyId);
}
