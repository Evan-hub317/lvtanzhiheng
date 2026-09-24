package com.smart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.entity.DimRegion;
import com.smart.mapper.DimRegionMapper;
import com.smart.mapper.FactEmissionMonthMapper;
import com.smart.mapper.FactEmissionYearMapper;
import com.smart.service.AnalysisService;
import com.smart.vo.DetailVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.KpiVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.StructureVO;
import com.smart.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    /** 省级区域ID（全省聚合口径） */
    private static final int PROVINCE_REGION_ID = 1;

    private final FactEmissionYearMapper yearMapper;
    private final FactEmissionMonthMapper monthMapper;
    private final DimRegionMapper regionMapper;

    @Override
    public List<TrendVO> trend(int regionId, int startYear, int endYear, Integer energyId) {
        return regionId == PROVINCE_REGION_ID
                ? yearMapper.selectTrendAll(startYear, endYear, energyId)
                : yearMapper.selectTrendByRegion(regionId, startYear, endYear, energyId);
    }

    @Override
    public List<StructureVO> structure(int regionId, int year) {
        return regionId == PROVINCE_REGION_ID
                ? yearMapper.selectStructureAll(year)
                : yearMapper.selectStructureByRegion(regionId, year);
    }

    @Override
    public List<EnergyStructureVO> energyStructure(int regionId, int year) {
        return regionId == PROVINCE_REGION_ID
                ? yearMapper.selectEnergyStructureAll(year)
                : yearMapper.selectEnergyStructureByRegion(regionId, year);
    }

    @Override
    public List<StructureVO> structureMonthly(int regionId, int year, int month) {
        return regionId == PROVINCE_REGION_ID
                ? monthMapper.selectStructureMonthlyAll(year, month)
                : monthMapper.selectStructureMonthlyByRegion(regionId, year, month);
    }

    @Override
    public List<MonthlyVO> monthlyTrend(int regionId, int year, Integer industryId, Integer energyId) {
        return regionId == PROVINCE_REGION_ID
                ? monthMapper.selectMonthlyAll(year, industryId, energyId)
                : monthMapper.selectMonthlyByRegion(regionId, year, industryId, energyId);
    }

    @Override
    public List<RegionRankVO> cityRankingByProvince(int provinceId, int year) {
        return yearMapper.selectCityRankingByProvince(provinceId, year);
    }

    @Override
    public List<TrendVO> industryTrend(int regionId, int startYear, int endYear, int industryId) {
        return regionId == PROVINCE_REGION_ID
                ? yearMapper.selectTrendAllByIndustry(startYear, endYear, industryId)
                : yearMapper.selectTrendByRegionIndustry(regionId, startYear, endYear, industryId);
    }

    @Override
    public List<RegionRankVO> regionRanking(int year) {
        return yearMapper.selectRegionRanking(year);
    }

    @Override
    public List<RegionRankVO> provinceEmissions(int year) {
        return yearMapper.selectProvinceRanking(year);
    }

    @Override
    public IPage<DetailVO> detailPage(int pageNum, int pageSize, int regionId,
                                      Integer year, Integer industryId, Integer energyId) {
        Page<DetailVO> page = new Page<>(pageNum, pageSize);
        return regionId == PROVINCE_REGION_ID
                ? yearMapper.selectDetailAll(page, year, industryId, energyId)
                : yearMapper.selectDetailByRegion(page, regionId, year, industryId, energyId);
    }

    @Override
    public KpiVO kpi(int regionId, int year) {
        List<TrendVO> rows = trend(regionId, year, year, null);
        if (rows.isEmpty()) {
            return null;
        }
        BigDecimal total = rows.get(0).getEmission();
        // 同比：与上一年对比
        List<TrendVO> prevRows = trend(regionId, year - 1, year - 1, null);
        BigDecimal yoy = null;
        if (!prevRows.isEmpty() && prevRows.get(0).getEmission().signum() > 0) {
            yoy = total.subtract(prevRows.get(0).getEmission())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prevRows.get(0).getEmission(), 2, RoundingMode.HALF_UP);
        }
        // 碳强度：tCO2 / 万元GDP（GDP 亿元 × 10000）
        // 全国口径（regionId=1）：GDP 取全部省级 GDP 之和；省/市口径取对应省级 GDP
        BigDecimal intensity = null;
        BigDecimal gdpYi = null;
        if (regionId == PROVINCE_REGION_ID) {
            gdpYi = regionMapper.selectList(new LambdaQueryWrapper<DimRegion>().eq(DimRegion::getLevel, 1))
                    .stream().map(DimRegion::getGdp).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            DimRegion region = regionMapper.selectById(regionId);
            int provinceId = (region != null && region.getLevel() == 2) ? region.getParentId() : regionId;
            DimRegion province = regionMapper.selectById(provinceId);
            if (province != null) {
                gdpYi = province.getGdp();
            }
        }
        if (gdpYi != null && gdpYi.signum() > 0) {
            intensity = total.divide(gdpYi.multiply(BigDecimal.valueOf(10000)), 6, RoundingMode.HALF_UP);
        }
        KpiVO vo = new KpiVO();
        vo.setYear(year);
        vo.setTotalEmission(total);
        vo.setYoyRate(yoy);
        vo.setIntensity(intensity);
        return vo;
    }
}
