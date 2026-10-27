package com.smart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.common.BizException;
import com.smart.dto.DetailQueryDTO;
import com.smart.entity.DimRegion;
import com.smart.mapper.DimRegionMapper;
import com.smart.mapper.FactEmissionMonthMapper;
import com.smart.mapper.FactEmissionYearMapper;
import com.smart.mapper.SummaryMapper;
import com.smart.service.AnalysisService;
import com.smart.vo.DetailVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.KpiVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.ScreenPeriodVO;
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
    private final SummaryMapper summaryMapper;

    /** 汇总口径判定：1=全国；省ID=省级；-1=市级（走明细表） */
    private int scopeRegionId(int regionId) {
        if (regionId == PROVINCE_REGION_ID) {
            return 1;
        }
        DimRegion region = regionMapper.selectById(regionId);
        return (region != null && region.getLevel() == 1) ? regionId : -1;
    }

    @Override
    public List<TrendVO> trend(int regionId, int startYear, int endYear, Integer industryId, Integer energyId) {
        int scope = scopeRegionId(regionId);
        // 行业筛选需要明细聚合（汇总表无行业维度）
        if (scope >= 0 && energyId == null && industryId == null) {
            List<TrendVO> rows = summaryMapper.selectTrend(scope, startYear, endYear);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return regionId == PROVINCE_REGION_ID
                ? yearMapper.selectTrendAll(startYear, endYear, industryId, energyId)
                : yearMapper.selectTrendByRegion(regionId, startYear, endYear, industryId, energyId);
    }

    @Override
    public List<StructureVO> structure(int regionId, int year) {
        int scope = scopeRegionId(regionId);
        if (scope >= 0) {
            List<StructureVO> rows = summaryMapper.selectStructure(scope, year);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return regionId == PROVINCE_REGION_ID
                ? yearMapper.selectStructureAll(year)
                : yearMapper.selectStructureByRegion(regionId, year);
    }

    @Override
    public List<EnergyStructureVO> energyStructure(int regionId, int year) {
        int scope = scopeRegionId(regionId);
        if (scope >= 0) {
            List<EnergyStructureVO> rows = summaryMapper.selectEnergy(scope, year, 6);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return regionId == PROVINCE_REGION_ID
                ? yearMapper.selectEnergyStructureAll(year)
                : yearMapper.selectEnergyStructureByRegion(regionId, year);
    }

    @Override
    public List<StructureVO> structureMonthly(int regionId, int year, int month) {
        int scope = scopeRegionId(regionId);
        if (scope >= 0) {
            List<StructureVO> rows = summaryMapper.selectStructureMonthly(scope, year, month);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return regionId == PROVINCE_REGION_ID
                ? monthMapper.selectStructureMonthlyAll(year, month)
                : monthMapper.selectStructureMonthlyByRegion(regionId, year, month);
    }

    @Override
    public List<MonthlyVO> monthlyTrend(int regionId, int year, Integer industryId, Integer energyId) {
        int scope = scopeRegionId(regionId);
        if (scope >= 0 && industryId == null && energyId == null) {
            List<MonthlyVO> rows = summaryMapper.selectMonthly(scope, year);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return regionId == PROVINCE_REGION_ID
                ? monthMapper.selectMonthlyAll(year, industryId, energyId)
                : monthMapper.selectMonthlyByRegion(regionId, year, industryId, energyId);
    }

    @Override
    public List<RegionRankVO> cityRankingByProvince(int provinceId, int year, Integer industryId, Integer energyId) {
        // 行业/能源筛选需要明细聚合（汇总表无该维度），其余走汇总表
        if (industryId != null || energyId != null) {
            return yearMapper.selectCityRankingByProvinceFiltered(provinceId, year, industryId, energyId);
        }
        List<RegionRankVO> rows = summaryMapper.selectCityRankingByProvince(provinceId, year);
        return rows.isEmpty() ? yearMapper.selectCityRankingByProvince(provinceId, year) : rows;
    }

    @Override
    public List<TrendVO> industryTrend(int regionId, int startYear, int endYear, int industryId) {
        return regionId == PROVINCE_REGION_ID
                ? yearMapper.selectTrendAllByIndustry(startYear, endYear, industryId)
                : yearMapper.selectTrendByRegionIndustry(regionId, startYear, endYear, industryId);
    }

    @Override
    public List<RegionRankVO> regionRanking(int year, Integer industryId, Integer energyId) {
        if (industryId != null || energyId != null) {
            return yearMapper.selectRegionRankingFiltered(year, industryId, energyId);
        }
        List<RegionRankVO> rows = summaryMapper.selectCityRanking(year);
        return rows.isEmpty() ? yearMapper.selectRegionRanking(year) : rows;
    }

    @Override
    public List<EnergyStructureVO> indirect(int year) {
        List<EnergyStructureVO> rows = summaryMapper.selectEnergy(1, year, 8).stream()
                .filter(v -> v.getEnergyId() != null && v.getEnergyId() > 6)
                .collect(java.util.stream.Collectors.toList());
        return rows.isEmpty() ? yearMapper.selectIndirect(year) : rows;
    }

    @Override
    public List<RegionRankVO> provinceEmissions(int year, Integer industryId, Integer energyId) {
        if (industryId != null || energyId != null) {
            return yearMapper.selectProvinceRankingFiltered(year, industryId, energyId);
        }
        List<RegionRankVO> rows = summaryMapper.selectProvinceRanking(year);
        return rows.isEmpty() ? yearMapper.selectProvinceRanking(year) : rows;
    }

    @Override
    public List<RegionRankVO> monthRanking(int year, int month, Integer provinceId, String scope,
                                           Integer industryId, Integer energyId) {
        if ("city".equalsIgnoreCase(scope)) {
            Integer pid = (provinceId == null || provinceId == PROVINCE_REGION_ID) ? null : provinceId;
            return monthMapper.selectMonthRankingCity(year, month, pid, industryId, energyId);
        }
        return monthMapper.selectMonthRankingProvince(year, month, industryId, energyId);
    }

    @Override
    public IPage<DetailVO> detailPage(DetailQueryDTO q) {
        // 排序白名单映射（防注入：sortField 只允许四种取值，方向只允许 ASC/DESC）
        String field = q.getSortField() == null ? "time" : q.getSortField();
        String dir = "asc".equalsIgnoreCase(q.getSortOrder()) ? "ASC" : "DESC";
        boolean byMonth = "month".equalsIgnoreCase(q.getGranularity());
        String timeCols = byMonth ? "year, month" : "year";
        String col = switch (field) {
            case "emission" -> "emission";
            case "perCapita" -> "perCapita";
            case "yoyRate" -> "yoyRate";
            default -> null;
        };
        String sortSql = col == null
                ? timeCols + " " + dir
                : col + " " + dir + ", " + timeCols + " " + dir;

        Page<DetailVO> page = new Page<>(q.getPageNum(), q.getPageSize());
        if (byMonth) {
            return q.getRegionId() == PROVINCE_REGION_ID
                    ? monthMapper.selectDetailAllMonth(page, q, sortSql)
                    : monthMapper.selectDetailByRegionMonth(page, q, sortSql);
        }
        return q.getRegionId() == PROVINCE_REGION_ID
                ? yearMapper.selectDetailAll(page, q, sortSql)
                : yearMapper.selectDetailByRegion(page, q, sortSql);
    }

    @Override
    public KpiVO kpi(int regionId, int year) {
        List<TrendVO> rows = trend(regionId, year, year, null, null);
        if (rows.isEmpty()) {
            return null;
        }
        BigDecimal total = rows.get(0).getEmission();
        // 同比：与上一年对比
        List<TrendVO> prevRows = trend(regionId, year - 1, year - 1, null, null);
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

    @Override
    public ScreenPeriodVO screenPeriod(int regionId, String startMonth, String endMonth) {
        String[] s = startMonth.split("-"), e = endMonth.split("-");
        int sy = Integer.parseInt(s[0]), sm = Integer.parseInt(s[1]);
        int ey = Integer.parseInt(e[0]), em = Integer.parseInt(e[1]);
        if (ey < sy || (ey == sy && em < sm)) {
            throw new BizException("时段起止不合法");
        }
        int months = (ey - sy) * 12 + (em - sm) + 1;

        // 全国/省级走汇总表（空回退明细），市级直接走明细
        DimRegion region = regionId == PROVINCE_REGION_ID ? null : regionMapper.selectById(regionId);
        if (regionId != PROVINCE_REGION_ID && region == null) {
            throw new BizException("区域不存在");
        }
        Integer statScope;
        if (regionId == PROVINCE_REGION_ID) {
            statScope = 1;
        } else {
            Integer lv = region.getLevel();
            statScope = (lv != null && lv == 1) ? regionId : null;
        }
        Integer factRegion = regionId == PROVINCE_REGION_ID ? null : regionId;

        ScreenPeriodVO vo = new ScreenPeriodVO();
        BigDecimal total = statScope != null
                ? summaryMapper.selectPeriodTotal(statScope, sy, sm, ey, em) : null;
        if (total == null || total.signum() == 0) {
            total = monthMapper.selectPeriodTotal(factRegion, sy, sm, ey, em);
        }
        vo.setTotal(total);

        // 碳强度：tCO2 / 万元GDP；GDP 为年值，按时段月数占比折算
        BigDecimal gdpYi = null;
        if (regionId == PROVINCE_REGION_ID) {
            gdpYi = regionMapper.selectList(new LambdaQueryWrapper<DimRegion>().eq(DimRegion::getLevel, 1))
                    .stream().map(DimRegion::getGdp).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            int provinceId = regionId;
            if (region.getLevel() != null && region.getLevel() == 2 && region.getParentId() != null) {
                provinceId = region.getParentId();
            }
            DimRegion province = regionMapper.selectById(provinceId);
            if (province != null) {
                gdpYi = province.getGdp();
            }
        }
        if (gdpYi != null && gdpYi.signum() > 0 && total != null) {
            BigDecimal periodGdpWan = gdpYi.multiply(BigDecimal.valueOf(10000))
                    .multiply(BigDecimal.valueOf(months))
                    .divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
            vo.setIntensity(total.divide(periodGdpWan, 2, RoundingMode.HALF_UP));
        }

        // 结构类数据：全国/省级优先走月度汇总表（亚秒级），空回退明细表；市级直接走明细
        List<EnergyStructureVO> direct = statScope != null
                ? summaryMapper.selectPeriodEnergy(statScope, 1, 6, sy, sm, ey, em) : null;
        List<EnergyStructureVO> indirect = statScope != null
                ? summaryMapper.selectPeriodEnergy(statScope, 7, 8, sy, sm, ey, em) : null;
        List<StructureVO> industries = statScope != null
                ? summaryMapper.selectPeriodIndustry(statScope, sy, sm, ey, em) : null;
        if (direct == null || direct.isEmpty()) {
            direct = monthMapper.selectPeriodEnergy(factRegion, 1, 6, sy, sm, ey, em);
        }
        if (indirect == null || indirect.isEmpty()) {
            indirect = monthMapper.selectPeriodEnergy(factRegion, 7, 8, sy, sm, ey, em);
        }
        if (industries == null || industries.isEmpty()) {
            industries = monthMapper.selectPeriodIndustry(factRegion, sy, sm, ey, em);
        }
        vo.setDirect(direct);
        vo.setIndirect(indirect);
        vo.setIndustries(industries);
        if (regionId == PROVINCE_REGION_ID) {
            List<RegionRankVO> provinces = summaryMapper.selectPeriodProvinces(sy, sm, ey, em);
            if (provinces.isEmpty()) {
                provinces = monthMapper.selectPeriodProvinces(sy, sm, ey, em);
            }
            vo.setRegions(provinces);
        }
        return vo;
    }
}
