package com.smart.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.smart.common.Result;
import com.smart.service.CalcService;
import com.smart.vo.CalcResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 碳核算接口
 */
@Tag(name = "碳核算")
@RestController
@RequestMapping("/calc")
@RequiredArgsConstructor
public class CalcController {

    private final CalcService calcService;

    @Data
    public static class CalcDTO {
        /** 0 表示数据最早年份 */
        private int startYear = 0;
        /** 0 表示数据最晚年份 */
        private int endYear = 0;
    }

    @Operation(summary = "执行碳核算（管理员，幂等可重算）")
    @SaCheckRole("ADMIN")
    @PostMapping("/execute")
    public Result<CalcResultVO> execute(@RequestBody CalcDTO dto) {
        return Result.ok(calcService.execute(dto.getStartYear(), dto.getEndYear()));
    }
}
