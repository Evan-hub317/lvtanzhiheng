package com.smart.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smart.common.Result;
import com.smart.dto.DetailQueryDTO;
import com.smart.service.AnalysisService;
import com.smart.vo.DetailVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.KpiVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.ScreenPeriodVO;
import com.smart.vo.StructureVO;
import com.smart.vo.TrendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据分析接口（大屏/工作台/钻取分析页数据源）
 */
@Tag(name = "数据分析")
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "年度排放总量趋势（可选行业/能源筛选）")
    @GetMapping("/trend")
    public Result<List<TrendVO>> trend(@RequestParam(defaultValue = "1") int regionId,
                                       @RequestParam int startYear,
                                       @RequestParam int endYear,
                                       @RequestParam(required = false) Integer industryId,
                                       @RequestParam(required = false) Integer energyId) {
        return Result.ok(analysisService.trend(regionId, startYear, endYear, industryId, energyId));
    }

    @Operation(summary = "某年行业排放结构")
    @GetMapping("/structure")
    public Result<List<StructureVO>> structure(@RequestParam(defaultValue = "1") int regionId,
                                               @RequestParam int year) {
        return Result.ok(analysisService.structure(regionId, year));
    }

    @Operation(summary = "某年能源结构")
    @GetMapping("/energy-structure")
    public Result<List<EnergyStructureVO>> energyStructure(@RequestParam(defaultValue = "1") int regionId,
                                                           @RequestParam int year) {
        return Result.ok(analysisService.energyStructure(regionId, year));
    }

    @Operation(summary = "某月行业排放结构（月报附图用）")
    @GetMapping("/structure-monthly")
    public Result<List<StructureVO>> structureMonthly(@RequestParam(defaultValue = "1") int regionId,
                                                      @RequestParam int year,
                                                      @RequestParam int month) {
        return Result.ok(analysisService.structureMonthly(regionId, year, month));
    }

    @Operation(summary = "某年月度排放趋势（可选行业/能源筛选）")
    @GetMapping("/monthly-trend")
    public Result<List<MonthlyVO>> monthlyTrend(@RequestParam(defaultValue = "1") int regionId,
                                                @RequestParam int year,
                                                @RequestParam(required = false) Integer industryId,
                                                @RequestParam(required = false) Integer energyId) {
        return Result.ok(analysisService.monthlyTrend(regionId, year, industryId, energyId));
    }

    @Operation(summary = "某年各市排放排行（可选行业/能源筛选）")
    @GetMapping("/region-ranking")
    public Result<List<RegionRankVO>> regionRanking(@RequestParam int year,
                                                    @RequestParam(required = false) Integer industryId,
                                                    @RequestParam(required = false) Integer energyId) {
        return Result.ok(analysisService.regionRanking(year, industryId, energyId));
    }

    @Operation(summary = "某省下辖各地级行政区排放排行（可选行业/能源筛选）")
    @GetMapping("/city-ranking")
    public Result<List<RegionRankVO>> cityRanking(@RequestParam int provinceId, @RequestParam int year,
                                                  @RequestParam(required = false) Integer industryId,
                                                  @RequestParam(required = false) Integer energyId) {
        return Result.ok(analysisService.cityRankingByProvince(provinceId, year, industryId, energyId));
    }

    @Operation(summary = "某年电力/热力间接排放（展示口径，不计入总量）")
    @GetMapping("/indirect")
    public Result<List<EnergyStructureVO>> indirect(@RequestParam int year) {
        return Result.ok(analysisService.indirect(year));
    }

    @Operation(summary = "全国地图热力数据：各省某年排放总量（大屏数据源；可选行业/能源筛选）")
    @GetMapping("/map")
    public Result<List<RegionRankVO>> map(@RequestParam int year,
                                          @RequestParam(required = false) Integer industryId,
                                          @RequestParam(required = false) Integer energyId) {
        return Result.ok(analysisService.provinceEmissions(year, industryId, energyId));
    }

    @Operation(summary = "某月排行（scope=province 省级 / city 地级，provinceId 为空时全国；可选行业/能源筛选）")
    @GetMapping("/month-ranking")
    public Result<List<RegionRankVO>> monthRanking(@RequestParam int year,
                                                   @RequestParam int month,
                                                   @RequestParam(required = false) Integer provinceId,
                                                   @RequestParam(required = false) String scope,
                                                   @RequestParam(required = false) Integer industryId,
                                                   @RequestParam(required = false) Integer energyId) {
        return Result.ok(analysisService.monthRanking(year, month, provinceId, scope, industryId, energyId));
    }

    @Operation(summary = "排放明细分页（年度/月度粒度，区域×行业×能源，区间筛选 + 排序）")
    @GetMapping("/detail")
    public Result<IPage<DetailVO>> detail(@Valid DetailQueryDTO q) {
        return Result.ok(analysisService.detailPage(q));
    }

    @Operation(summary = "年度核心指标（总量/同比/碳强度）")
    @GetMapping("/kpi")
    public Result<KpiVO> kpi(@RequestParam(defaultValue = "1") int regionId,
                             @RequestParam int year) {
        return Result.ok(analysisService.kpi(regionId, year));
    }

    @Operation(summary = "大屏时段聚合（碳排放热力图：起止月口径总量/碳强度/结构/排行）")
    @GetMapping("/screen/period")
    public Result<ScreenPeriodVO> screenPeriod(@RequestParam(defaultValue = "1") int regionId,
                                               @RequestParam String startMonth,
                                               @RequestParam String endMonth) {
        return Result.ok(analysisService.screenPeriod(regionId, startMonth, endMonth));
    }
}
