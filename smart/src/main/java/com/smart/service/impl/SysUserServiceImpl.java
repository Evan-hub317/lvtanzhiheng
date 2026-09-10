package com.smart.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.common.BizException;
import com.smart.dto.LoginDTO;
import com.smart.dto.PasswordDTO;
import com.smart.dto.UserDTO;
import com.smart.entity.SysRole;
import com.smart.entity.SysUser;
import com.smart.mapper.SysRoleMapper;
import com.smart.mapper.SysUserMapper;
import com.smart.service.SysUserService;
import com.smart.vo.LoginVO;
import com.smart.vo.UserPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /** 默认初始密码 */
    private static final String DEFAULT_PASSWORD = "admin123";

    /** 内置管理员ID，禁止删除 */
    private static final long PROTECTED_USER_ID = 1L;

    private final SysRoleMapper roleMapper;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = lambdaQuery().eq(SysUser::getUsername, dto.getUsername()).one();
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已被停用，请联系管理员");
        }
        StpUtil.login(user.getId());
        return buildVO(user, StpUtil.getTokenValue());
    }

    @Override
    public LoginVO currentUser() {
        SysUser user = getById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return buildVO(user, null);
    }

    @Override
    public Page<UserPageVO> pageUsers(int pageNum, int pageSize, String username, Integer status) {
        Page<SysUser> page = page(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime));

        // 角色名映射
        Map<Integer, String> roleNameMap = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName, (a, b) -> a));

        Page<UserPageVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<UserPageVO> records = page.getRecords().stream().map(u -> {
            UserPageVO vo = new UserPageVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setRealName(u.getRealName());
            vo.setRoleId(u.getRoleId());
            vo.setRoleName(roleNameMap.getOrDefault(u.getRoleId(), ""));
            vo.setStatus(u.getStatus());
            vo.setCreateTime(u.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public void createUser(UserDTO dto) {
        long count = lambdaQuery().eq(SysUser::getUsername, dto.getUsername()).count();
        if (count > 0) {
            throw new BizException("用户名已存在");
        }
        if (dto.getRoleId() == null) {
            throw new BizException("请选择角色");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        String raw = StringUtils.hasText(dto.getPassword()) ? dto.getPassword() : DEFAULT_PASSWORD;
        user.setPassword(BCrypt.hashpw(raw));
        user.setRealName(dto.getRealName());
        user.setRoleId(dto.getRoleId());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        save(user);
    }

    @Override
    public void updateUser(UserDTO dto) {
        if (dto.getId() == null) {
            throw new BizException("用户ID不能为空");
        }
        SysUser user = getById(dto.getId());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (user.getRoleId() == null || dto.getRoleId() != null) {
            user.setRoleId(dto.getRoleId());
        }
        user.setRealName(dto.getRealName());
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        updateById(user);
    }

    @Override
    public void deleteUser(Long id) {
        if (id == PROTECTED_USER_ID) {
            throw new BizException("内置管理员账号不可删除");
        }
        if (id.equals(StpUtil.getLoginIdAsLong())) {
            throw new BizException("不能删除当前登录账号");
        }
        removeById(id);
    }

    @Override
    public void resetPassword(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setPassword(BCrypt.hashpw(DEFAULT_PASSWORD));
        updateById(user);
    }

    @Override
    public void changePassword(PasswordDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = getById(userId);
        if (user == null || !BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new BizException("旧密码不正确");
        }
        user.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        updateById(user);
        // 改密后强制重新登录
        StpUtil.logout();
    }

    @Override
    public void updateProfile(String realName) {
        if (!StringUtils.hasText(realName)) {
            throw new BizException("姓名不能为空");
        }
        SysUser user = getById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        user.setRealName(realName.trim());
        updateById(user);
    }

    private LoginVO buildVO(SysUser user, String token) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        SysRole role = roleMapper.selectById(user.getRoleId());
        vo.setRoleCode(role == null ? "" : role.getRoleCode());
        return vo;
    }
}
