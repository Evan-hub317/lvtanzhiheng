package com.smart.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.dto.LoginDTO;
import com.smart.dto.PasswordDTO;
import com.smart.dto.UserDTO;
import com.smart.entity.SysUser;
import com.smart.vo.LoginVO;
import com.smart.vo.UserPageVO;

public interface SysUserService extends IService<SysUser> {

    /**
     * 登录：校验密码、签发 Token
     */
    LoginVO login(LoginDTO dto);

    /**
     * 当前登录用户信息
     */
    LoginVO currentUser();

    /**
     * 用户分页（含角色名）
     */
    Page<UserPageVO> pageUsers(int pageNum, int pageSize, String username, Integer status);

    /**
     * 新增用户（密码不传默认 admin123）
     */
    void createUser(UserDTO dto);

    /**
     * 编辑用户（用户名不可改）
     */
    void updateUser(UserDTO dto);

    /**
     * 删除用户（保护：不能删自己与内置管理员）
     */
    void deleteUser(Long id);

    /**
     * 重置密码为 admin123
     */
    void resetPassword(Long id);

    /**
     * 修改本人密码（校验旧密码，改后强制重新登录）
     */
    void changePassword(PasswordDTO dto);

    /**
     * 修改本人姓名
     */
    void updateProfile(String realName);
}
