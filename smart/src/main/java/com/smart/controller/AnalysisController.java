package com.smart.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smart.common.Result;
import com.smart.service.AnalysisService;
import com.smart.vo.DetailVO;
import com.smart.vo.EnergyStructureVO;
import com.smart.vo.KpiVO;
import com.smart.vo.MonthlyVO;
import com.smart.vo.RegionRankVO;
import com.smart.vo.StructureVO;
import com.smart.vo.TrendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "年度排放总量趋势（可选能源筛选）")
    @GetMapping("/trend")
    public Result<List<TrendVO>> trend(@RequestParam(defaultValue = "1") int regionId,
                                       @RequestParam int startYear,
                                       @RequestParam int endYear,
                                       @RequestParam(required = false) Integer energyId) {
        return Result.ok(analysisService.trend(regionId, startYear, endYear, energyId));
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

    @Operation(summary = "某年月度排放趋势（可选行业/能源筛选）")
    @GetMapping("/monthly-trend")
    public Result<List<MonthlyVO>> monthlyTrend(@RequestParam(defaultValue = "1") int regionId,
                                                @RequestParam int year,
                                                @RequestParam(required = false) Integer industryId,
                                                @RequestParam(required = false) Integer energyId) {
        return Result.ok(analysisService.monthlyTrend(regionId, year, industryId, energyId));
    }

    @Operation(summary = "某年各市排放排行")
    @GetMapping("/region-ranking")
    public Result<List<RegionRankVO>> regionRanking(@RequestParam int year) {
        return Result.ok(analysisService.regionRanking(year));
    }

    @Operation(summary = "全国地图热力数据：各省某年排放总量（大屏数据源）")
    @GetMapping("/map")
    public Result<List<RegionRankVO>> map(@RequestParam int year) {
        return Result.ok(analysisService.provinceEmissions(year));
    }

    @Operation(summary = "排放明细分页（区域×行业×能源×年度）")
    @GetMapping("/detail")
    public Result<IPage<DetailVO>> detail(@RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(defaultValue = "1") int regionId,
                                          @RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Integer industryId,
                                          @RequestParam(required = false) Integer energyId) {
        return Result.ok(analysisService.detailPage(pageNum, pageSize, regionId, year, industryId, energyId));
    }

    @Operation(summary = "年度核心指标（总量/同比/碳强度）")
    @GetMapping("/kpi")
    public Result<KpiVO> kpi(@RequestParam(defaultValue = "1") int regionId,
                             @RequestParam int year) {
        return Result.ok(analysisService.kpi(regionId, year));
    }
}
