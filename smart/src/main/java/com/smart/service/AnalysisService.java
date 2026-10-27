package com.smart.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smart.dto.DetailQueryDTO;
import com.smart.vo.DetailVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.KpiVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.ScreenPeriodVO;
import com.smart.vo.StructureVO;
import com.smart.vo.TrendVO;

import java.util.List;

public interface AnalysisService {

    /**
     * 年度排放趋势（regionId=1 为全省，否则为该市及所辖区县；可选能源筛选）
     */
    List<TrendVO> trend(int regionId, int startYear, int endYear, Integer industryId, Integer energyId);

    /**
     * 某年行业排放结构
     */
    List<StructureVO> structure(int regionId, int year);

    /**
     * 某年能源结构
     */
    List<EnergyStructureVO> energyStructure(int regionId, int year);

    /**
     * 某月行业排放结构（月报附图用）
     */
    List<StructureVO> structureMonthly(int regionId, int year, int month);

    /**
     * 某年月度排放趋势（可选行业/能源筛选）
     */
    List<MonthlyVO> monthlyTrend(int regionId, int year, Integer industryId, Integer energyId);

    /**
     * 某年各地级行政区排放排行（可选行业/能源筛选）
     */
    List<RegionRankVO> regionRanking(int year, Integer industryId, Integer energyId);

    /**
     * 某省下辖各地级行政区某年排放排行（可选行业/能源筛选）
     */
    List<RegionRankVO> cityRankingByProvince(int provinceId, int year, Integer industryId, Integer energyId);

    /**
     * 某行业年度排放趋势（regionId=1 为全国）
     */
    List<TrendVO> industryTrend(int regionId, int startYear, int endYear, int industryId);

    /**
     * 某年电力/热力间接排放（展示口径）
     */
    List<EnergyStructureVO> indirect(int year);

    /**
     * 某年各省排放总量（地图热力数据；可选行业/能源筛选）
     */
    List<RegionRankVO> provinceEmissions(int year, Integer industryId, Integer energyId);

    /**
     * 某月排行：scope=province 省级行政区 / city 地级行政区（provinceId 为 null 时全国）
     */
    List<RegionRankVO> monthRanking(int year, int month, Integer provinceId, String scope,
                                    Integer industryId, Integer energyId);

    /**
     * 排放明细分页（年度/月度粒度，区域×行业×能源，区间筛选 + 排序）
     */
    IPage<DetailVO> detailPage(DetailQueryDTO q);

    /**
     * 年度核心指标：总量、同比、碳强度
     */
    KpiVO kpi(int regionId, int year);

    /**
     * 大屏时段聚合（碳排放热力图）：起止月口径的总量/碳强度/结构/排行。
     * regionId=1 时附带各省排行（地图 + TOP10 数据源）；省/市级返回该区域口径结构数据
     */
    ScreenPeriodVO screenPeriod(int regionId, String startMonth, String endMonth);
}
