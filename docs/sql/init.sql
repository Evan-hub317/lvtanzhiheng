-- =====================================================================
-- 区域碳排放大数据监测与仿真决策平台（绿碳智衡）
-- 数据库初始化脚本 V1.0
-- 适用：MySQL 8.0+   字符集：utf8mb4   引擎：InnoDB
-- 说明：本脚本可直接重复执行（IF NOT EXISTS + INSERT IGNORE 幂等）
-- 模块映射：M1用户权限 / M2数据管理 / M3核算 / M4大屏 / M5预警
--           M6仿真 / M7 AI碳管家(RAG) / M8 AIGC报告
-- =====================================================================

CREATE DATABASE IF NOT EXISTS smart_carbon DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE smart_carbon;

-- =====================================================================
-- 一、系统管理（M1）
-- =====================================================================

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code   VARCHAR(50)  NOT NULL COMMENT '角色编码：ADMIN/USER',
    role_name   VARCHAR(50)  NOT NULL COMMENT '角色名称',
    description VARCHAR(200) DEFAULT NULL COMMENT '描述',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='角色表';

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
    real_name   VARCHAR(50)  DEFAULT NULL COMMENT '姓名',
    role_id     INT          NOT NULL COMMENT '角色ID',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role (role_id)
) ENGINE=InnoDB COMMENT='用户表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS sys_log (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT        DEFAULT NULL COMMENT '操作人ID',
    username    VARCHAR(50)   DEFAULT NULL COMMENT '操作人账号',
    operation   VARCHAR(100)  NOT NULL COMMENT '操作内容（如：执行核算、上传知识库文档）',
    method      VARCHAR(200)  DEFAULT NULL COMMENT '请求方法',
    params      VARCHAR(1000) DEFAULT NULL COMMENT '请求参数（截断）',
    ip          VARCHAR(50)   DEFAULT NULL COMMENT 'IP地址',
    duration_ms INT           DEFAULT NULL COMMENT '耗时（毫秒）',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '结果：0成功 1失败',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='操作日志表';

-- =====================================================================
-- 二、维度字典（M2）
-- =====================================================================

-- 区域表（省/市两级）
CREATE TABLE IF NOT EXISTS dim_region (
    id          INT           NOT NULL AUTO_INCREMENT COMMENT '主键',
    region_code VARCHAR(20)   NOT NULL COMMENT '行政区划编码',
    region_name VARCHAR(50)   NOT NULL COMMENT '区域名称',
    parent_id   INT           NOT NULL DEFAULT 0 COMMENT '父级ID，0为省级',
    level       TINYINT       NOT NULL DEFAULT 2 COMMENT '层级：1省 2市',
    gdp         DECIMAL(16,2) DEFAULT NULL COMMENT 'GDP（亿元），用于碳强度计算，可配置',
    sort_order  INT           NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_region_code (region_code),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB COMMENT='区域维度表';

-- 行业表
CREATE TABLE IF NOT EXISTS dim_industry (
    id            INT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    industry_code VARCHAR(20) NOT NULL COMMENT '行业编码',
    industry_name VARCHAR(50) NOT NULL COMMENT '行业名称',
    sort_order    INT         NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_industry_code (industry_code)
) ENGINE=InnoDB COMMENT='行业维度表';

-- 能源品种表
CREATE TABLE IF NOT EXISTS dim_energy (
    id          INT           NOT NULL AUTO_INCREMENT COMMENT '主键',
    energy_code VARCHAR(20)   NOT NULL COMMENT '能源编码',
    energy_name VARCHAR(50)   NOT NULL COMMENT '能源名称',
    unit        VARCHAR(20)   NOT NULL COMMENT '计量单位（万吨/万立方米/万千瓦时等）',
    energy_type TINYINT       NOT NULL COMMENT '类型：1化石燃料 2电力 3热力',
    sort_order  INT           NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_energy_code (energy_code)
) ENGINE=InnoDB COMMENT='能源品种维度表';

-- =====================================================================
-- 三、排放因子库（M2/M3）
-- =====================================================================

CREATE TABLE IF NOT EXISTS factor_emission (
    id            INT           NOT NULL AUTO_INCREMENT COMMENT '主键',
    energy_id     INT           NOT NULL COMMENT '能源品种ID',
    factor_value  DECIMAL(12,6) NOT NULL COMMENT '排放因子值（tCO2/单位，与能源单位对应）',
    oxid_rate     DECIMAL(6,4)  NOT NULL DEFAULT 1.0000 COMMENT '碳氧化率',
    data_source   VARCHAR(200)  DEFAULT NULL COMMENT '数据来源（指南名称/年份）',
    effective_year SMALLINT     NOT NULL DEFAULT 2024 COMMENT '生效年份',
    remark        VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_energy_year (energy_id, effective_year)
) ENGINE=InnoDB COMMENT='排放因子库（核算公式：排放量 = 活动数据 × 因子 × 氧化率）';

-- =====================================================================
-- 四、事实数据表（M2/M3）
-- =====================================================================

-- 月度活动数据明细（模拟数据生成器输出 ~100 万条）
CREATE TABLE IF NOT EXISTS fact_energy_month (
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    region_id   INT            NOT NULL COMMENT '区域ID',
    industry_id INT            NOT NULL COMMENT '行业ID',
    energy_id   INT            NOT NULL COMMENT '能源品种ID',
    year        SMALLINT       NOT NULL COMMENT '年份',
    month       TINYINT        NOT NULL COMMENT '月份1-12',
    consumption DECIMAL(18,4)  NOT NULL COMMENT '活动数据量（单位与能源品种一致）',
    data_source TINYINT        NOT NULL DEFAULT 1 COMMENT '来源：1模拟生成 2Excel导入',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_riym (region_id, industry_id, year, month),
    KEY idx_year (year)
) ENGINE=InnoDB COMMENT='月度活动数据明细表（模拟数据主表）';
-- 性能说明：100 万条明细仅用于核算与钻取，大屏查询走下方月度/年度聚合表；
-- 如需演示"大数据分区"，可将主键改为 (id, year) 后按年 RANGE 分区。

-- 月度核算聚合结果
CREATE TABLE IF NOT EXISTS fact_emission_month (
    id           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    region_id    INT            NOT NULL COMMENT '区域ID',
    industry_id  INT            NOT NULL COMMENT '行业ID',
    energy_id    INT            NOT NULL COMMENT '能源品种ID',
    year         SMALLINT       NOT NULL COMMENT '年份',
    month        TINYINT        NOT NULL COMMENT '月份1-12',
    emission     DECIMAL(18,4)  NOT NULL COMMENT 'CO2排放量（tCO2）',
    factor_value DECIMAL(12,6)  DEFAULT NULL COMMENT '核算所用因子（留痕）',
    oxid_rate    DECIMAL(6,4)   DEFAULT NULL COMMENT '核算所用氧化率（留痕）',
    intensity    DECIMAL(16,6)  DEFAULT NULL COMMENT '碳强度（tCO2/万元GDP）',
    yoy_rate     DECIMAL(10,4)  DEFAULT NULL COMMENT '同比增速（%）',
    create_time  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核算时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rieym (region_id, industry_id, energy_id, year, month),
    KEY idx_ry (region_id, year),
    KEY idx_year (year)
) ENGINE=InnoDB COMMENT='月度核算聚合结果表';

-- 年度核算聚合结果（大屏趋势图/KPI 主要数据源）
CREATE TABLE IF NOT EXISTS fact_emission_year (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    region_id   INT           NOT NULL COMMENT '区域ID',
    industry_id INT           NOT NULL COMMENT '行业ID',
    energy_id   INT           NOT NULL COMMENT '能源品种ID',
    year        SMALLINT      NOT NULL COMMENT '年份',
    emission    DECIMAL(18,4) NOT NULL COMMENT '年度CO2排放量（tCO2）',
    yoy_rate    DECIMAL(10,4) DEFAULT NULL COMMENT '同比增速（%）',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '聚合时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_riey (region_id, industry_id, energy_id, year),
    KEY idx_year (year)
) ENGINE=InnoDB COMMENT='年度核算聚合结果表';

-- =====================================================================
-- 五、预警模块（M5）
-- =====================================================================

-- 预警规则表
CREATE TABLE IF NOT EXISTS alert_rule (
    id              INT           NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_name       VARCHAR(100)  NOT NULL COMMENT '规则名称',
    rule_type       TINYINT       NOT NULL COMMENT '类型：1环比超限 2同比超限 3总量上限',
    dimension       VARCHAR(20)   NOT NULL COMMENT '监测维度：region区域/industry行业',
    dimension_id    INT           DEFAULT NULL COMMENT '维度ID，NULL表示全部',
    threshold_value DECIMAL(18,4) NOT NULL COMMENT '阈值（1/2为增幅%，3为总量tCO2）',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_name (rule_name)
) ENGINE=InnoDB COMMENT='预警规则表（定时任务扫描）';

-- 预警记录表
CREATE TABLE IF NOT EXISTS alert_record (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_id         INT           DEFAULT NULL COMMENT '触发规则ID（AI检测为NULL）',
    rule_name       VARCHAR(100)  DEFAULT NULL COMMENT '触发规则名称',
    detect_type     TINYINT       NOT NULL COMMENT '检测方式：1阈值规则 2孤立森林AI检测',
    region_id       INT           NOT NULL COMMENT '区域ID',
    industry_id     INT           NOT NULL COMMENT '行业ID',
    year            SMALLINT      NOT NULL COMMENT '年份',
    month           TINYINT       DEFAULT NULL COMMENT '月份（年度预警为NULL）',
    actual_value    DECIMAL(18,4) NOT NULL COMMENT '实际值',
    threshold_value DECIMAL(18,4) DEFAULT NULL COMMENT '阈值/正常值',
    anomaly_score   DECIMAL(8,4)  DEFAULT NULL COMMENT '异常分数（AI检测，越接近1越异常）',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '处理状态：0待确认 1已确认',
    handler         VARCHAR(50)   DEFAULT NULL COMMENT '处理人',
    handle_time     DATETIME      DEFAULT NULL COMMENT '处理时间',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_create_time (create_time),
    KEY idx_region (region_id)
) ENGINE=InnoDB COMMENT='预警记录表';

-- =====================================================================
-- 六、情景仿真模块（M6）
-- =====================================================================

-- 情景记录表
CREATE TABLE IF NOT EXISTS scenario_record (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    scenario_name   VARCHAR(100)  NOT NULL COMMENT '情景名称',
    region_id       INT           NOT NULL COMMENT '区域ID',
    preset_type     TINYINT       NOT NULL DEFAULT 0 COMMENT '预设：0自定义 1基准 2低碳 3强化低碳',
    coal_ratio      DECIMAL(8,4)  NOT NULL COMMENT '煤炭占能源消费比重（%）',
    industry_ratio  DECIMAL(8,4)  NOT NULL COMMENT '工业占GDP比重（%）',
    tech_efficiency DECIMAL(8,4)  NOT NULL COMMENT '单位能耗年均下降率（%）',
    peak_year       SMALLINT      DEFAULT NULL COMMENT '预测达峰年份',
    peak_emission   DECIMAL(18,4) DEFAULT NULL COMMENT '预测峰值排放（tCO2）',
    params_json     JSON          DEFAULT NULL COMMENT '完整参数组合（含GDP增速等扩展参数）',
    result_json     JSON          DEFAULT NULL COMMENT '仿真结果摘要',
    creator_id      BIGINT        DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_region (region_id)
) ENGINE=InnoDB COMMENT='情景仿真记录表';

-- 仿真结果轨迹表（每个情景未来逐年排放点）
CREATE TABLE IF NOT EXISTS sim_result (
    id           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    scenario_id  BIGINT         NOT NULL COMMENT '情景ID',
    year         SMALLINT       NOT NULL COMMENT '年份',
    emission     DECIMAL(18,4)  NOT NULL COMMENT '该年排放量（tCO2）',
    lower_bound  DECIMAL(18,4)  DEFAULT NULL COMMENT '蒙特卡洛95%置信下界',
    upper_bound  DECIMAL(18,4)  DEFAULT NULL COMMENT '蒙特卡洛95%置信上界',
    create_time  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scenario_year (scenario_id, year)
) ENGINE=InnoDB COMMENT='情景仿真逐年轨迹表';

-- =====================================================================
-- 七、AI 碳管家：知识库与对话（M7）
-- =====================================================================

-- 知识库文档表
CREATE TABLE IF NOT EXISTS kb_document (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    doc_name    VARCHAR(200) NOT NULL COMMENT '文档名称',
    doc_type    VARCHAR(20)  NOT NULL COMMENT '类型：pdf/docx/txt/md',
    file_path   VARCHAR(300) DEFAULT NULL COMMENT '文件存储路径',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    chunk_count INT          NOT NULL DEFAULT 0 COMMENT '分块数量',
    uploader_id BIGINT       DEFAULT NULL COMMENT '上传人',
    upload_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='知识库文档表（政策文件）';

-- 知识库分块表（RAG 检索单位）
CREATE TABLE IF NOT EXISTS kb_chunk (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    doc_id      BIGINT      NOT NULL COMMENT '所属文档ID',
    chunk_index INT         NOT NULL COMMENT '分块序号（从1开始）',
    content     TEXT        NOT NULL COMMENT '分块文本（500字符，重叠50）',
    vector      JSON        DEFAULT NULL COMMENT '文本向量（512维float32数组，算法服务写入）',
    token_count INT         DEFAULT NULL COMMENT 'token数',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_chunk (doc_id, chunk_index)
) ENGINE=InnoDB COMMENT='知识库分块表（检索时全量向量加载内存做余弦相似度）';

-- 对话会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '所属用户',
    title       VARCHAR(100) NOT NULL COMMENT '会话标题（首问截取）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='AI对话会话表';

-- 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id  BIGINT      NOT NULL COMMENT '会话ID',
    role        VARCHAR(20) NOT NULL COMMENT '角色：user/assistant',
    content     TEXT        NOT NULL COMMENT '消息内容',
    sources     JSON        DEFAULT NULL COMMENT '引用来源：[{doc_name, chunk_index, similarity}]',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_session (session_id)
) ENGINE=InnoDB COMMENT='AI对话消息表（RAG问答留痕）';

-- =====================================================================
-- 八、AIGC 报告（M8）
-- =====================================================================

CREATE TABLE IF NOT EXISTS report_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(200) NOT NULL COMMENT '报告标题',
    region_id   INT          NOT NULL COMMENT '区域ID',
    period_type TINYINT      NOT NULL COMMENT '报告期：1月报 2年报',
    report_year SMALLINT     NOT NULL COMMENT '报告年份',
    report_month TINYINT     DEFAULT NULL COMMENT '报告月份（年报为NULL）',
    content     MEDIUMTEXT   DEFAULT NULL COMMENT '报告正文（AI生成，含图表占位符）',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0生成中 1成功 2失败',
    creator_id  BIGINT       DEFAULT NULL COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (id),
    KEY idx_region_year (region_id, report_year)
) ENGINE=InnoDB COMMENT='AIGC监测报告记录表';

-- =====================================================================
-- 九、初始化数据
-- =====================================================================

-- 角色
INSERT IGNORE INTO sys_role (id, role_code, role_name, description) VALUES
(1, 'ADMIN', '系统管理员', '平台运维，含系统配置与知识库维护'),
(2, 'USER',  '业务用户',   '数据查看、分析、仿真、报告、AI问答');

-- 用户（初始密码均为 admin123；若 BCrypt 校验失败请用后端工具类重新生成替换）
INSERT IGNORE INTO sys_user (id, username, password, real_name, role_id, status) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '平台管理员', 1, 1),
(2, 'demo',  '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '演示用户',   2, 1);

-- 区域（演示省 + 8 市，行政区划与 GDP 均为演示配置，可替换）
INSERT IGNORE INTO dim_region (id, region_code, region_name, parent_id, level, gdp, sort_order) VALUES
(1, 'XX',   '演示省', 0, 1, 85000.00, 1),
(2, 'XX01', '云岭市', 1, 2, 12000.00, 1),
(3, 'XX02', '江滨市', 1, 2, 9500.00,  2),
(4, 'XX03', '临海市', 1, 2, 8800.00,  3),
(5, 'XX04', '中原市', 1, 2, 10200.00, 4),
(6, 'XX05', '川西市', 1, 2, 7600.00,  5),
(7, 'XX06', '河东市', 1, 2, 8900.00,  6),
(8, 'XX07', '平川市', 1, 2, 6800.00,  7),
(9, 'XX08', '苍山市', 1, 2, 7200.00,  8);

-- 行业
INSERT IGNORE INTO dim_industry (id, industry_code, industry_name, sort_order) VALUES
(1, 'DL', '电力生产', 1),
(2, 'GY', '工业',     2),
(3, 'JZ', '建筑',     3),
(4, 'JT', '交通',     4),
(5, 'NY', '农业',     5);

-- 能源品种
INSERT IGNORE INTO dim_energy (id, energy_code, energy_name, unit, energy_type, sort_order) VALUES
(1, 'YM', '原煤',   '万吨',     1, 1),
(2, 'JT', '焦炭',   '万吨',     1, 2),
(3, 'YY', '原油',   '万吨',     1, 3),
(4, 'QY', '汽油',   '万吨',     1, 4),
(5, 'CY', '柴油',   '万吨',     1, 5),
(6, 'TRQ', '天然气', '万立方米', 1, 6),
(7, 'DL', '电力',   '万千瓦时', 2, 7),
(8, 'RL', '热力',   '万吉焦',   3, 8);

-- 排放因子（示例值，依据《省级温室气体清单编制指南（试行）》及区域电网排放因子，可在系统内维护更新）
INSERT IGNORE INTO factor_emission (id, energy_id, factor_value, oxid_rate, data_source, effective_year, remark) VALUES
(1, 1, 1.900300, 0.9300, '省级温室气体清单编制指南', 2024, '原煤 tCO2/吨'),
(2, 2, 2.860400, 0.9300, '省级温室气体清单编制指南', 2024, '焦炭 tCO2/吨'),
(3, 3, 3.020200, 0.9800, '省级温室气体清单编制指南', 2024, '原油 tCO2/吨'),
(4, 4, 2.925100, 0.9800, '省级温室气体清单编制指南', 2024, '汽油 tCO2/吨'),
(5, 5, 3.095900, 0.9800, '省级温室气体清单编制指南', 2024, '柴油 tCO2/吨'),
(6, 6, 21.622000, 0.9900, '省级温室气体清单编制指南', 2024, '天然气 tCO2/万立方米'),
(7, 7, 5.810000, 1.0000, '生态环境部区域电网平均排放因子', 2024, '电力 tCO2/万千瓦时（示例，以最新公告为准）'),
(8, 8, 110.000000, 1.0000, '省级温室气体清单编制指南', 2024, '热力 tCO2/万吉焦');

-- 预警规则（示例；年度上限按演示省 7.1 万亿 GDP 校准，年排放约 5.5 亿吨）
INSERT IGNORE INTO alert_rule (id, rule_name, rule_type, dimension, dimension_id, threshold_value, status) VALUES
(1, '行业月度排放环比增幅超限', 1, 'industry', NULL, 20.0000, 1),
(2, '区域年度排放总量超上限',   3, 'region',   1,    600000000.0000, 1);

-- 预设情景（基准/低碳/强化低碳，参数可在仿真器中调整）
INSERT IGNORE INTO scenario_record (id, scenario_name, region_id, preset_type, coal_ratio, industry_ratio, tech_efficiency) VALUES
(1, '基准情景',     1, 1, 58.0000, 42.0000, 1.5000),
(2, '低碳情景',     1, 2, 48.0000, 36.0000, 2.5000),
(3, '强化低碳情景', 1, 3, 38.0000, 30.0000, 3.5000);
