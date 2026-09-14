package com.smart.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smart.common.Result;
import com.smart.entity.SysUser;
import com.smart.mapper.SysUserMapper;
import com.smart.service.AlertService;
import com.smart.vo.AlertPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 智能预警接口
 */
@Tag(name = "智能预警")
@RestController
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final SysUserMapper userMapper;

    @Operation(summary = "执行阈值规则扫描（管理员）")
    @SaCheckRole("ADMIN")
    @PostMapping("/scan")
    public Result<Map<String, Object>> scan() {
        int count = alertService.scanRules();
        return Result.ok(Map.of("newAlerts", count));
    }

    @Operation(summary = "执行孤立森林 AI 异常检测（管理员）")
    @SaCheckRole("ADMIN")
    @PostMapping("/anomaly")
    public Result<Map<String, Object>> anomaly() {
        return Result.ok(alertService.runAnomalyDetection());
    }

    @Operation(summary = "预警分页查询")
    @GetMapping("/page")
    public Result<IPage<AlertPageVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) Integer detectType,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) Integer regionId) {
        return Result.ok(alertService.page(pageNum, pageSize, detectType, status, regionId));
    }

    @Operation(summary = "确认预警（处置闭环）")
    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable long id) {
        SysUser user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        String username = user == null ? "" : (user.getRealName() != null ? user.getRealName() : user.getUsername());
        alertService.handle(id, username);
        return Result.ok();
    }

    @Operation(summary = "预警统计摘要")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(alertService.summary());
    }
}
