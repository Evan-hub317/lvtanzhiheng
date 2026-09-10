package com.smart.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.common.Result;
import com.smart.dto.PasswordDTO;
import com.smart.dto.UserDTO;
import com.smart.service.SysUserService;
import com.smart.vo.UserPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（管理操作仅管理员可用）
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserService sysUserService;

    @Operation(summary = "用户分页查询")
    @GetMapping("/page")
    public Result<Page<UserPageVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String username,
                                         @RequestParam(required = false) Integer status) {
        return Result.ok(sysUserService.pageUsers(pageNum, pageSize, username, status));
    }

    @Operation(summary = "新增用户（管理员）")
    @SaCheckRole("ADMIN")
    @PostMapping
    public Result<Void> create(@RequestBody @Valid UserDTO dto) {
        sysUserService.createUser(dto);
        return Result.ok();
    }

    @Operation(summary = "编辑用户（管理员）")
    @SaCheckRole("ADMIN")
    @PutMapping
    public Result<Void> update(@RequestBody @Valid UserDTO dto) {
        sysUserService.updateUser(dto);
        return Result.ok();
    }

    @Operation(summary = "删除用户（管理员）")
    @SaCheckRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteUser(id);
        return Result.ok();
    }

    @Operation(summary = "重置密码为 admin123（管理员）")
    @SaCheckRole("ADMIN")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        sysUserService.resetPassword(id);
        return Result.ok();
    }

    @Operation(summary = "修改本人密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid PasswordDTO dto) {
        sysUserService.changePassword(dto);
        return Result.ok();
    }

    @Operation(summary = "修改本人姓名")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestParam String realName) {
        sysUserService.updateProfile(realName);
        return Result.ok();
    }
}
