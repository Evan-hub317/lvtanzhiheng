package com.smart.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/**
 * DeepSeek 请求定制：向每个 /chat/completions 请求体注入 thinking=disabled。
 * <p>
 * 背景：Spring AI 1.0.2 的 DeepSeek 实现在请求侧不会回传 reasoning_content
 * （转换为 API 消息时硬编码 null），而 DeepSeek V4 思考模式要求多轮必须回传，
 * 否则返回 400。同时该版本不支持 thinking 开关参数。
 * 因此通过 RestClient 拦截器统一关闭思考模式（V4 通过请求参数切换思考/非思考），
 * 使多轮工具调用（AI 分析助手核心链路）正常工作，响应也更快更省 token。
 * <p>
 * 注意：本 RestClient.Builder 为全局 @Primary，仅 DeepSeek 自动配置消费；
 * 项目内无其他 RestClient.Builder 使用方。
 */
@Configuration
public class DeepSeekAiConfig {

    @Bean
    @Primary
    public RestClient.Builder deepSeekRestClientBuilder() {
        return RestClient.builder()
                // 缓冲请求体：拦截器需要读取并改写 body
                .requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                .requestInterceptor((request, body, execution) -> {
                    byte[] bytes = body;
                    if (bytes != null && bytes.length > 0) {
                        JSONObject json = JSONUtil.parseObj(new String(bytes, StandardCharsets.UTF_8));
                        if (!json.containsKey("thinking")) {
                            JSONObject thinking = new JSONObject();
                            thinking.set("type", "disabled");
                            json.set("thinking", thinking);
                        }
                        bytes = JSONUtil.toJsonStr(json).getBytes(StandardCharsets.UTF_8);
                    }
                    return execution.execute(request, bytes);
                });
    }
}
