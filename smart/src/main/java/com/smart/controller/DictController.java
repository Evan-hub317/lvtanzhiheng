package com.smart.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.common.Result;
import com.smart.entity.DimEnergy;
import com.smart.entity.DimIndustry;
import com.smart.entity.DimRegion;
import com.smart.entity.FactorEmission;
import com.smart.entity.SysRole;
import com.smart.mapper.DimEnergyMapper;
import com.smart.mapper.DimIndustryMapper;
import com.smart.mapper.DimRegionMapper;
import com.smart.mapper.FactorEmissionMapper;
import com.smart.mapper.SysRoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据字典接口（区域/行业/能源品种/排放因子）
 */
@Tag(name = "数据字典")
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class DictController {

    private final DimRegionMapper regionMapper;
    private final DimIndustryMapper industryMapper;
    private final DimEnergyMapper energyMapper;
    private final FactorEmissionMapper factorMapper;
    private final SysRoleMapper roleMapper;

    @Operation(summary = "区域列表（省/市）")
    @GetMapping("/region/list")
    public Result<List<DimRegion>> regionList() {
        return Result.ok(regionMapper.selectList(
                new LambdaQueryWrapper<DimRegion>().orderByAsc(DimRegion::getSortOrder)));
    }

    @Operation(summary = "行业列表")
    @GetMapping("/industry/list")
    public Result<List<DimIndustry>> industryList() {
        return Result.ok(industryMapper.selectList(
                new LambdaQueryWrapper<DimIndustry>().orderByAsc(DimIndustry::getSortOrder)));
    }

    @Operation(summary = "能源品种列表")
    @GetMapping("/energy/list")
    public Result<List<DimEnergy>> energyList() {
        return Result.ok(energyMapper.selectList(
                new LambdaQueryWrapper<DimEnergy>().orderByAsc(DimEnergy::getSortOrder)));
    }

    @Operation(summary = "排放因子列表")
    @GetMapping("/factor/list")
    public Result<List<FactorEmission>> factorList() {
        return Result.ok(factorMapper.selectList(
                new LambdaQueryWrapper<FactorEmission>()
                        .orderByAsc(FactorEmission::getEnergyId)
                        .orderByAsc(FactorEmission::getEffectiveYear)));
    }

    @Operation(summary = "角色列表")
    @GetMapping("/role/list")
    public Result<List<SysRole>> roleList() {
        return Result.ok(roleMapper.selectList(null));
    }
}
