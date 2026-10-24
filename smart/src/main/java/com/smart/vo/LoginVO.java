package com.smart.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 登录返回信息 */
@Data
public class LoginVO {

    /** 登录后返回，userInfo 接口为 null */
    private String token;

    private String username;

    private String realName;

    private String roleCode;

    /** 邮箱（userInfo 接口返回，个人中心展示） */
    private String email;

    /** 手机号（userInfo 接口返回，个人中心展示） */
    private String phone;

    /** 头像相对路径（未上传为 null，前端回退为姓名首字） */
    private String avatar;

    /** 注册时间（userInfo 接口返回，个人中心展示用） */
    private LocalDateTime createTime;
}
