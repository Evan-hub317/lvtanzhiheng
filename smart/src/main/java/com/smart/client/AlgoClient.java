package com.smart.client;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.smart.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Python 算法服务客户端（HTTP 调用，Java 业务层与算法层解耦）
 */
@Slf4j
@Component
public class AlgoClient {

    @Value("${algo.base-url:http://localhost:8000}")
    private String baseUrl;

    /**
     * POST JSON 调用算法服务，统一处理返回码
     *
     * @param path  接口路径（如 /api/alg/simulate）
     * @param body  请求体对象
     * @return 响应中的 data 部分（JSONObject）
     */
    public JSONObject post(String path, Object body) {
        String url = baseUrl + path;
        String resp;
        try {
            resp = HttpUtil.post(url, JSONUtil.toJsonStr(body), 60000);
        } catch (Exception e) {
            log.error("算法服务调用失败: {}", url, e);
            throw new BizException("算法服务不可用，请确认已启动 Python 算法服务（algo 目录：uvicorn main:app --port 8000）");
        }
        JSONObject json = JSONUtil.parseObj(resp);
        int code = json.getInt("code", 500);
        if (code != 200) {
            throw new BizException(json.getStr("msg", "算法服务返回异常"));
        }
        return json.getJSONObject("data");
    }
}
