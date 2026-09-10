package com.smart.controller;

import com.smart.common.Result;
import com.smart.dto.SaveScenarioDTO;
import com.smart.dto.SimulateDTO;
import com.smart.service.SimulationService;
import com.smart.vo.PredictVO;
import com.smart.vo.ScenarioVO;
import com.smart.vo.SimResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预测与情景仿真接口（核心亮点模块）
 */
@Tag(name = "情景仿真")
@RestController
@RequestMapping("/sim")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @Data
    public static class PredictDTO {
        private int regionId = 1;
    }

    @Operation(summary = "排放趋势预测（LSTM+蒙特卡洛，含达峰年份）")
    @PostMapping("/predict")
    public Result<PredictVO> predict(@RequestBody PredictDTO dto) {
        return Result.ok(simulationService.predict(dto.getRegionId()));
    }

    @Operation(summary = "情景仿真（调整参数实时重算轨迹）")
    @PostMapping("/simulate")
    public Result<SimResultVO> simulate(@RequestBody SimulateDTO dto) {
        return Result.ok(simulationService.simulate(dto));
    }

    @Operation(summary = "保存情景")
    @PostMapping("/save")
    public Result<ScenarioVO> save(@RequestBody SaveScenarioDTO dto) {
        return Result.ok(simulationService.save(dto));
    }

    @Operation(summary = "情景列表")
    @GetMapping("/list")
    public Result<List<ScenarioVO>> list(@RequestParam(defaultValue = "1") int regionId) {
        return Result.ok(simulationService.list(regionId));
    }

    @Operation(summary = "情景详情（含轨迹）")
    @GetMapping("/{id}")
    public Result<ScenarioVO> detail(@PathVariable long id) {
        return Result.ok(simulationService.detail(id));
    }
}
