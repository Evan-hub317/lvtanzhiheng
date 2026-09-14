package com.smart.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.smart.common.Result;
import com.smart.dto.ReportGenerateDTO;
import com.smart.entity.ReportRecord;
import com.smart.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AIGC 监测报告接口
 */
@Tag(name = "监测报告")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "一键生成监测报告")
    @PostMapping("/generate")
    public Result<ReportRecord> generate(@RequestBody ReportGenerateDTO dto) {
        return Result.ok(reportService.generate(dto, StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "报告列表")
    @GetMapping("/list")
    public Result<List<ReportRecord>> list() {
        return Result.ok(reportService.list());
    }

    @Operation(summary = "报告详情")
    @GetMapping("/{id}")
    public Result<ReportRecord> detail(@PathVariable long id) {
        return Result.ok(reportService.detail(id));
    }

    @Operation(summary = "重新生成")
    @PostMapping("/{id}/regenerate")
    public Result<ReportRecord> regenerate(@PathVariable long id) {
        return Result.ok(reportService.regenerate(id, StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "删除报告")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        reportService.delete(id);
        return Result.ok();
    }
}
