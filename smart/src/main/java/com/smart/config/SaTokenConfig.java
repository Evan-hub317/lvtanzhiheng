package com.smart.config;

import cn.dev33.satoken.context.SaHolder;
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
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // CORS 预检请求（OPTIONS）直接放行：预检同样经过拦截器链，
                    // 若被鉴权拦截返回非 2xx，浏览器会判定跨域失败、拦截所有后续请求。
                    // 注意：1.39 的 SaInterceptor auth 参数是 handler 而非请求对象，
                    // 必须通过 SaHolder 上下文取当前请求
                    if ("OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                        return;
                    }
                    SaRouter.match("/**")
                            .notMatch("/auth/login",
                                    "/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-ui/**",
                                    "/favicon.ico", "/error")
                            .check(r -> StpUtil.checkLogin());
                })
                .isAnnotation(true))
                .addPathPatterns("/**");
    }
}
