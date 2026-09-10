package com.smart.vo;

import lombok.Data;

/** 登录返回信息 */
@Data
public class LoginVO {

    /** 登录后返回，userInfo 接口为 null */
    private String token;

    private String username;

    private String realName;

    private String roleCode;
}
