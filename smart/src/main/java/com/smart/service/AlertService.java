package com.smart.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smart.vo.AlertPageVO;

import java.util.Map;

public interface AlertService {

    /**
     * 执行阈值规则扫描（环比超限 / 总量上限），重扫幂等
     * @return 本次新增预警条数
     */
    int scanRules();

    /**
     * 执行孤立森林 AI 异常检测（对接 Python 算法服务），重扫幂等
     * @return {total, anomalies, topCount, seconds}
     */
    Map<String, Object> runAnomalyDetection();

    /**
     * 预警分页查询
     */
    IPage<AlertPageVO> page(int pageNum, int pageSize, Integer detectType, Integer status, Integer regionId);

    /**
     * 确认预警
     */
    void handle(long id, String username);

    /**
     * 预警统计摘要（未处理数/阈值预警数/AI检测数）
     */
    Map<String, Object> summary();
}
