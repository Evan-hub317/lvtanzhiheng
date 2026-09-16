package com.smart.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.smart.common.Result;
import com.smart.entity.DimRegion;
import com.smart.mapper.DimRegionMapper;
import com.smart.mapper.FactEmissionYearMapper;
import com.smart.mapper.FactEnergyMonthMapper;
import com.smart.service.DataGenerateService;
import com.smart.vo.DataStatusVO;
import com.smart.vo.GenerateResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 数据管理接口
 */
@Tag(name = "数据管理")
@RestController
@RequestMapping("/data")
@RequiredArgsConstructor
public class DataController {

    private final DataGenerateService generateService;
    private final FactEnergyMonthMapper energyMonthMapper;
    private final FactEmissionYearMapper emissionYearMapper;
    private final DimRegionMapper regionMapper;

    @Data
    public static class GenerateDTO {
        /** all 全量（2021 至今，清空重建）/ recent 增量（仅已过完的最新月份，幂等） */
        private String mode = "all";
        private int startYear = 2021;
        private boolean injectAnomaly = true;
    }

    @Operation(summary = "生成全国模拟数据（管理员；all 全量 / recent 增量上月）")
    @SaCheckRole("ADMIN")
    @PostMapping("/generate")
    public Result<GenerateResultVO> generate(@RequestBody GenerateDTO dto) {
        return Result.ok(generateService.generate(dto.getMode(), dto.getStartYear(), dto.isInjectAnomaly()));
    }

    @Operation(summary = "数据现状概览")
    @GetMapping("/status")
    public Result<DataStatusVO> status() {
        DataStatusVO vo = energyMonthMapper.selectStatus();
        if (vo == null) {
            vo = new DataStatusVO();
        }
        vo.setCityCount(regionMapper.selectCount(
                new LambdaQueryWrapper<DimRegion>().eq(DimRegion::getLevel, 2)));
        vo.setCountyCount(regionMapper.selectCount(
                new LambdaQueryWrapper<DimRegion>().eq(DimRegion::getLevel, 3)));
        vo.setEmissionCount(emissionYearMapper.selectCount(null));
        // 最后一个完整年：当年未过完（数据月 < 12）时回退上一年
        int fullYear = vo.getMaxYear() == null ? 0 : vo.getMaxYear();
        vo.setMaxMonth(fullYear > 0 ? energyMonthMapper.selectMaxMonth(fullYear) : 0);
        if (fullYear > 0 && vo.getMaxMonth() < 12) {
            fullYear -= 1;
        }
        vo.setMaxFullYear(fullYear);
        return Result.ok(vo);
    }
}
