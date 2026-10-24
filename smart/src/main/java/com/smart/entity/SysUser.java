package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户表 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 加密 */
    private String password;

    private String realName;

    /** 邮箱（个人中心展示） */
    private String email;

    /** 手机号（个人中心展示） */
    private String phone;

    /** 头像相对路径（如 /uploads/images/xxx.png，存 images 目录） */
    private String avatar;

    private Integer roleId;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
