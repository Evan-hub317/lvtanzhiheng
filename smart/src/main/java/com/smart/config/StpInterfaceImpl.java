package com.smart.config;

import cn.dev33.satoken.stp.StpInterface;
import com.smart.entity.SysRole;
import com.smart.entity.SysUser;
import com.smart.mapper.SysRoleMapper;
import com.smart.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 角色/权限数据源
 * <p>
 * @SaCheckRole 等注解校验依赖此实现，缺失时任何账号均无角色（403）。
 * 此处从数据库实时加载用户角色，保证停用/改角色立即生效。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 本项目仅使用角色级权限（ADMIN/USER），暂不启用细粒度权限码
        return new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            SysUser user = userMapper.selectById(Long.parseLong(String.valueOf(loginId)));
            if (user == null || user.getRoleId() == null) {
                return new ArrayList<>();
            }
            SysRole role = roleMapper.selectById(user.getRoleId());
            return role == null ? new ArrayList<>() : Collections.singletonList(role.getRoleCode());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
