package com.smart.service;

import com.smart.dto.ReportGenerateDTO;
import com.smart.entity.ReportRecord;

import java.util.List;

public interface ReportService {

    /**
     * 生成监测报告：聚合数据摘要 → AI 生成正文（失败降级模板）→ 落库
     */
    ReportRecord generate(ReportGenerateDTO dto, Long creatorId);

    /**
     * 报告列表
     */
    List<ReportRecord> list();

    /**
     * 报告详情
     */
    ReportRecord detail(long id);

    /**
     * 重新生成
     */
    ReportRecord regenerate(long id, Long creatorId);

    /**
     * 删除报告
     */
    void delete(long id);
}
