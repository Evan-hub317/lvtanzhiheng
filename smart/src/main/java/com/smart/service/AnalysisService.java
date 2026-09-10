package com.smart.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smart.vo.DetailVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.KpiVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.StructureVO;
import com.smart.vo.TrendVO;

import java.util.List;

public interface AnalysisService {

    /**
     * 年度排放趋势（regionId=1 为全省，否则为该市及所辖区县；可选能源筛选）
     */
    List<TrendVO> trend(int regionId, int startYear, int endYear, Integer energyId);

    /**
     * 某年行业排放结构
     */
    List<StructureVO> structure(int regionId, int year);

    /**
     * 某年能源结构
     */
    List<EnergyStructureVO> energyStructure(int regionId, int year);

    /**
     * 某年月度排放趋势（可选行业/能源筛选）
     */
    List<MonthlyVO> monthlyTrend(int regionId, int year, Integer industryId, Integer energyId);

    /**
     * 某年各市排放排行
     */
    List<RegionRankVO> regionRanking(int year);

    /**
     * 排放明细分页（区域×行业×能源×年度，可选筛选）
     */
    IPage<DetailVO> detailPage(int pageNum, int pageSize, int regionId,
                               Integer year, Integer industryId, Integer energyId);

    /**
     * 年度核心指标：总量、同比、碳强度
     */
    KpiVO kpi(int regionId, int year);
}
