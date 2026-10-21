package com.smart.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 多模型 ChatClient 装配（主备模型）：
 * 主：DeepSeek（AI 分析助手链路，配置 spring.ai.deepseek.*）
 * 备：qwen3.8-flash（OpenAI 兼容端点，配置 spring.ai.openai.*，故障转移用）
 * <p>
 * 注意：@Primary ChatClient.Builder 同时抑制 Spring AI 自动配置的 Builder，
 * 现有注入点（AgentServiceImpl 等）无需改动即继续使用 DeepSeek 主模型。
 * 故障转移逻辑（AiRouter）接入后，业务代码注入两个具名 ChatClient 而非 Builder。
 */
@Configuration
public class AiModelConfig {

    /** 主模型 Builder（@Primary：保持既有 ChatClient.Builder 注入点行为不变） */
    @Bean
    @Primary
    public ChatClient.Builder primaryChatClientBuilder(DeepSeekChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel);
    }

    /** 主模型 ChatClient：DeepSeek */
    @Bean
    public ChatClient deepSeekChatClient(DeepSeekChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel).build();
    }

    /** 备用模型 ChatClient：千问 qwen3.8-flash（OpenAI 兼容端点） */
    @Bean
    public ChatClient qwenChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }
}
