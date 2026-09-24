-- =====================================================================
-- AI 分析助手（对话式分析 Agent）会话表
-- 执行：mysql -uroot -p < docs/sql/agent_session.sql
-- =====================================================================
USE smart_carbon;

CREATE TABLE IF NOT EXISTS agent_session (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL COMMENT '所属用户',
    title         VARCHAR(100) NOT NULL COMMENT '会话标题（首问截取）',
    messages_json JSON         DEFAULT NULL COMMENT '问答消息数组：[{role, content}, ...]',
    steps_json    JSON         DEFAULT NULL COMMENT '执行链路步骤数组：[{name, args, summary, durationMs, chart}, ...]',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='AI分析助手会话（对话与执行链路留痕）';
