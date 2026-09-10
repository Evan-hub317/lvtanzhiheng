package com.smart.service;

import com.smart.vo.CalcResultVO;

public interface CalcService {

    /**
     * 执行碳核算：月度聚合 → 碳强度 → 年度聚合 → 同比
     * 幂等：重算前清理同范围旧结果
     *
     * @param startYear 起始年份（0 表示数据最早年份）
     * @param endYear   结束年份（0 表示数据最晚年份）
     */
    CalcResultVO execute(int startYear, int endYear);
}
