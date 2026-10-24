package com.smart.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改本人资料（个人中心） */
@Data
public class ProfileDTO {

    @NotBlank(message = "姓名不能为空")
    private String realName;

    /** 可为空 */
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 可为空 */
    @Size(max = 20, message = "手机号过长")
    private String phone;
}
