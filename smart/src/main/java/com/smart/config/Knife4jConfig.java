package com.smart.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j 接口文档配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("绿碳智衡 - 区域碳排放大数据监测与仿真决策平台 API")
                .description("后端接口文档（Java 业务层）；算法接口见 Python 算法服务")
                .version("1.0"));
    }
}
