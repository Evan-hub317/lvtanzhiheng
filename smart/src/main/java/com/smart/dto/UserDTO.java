package com.smart.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户新增/编辑参数
 * 新增：username/password 有效；编辑：仅 id/realName/roleId/status 生效
 */
@Data
public class UserDTO {

    /** 编辑时必填 */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 仅新增有效，不传默认 admin123 */
    private String password;

    private String realName;

    private Integer roleId;

    /** 1启用 0停用 */
    private Integer status;
}
