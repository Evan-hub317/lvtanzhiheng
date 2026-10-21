package com.smart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多模型故障转移路由：主模型（DeepSeek）优先，失败按异常分类回退备用模型（qwen3.8-flash）。
 * <p>
 * 回退判定只在阻塞调用（.call()）层面进行——Agent 的模型调用均为阻塞式，
 * 天然满足"流式输出开始前切换"的约束；流式调用方（若未来接入）须保证
 * 在首个元素发出前调用 {@link #isFallbackEligible} 判定并整段切换。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRouter {

    private final ChatClient deepSeekChatClient;
    private final ChatClient qwenChatClient;

    /**
     * 主模型调用，失败按分类回退备用模型；不可回退的错误原样抛出。
     *
     * @param messages        消息列表（模型无关的 Spring AI Message）
     * @param primaryOptions  主模型专属选项（DeepSeekChatOptions：工具回调/内部执行开关等）
     * @param fallbackOptions 备用模型专属选项（OpenAiChatOptions，与主模型同构）
     */
    public ChatResponse callWithFallback(List<Message> messages, ChatOptions primaryOptions, ChatOptions fallbackOptions) {
        try {
            return deepSeekChatClient.prompt()
                    .messages(messages)
                    .options(primaryOptions)
                    .call()
                    .chatResponse();
        } catch (Exception e) {
            if (isFallbackEligible(e)) {
                log.warn("主模型调用失败，回退备用模型 qwen3.8-flash：{} - {}", e.getClass().getSimpleName(), e.getMessage());
                return qwenChatClient.prompt()
                        .messages(messages)
                        .options(fallbackOptions)
                        .call()
                        .chatResponse();
            }
            throw e;
        }
    }

    /**
     * 回退资格判定：连接类故障、鉴权失效（401/403）、限流（429）、服务端错误（5xx）可回退；
     * 400 类（请求本身问题，换模型同样失败）不回退。
     * <p>
     * 注意：Spring AI 1.0.2 的重试自动配置会把 HTTP 错误包装为 TransientAiException（5xx/429，
     * 框架内部重试后抛出）或 NonTransientAiException（其余，仅消息含 "HTTP xxx" 状态码），
     * 两者均无 cause 链，必须直接识别。
     */
    public boolean isFallbackEligible(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof ResourceAccessException) {
                return true;
            }
            if (t instanceof TransientAiException) {
                return true;
            }
            if (t instanceof NonTransientAiException) {
                return isAuthOrRateLimitOrServerStatus(t.getMessage());
            }
            if (t instanceof RestClientResponseException r) {
                int code = r.getStatusCode().value();
                return code == 401 || code == 403 || code == 429 || code >= 500;
            }
            t = t.getCause();
        }
        return false;
    }

    /** 从 Spring AI 异常消息（"HTTP 401 - ..."）提取状态码并按回退规则判定 */
    private boolean isAuthOrRateLimitOrServerStatus(String message) {
        if (message == null) {
            return false;
        }
        Matcher m = Pattern.compile("HTTP\\s+(\\d{3})").matcher(message);
        if (m.find()) {
            int code = Integer.parseInt(m.group(1));
            return code == 401 || code == 403 || code == 429 || code >= 500;
        }
        return false;
    }
}
