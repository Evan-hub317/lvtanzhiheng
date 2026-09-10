package com.smart.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户分页列表项（含角色名）
 */
@Data
public class UserPageVO {

    private Long id;

    private String username;

    private String realName;

    private Integer roleId;

    private String roleName;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;
}
