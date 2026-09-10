package com.smart.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 鉴权：除登录与文档接口外均需登录
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 显式开启注解鉴权（@SaCheckRole 由 StpInterfaceImpl 提供角色数据）
        registry.addInterceptor(new SaInterceptor(handle ->
                        SaRouter.match("/**")
                                .notMatch("/auth/login",
                                        "/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-ui/**",
                                        "/favicon.ico", "/error")
                                .check(r -> StpUtil.checkLogin()))
                        .isAnnotation(true))
                .addPathPatterns("/**");
    }
}
