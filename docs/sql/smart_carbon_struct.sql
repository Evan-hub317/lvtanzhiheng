/*
 Navicat Premium Data Transfer

 Source Server         : conn1
 Source Server Type    : MySQL
 Source Server Version : 80037 (8.0.37)
 Source Host           : localhost:3306
 Source Schema         : smart_carbon

 Target Server Type    : MySQL
 Target Server Version : 80037 (8.0.37)
 File Encoding         : 65001

 Date: 27/10/2026 19:08:33
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for agent_session
-- ----------------------------
DROP TABLE IF EXISTS `agent_session`;
CREATE TABLE `agent_session`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `user_id` bigint NOT NULL COMMENT '鎵?睘鐢ㄦ埛',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '浼氳瘽鏍囬?锛堥?闂?埅鍙栵級',
  `messages_json` json NULL COMMENT '闂?瓟娑堟伅鏁扮粍锛歔{role, content}, ...]',
  `steps_json` json NULL COMMENT '鎵ц?閾捐矾姝ラ?鏁扮粍锛歔{name, args, summary, durationMs, chart}, ...]',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 42 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI鍒嗘瀽鍔╂墜浼氳瘽锛堝?璇濅笌鎵ц?閾捐矾鐣欑棔锛' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for alert_record
-- ----------------------------
DROP TABLE IF EXISTS `alert_record`;
CREATE TABLE `alert_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_id` int NULL DEFAULT NULL COMMENT '触发规则ID（AI检测为NULL）',
  `rule_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '触发规则名称',
  `detect_type` tinyint NOT NULL COMMENT '检测方式：1阈值规则 2孤立森林AI检测',
  `region_id` int NOT NULL COMMENT '区域ID',
  `industry_id` int NOT NULL COMMENT '行业ID',
  `year` smallint NOT NULL COMMENT '年份',
  `month` tinyint NULL DEFAULT NULL COMMENT '月份（年度预警为NULL）',
  `actual_value` decimal(18, 4) NOT NULL COMMENT '实际值',
  `threshold_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '阈值/正常值',
  `anomaly_score` decimal(8, 4) NULL DEFAULT NULL COMMENT '异常分数（AI检测，越接近1越异常）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态：0待确认 1已确认',
  `handler` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '处理人',
  `handle_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_region`(`region_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1119 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '预警记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for alert_rule
-- ----------------------------
DROP TABLE IF EXISTS `alert_rule`;
CREATE TABLE `alert_rule`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则名称',
  `rule_type` tinyint NOT NULL COMMENT '类型：1环比超限 2同比超限 3总量上限',
  `dimension` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '监测维度：region区域/industry行业',
  `dimension_id` int NULL DEFAULT NULL COMMENT '维度ID，NULL表示全部',
  `threshold_value` decimal(18, 4) NOT NULL COMMENT '阈值（1/2为增幅%，3为总量tCO2）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rule_name`(`rule_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '预警规则表（定时任务扫描）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_fact_emission_month_20261025
-- ----------------------------
DROP TABLE IF EXISTS `bak_fact_emission_month_20261025`;
CREATE TABLE `bak_fact_emission_month_20261025`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `region_id` int NOT NULL COMMENT '区域ID',
  `industry_id` int NOT NULL COMMENT '行业ID',
  `energy_id` int NOT NULL COMMENT '能源品种ID',
  `year` smallint NOT NULL COMMENT '年份',
  `month` tinyint NOT NULL COMMENT '月份1-12',
  `emission` decimal(18, 4) NOT NULL COMMENT 'CO2排放量（tCO2）',
  `factor_value` decimal(12, 6) NULL DEFAULT NULL COMMENT '核算所用因子（留痕）',
  `oxid_rate` decimal(6, 4) NULL DEFAULT NULL COMMENT '核算所用氧化率（留痕）',
  `intensity` decimal(16, 6) NULL DEFAULT NULL COMMENT '碳强度（tCO2/万元GDP）',
  `yoy_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '同比增速（%）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核算时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rieym`(`region_id` ASC, `industry_id` ASC, `energy_id` ASC, `year` ASC, `month` ASC) USING BTREE,
  INDEX `idx_ry`(`region_id` ASC, `year` ASC) USING BTREE,
  INDEX `idx_year`(`year` ASC) USING BTREE,
  INDEX `idx_year_energy`(`year` ASC, `energy_id` ASC) USING BTREE,
  INDEX `idx_energy_year`(`energy_id` ASC, `year` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14066874 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '月度核算聚合结果表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_fact_emission_year_20261025
-- ----------------------------
DROP TABLE IF EXISTS `bak_fact_emission_year_20261025`;
CREATE TABLE `bak_fact_emission_year_20261025`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `region_id` int NOT NULL COMMENT '区域ID',
  `industry_id` int NOT NULL COMMENT '行业ID',
  `energy_id` int NOT NULL COMMENT '能源品种ID',
  `year` smallint NOT NULL COMMENT '年份',
  `emission` decimal(18, 4) NOT NULL COMMENT '年度CO2排放量（tCO2）',
  `yoy_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '同比增速（%）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '聚合时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_riey`(`region_id` ASC, `industry_id` ASC, `energy_id` ASC, `year` ASC) USING BTREE,
  INDEX `idx_year`(`year` ASC) USING BTREE,
  INDEX `idx_energy_year`(`energy_id` ASC, `year` ASC) USING BTREE,
  INDEX `idx_year_energy`(`year` ASC, `energy_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 780244 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '年度核算聚合结果表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bak_fact_energy_month_20261025
-- ----------------------------
DROP TABLE IF EXISTS `bak_fact_energy_month_20261025`;
CREATE TABLE `bak_fact_energy_month_20261025`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `region_id` int NOT NULL COMMENT '区域ID',
  `industry_id` int NOT NULL COMMENT '行业ID',
  `energy_id` int NOT NULL COMMENT '能源品种ID',
  `year` smallint NOT NULL COMMENT '年份',
  `month` tinyint NOT NULL COMMENT '月份1-12',
  `consumption` decimal(18, 4) NOT NULL COMMENT '活动数据量（单位与能源品种一致）',
  `data_source` tinyint NOT NULL DEFAULT 1 COMMENT '来源：1模拟生成 2Excel导入',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rieym`(`region_id` ASC, `industry_id` ASC, `energy_id` ASC, `year` ASC, `month` ASC) USING BTREE,
  INDEX `idx_riym`(`region_id` ASC, `industry_id` ASC, `year` ASC, `month` ASC) USING BTREE,
  INDEX `idx_year`(`year` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2824225 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '月度活动数据明细表（模拟数据主表）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chat_message
-- ----------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色：user/assistant',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息内容',
  `sources` json NULL COMMENT '引用来源：[{doc_name, chunk_index, similarity}]',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session`(`session_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 137 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI对话消息表（RAG问答留痕）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chat_session
-- ----------------------------
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '所属用户',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话标题（首问截取）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 42 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI对话会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dim_energy
-- ----------------------------
DROP TABLE IF EXISTS `dim_energy`;
CREATE TABLE `dim_energy`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `energy_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '能源编码',
  `energy_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '能源名称',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '计量单位（万吨/万立方米/万千瓦时等）',
  `energy_type` tinyint NOT NULL COMMENT '类型：1化石燃料 2电力 3热力',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_energy_code`(`energy_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '能源品种维度表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dim_industry
-- ----------------------------
DROP TABLE IF EXISTS `dim_industry`;
CREATE TABLE `dim_industry`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `industry_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '行业编码',
  `industry_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '行业名称',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_industry_code`(`industry_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '行业维度表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dim_region
-- ----------------------------
DROP TABLE IF EXISTS `dim_region`;
CREATE TABLE `dim_region`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `region_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '行政区划编码',
  `region_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '区域名称',
  `parent_id` int NOT NULL DEFAULT 0 COMMENT '父级ID，0为省级',
  `level` tinyint NOT NULL DEFAULT 2 COMMENT '层级：1省 2市',
  `is_municipality` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否直辖市：1=既是省也是市',
  `gdp` decimal(16, 2) NULL DEFAULT NULL COMMENT 'GDP（亿元），用于碳强度计算，可配置',
  `population` decimal(12, 2) NULL DEFAULT NULL COMMENT '常住人口（万人，人均口径）',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_region_code`(`region_code` ASC) USING BTREE,
  INDEX `idx_parent`(`parent_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1267 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '区域维度表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for fact_emission_month
-- ----------------------------
DROP TABLE IF EXISTS `fact_emission_month`;
CREATE TABLE `fact_emission_month`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `region_id` int NOT NULL COMMENT '区域ID',
  `industry_id` int NOT NULL COMMENT '行业ID',
  `energy_id` int NOT NULL COMMENT '能源品种ID',
  `year` smallint NOT NULL COMMENT '年份',
  `month` tinyint NOT NULL COMMENT '月份1-12',
  `emission` decimal(18, 4) NOT NULL COMMENT 'CO2排放量（tCO2）',
  `factor_value` decimal(12, 6) NULL DEFAULT NULL COMMENT '核算所用因子（留痕）',
  `oxid_rate` decimal(6, 4) NULL DEFAULT NULL COMMENT '核算所用氧化率（留痕）',
  `intensity` decimal(16, 6) NULL DEFAULT NULL COMMENT '碳强度（tCO2/万元GDP）',
  `yoy_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '同比增速（%）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核算时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rieym`(`region_id` ASC, `industry_id` ASC, `energy_id` ASC, `year` ASC, `month` ASC) USING BTREE,
  INDEX `idx_ry`(`region_id` ASC, `year` ASC) USING BTREE,
  INDEX `idx_year`(`year` ASC) USING BTREE,
  INDEX `idx_year_energy`(`year` ASC, `energy_id` ASC) USING BTREE,
  INDEX `idx_energy_year`(`energy_id` ASC, `year` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16729824 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '月度核算聚合结果表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for fact_emission_year
-- ----------------------------
DROP TABLE IF EXISTS `fact_emission_year`;
CREATE TABLE `fact_emission_year`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `region_id` int NOT NULL COMMENT '区域ID',
  `industry_id` int NOT NULL COMMENT '行业ID',
  `energy_id` int NOT NULL COMMENT '能源品种ID',
  `year` smallint NOT NULL COMMENT '年份',
  `emission` decimal(18, 4) NOT NULL COMMENT '年度CO2排放量（tCO2）',
  `yoy_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '同比增速（%）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '聚合时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_riey`(`region_id` ASC, `industry_id` ASC, `energy_id` ASC, `year` ASC) USING BTREE,
  INDEX `idx_year`(`year` ASC) USING BTREE,
  INDEX `idx_energy_year`(`energy_id` ASC, `year` ASC) USING BTREE,
  INDEX `idx_year_energy`(`year` ASC, `energy_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1083166 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '年度核算聚合结果表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for fact_energy_month
-- ----------------------------
DROP TABLE IF EXISTS `fact_energy_month`;
CREATE TABLE `fact_energy_month`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `region_id` int NOT NULL COMMENT '区域ID',
  `industry_id` int NOT NULL COMMENT '行业ID',
  `energy_id` int NOT NULL COMMENT '能源品种ID',
  `year` smallint NOT NULL COMMENT '年份',
  `month` tinyint NOT NULL COMMENT '月份1-12',
  `consumption` decimal(18, 4) NOT NULL COMMENT '活动数据量（单位与能源品种一致）',
  `data_source` tinyint NOT NULL DEFAULT 1 COMMENT '来源：1模拟生成 2Excel导入',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rieym`(`region_id` ASC, `industry_id` ASC, `energy_id` ASC, `year` ASC, `month` ASC) USING BTREE,
  INDEX `idx_riym`(`region_id` ASC, `industry_id` ASC, `year` ASC, `month` ASC) USING BTREE,
  INDEX `idx_year`(`year` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3970648 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '月度活动数据明细表（模拟数据主表）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for factor_emission
-- ----------------------------
DROP TABLE IF EXISTS `factor_emission`;
CREATE TABLE `factor_emission`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `energy_id` int NOT NULL COMMENT '能源品种ID',
  `grid_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '电网区域编码（仅电力因子使用）',
  `factor_value` decimal(12, 6) NOT NULL COMMENT '排放因子值（tCO2/单位，与能源单位对应）',
  `oxid_rate` decimal(6, 4) NOT NULL DEFAULT 1.0000 COMMENT '碳氧化率',
  `data_source` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据来源（指南名称/年份）',
  `effective_year` smallint NOT NULL DEFAULT 2024 COMMENT '生效年份',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_energy_grid_year`(`energy_id` ASC, `grid_code` ASC, `effective_year` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 27 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '排放因子库（核算公式：排放量 = 活动数据 × 因子 × 氧化率）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for kb_chunk
-- ----------------------------
DROP TABLE IF EXISTS `kb_chunk`;
CREATE TABLE `kb_chunk`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `doc_id` bigint NOT NULL COMMENT '所属文档ID',
  `chunk_index` int NOT NULL COMMENT '分块序号（从1开始）',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分块文本（500字符，重叠50）',
  `vector` json NULL COMMENT '文本向量（512维float32数组，算法服务写入）',
  `token_count` int NULL DEFAULT NULL COMMENT 'token数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_doc_chunk`(`doc_id` ASC, `chunk_index` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 476 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '知识库分块表（检索时全量向量加载内存做余弦相似度）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for kb_document
-- ----------------------------
DROP TABLE IF EXISTS `kb_document`;
CREATE TABLE `kb_document`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `doc_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文档名称',
  `doc_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型：pdf/docx/txt/md',
  `file_path` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件存储路径',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
  `chunk_count` int NOT NULL DEFAULT 0 COMMENT '分块数量',
  `uploader_id` bigint NULL DEFAULT NULL COMMENT '上传人',
  `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '知识库文档表（政策文件）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for province_param
-- ----------------------------
DROP TABLE IF EXISTS `province_param`;
CREATE TABLE `province_param`  (
  `region_id` int NOT NULL COMMENT '省级区域ID',
  `gdp` decimal(16, 2) NOT NULL COMMENT 'GDP（亿元）',
  `secondary_ratio` decimal(8, 2) NOT NULL COMMENT '第二产业占GDP比重（%）',
  `coal_ratio` decimal(8, 2) NOT NULL COMMENT '煤炭占能源消费比重（%）',
  `energy_intensity` decimal(8, 4) NOT NULL COMMENT '单位GDP能耗（吨标煤/万元）',
  `grid_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '电网区域：HB/DB/HD/HZ/XB/NF',
  `heating` tinyint NOT NULL DEFAULT 0 COMMENT '是否供暖省份：1是 0否',
  `gdp_growth` decimal(6, 2) NOT NULL DEFAULT 5.00 COMMENT 'GDP年均增速（%）',
  PRIMARY KEY (`region_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '省级经济能源特征参数（公开统计近似值）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for report_record
-- ----------------------------
DROP TABLE IF EXISTS `report_record`;
CREATE TABLE `report_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '报告标题',
  `region_id` int NOT NULL COMMENT '区域ID',
  `period_type` tinyint NOT NULL COMMENT '报告期：1月报 2年报',
  `report_year` smallint NOT NULL COMMENT '报告年份',
  `report_month` tinyint NULL DEFAULT NULL COMMENT '报告月份（年报为NULL）',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '报告正文（AI生成，含图表占位符）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0生成中 1成功 2失败',
  `creator_id` bigint NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_region_year`(`region_id` ASC, `report_year` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AIGC监测报告记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for scenario_record
-- ----------------------------
DROP TABLE IF EXISTS `scenario_record`;
CREATE TABLE `scenario_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenario_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '情景名称',
  `region_id` int NOT NULL COMMENT '区域ID',
  `preset_type` tinyint NOT NULL DEFAULT 0 COMMENT '预设：0自定义 1基准 2低碳 3强化低碳',
  `coal_ratio` decimal(8, 4) NOT NULL COMMENT '煤炭占能源消费比重（%）',
  `industry_ratio` decimal(8, 4) NOT NULL COMMENT '工业占GDP比重（%）',
  `tech_efficiency` decimal(8, 4) NOT NULL COMMENT '单位能耗年均下降率（%）',
  `peak_year` smallint NULL DEFAULT NULL COMMENT '预测达峰年份',
  `peak_emission` decimal(18, 4) NULL DEFAULT NULL COMMENT '预测峰值排放（tCO2）',
  `params_json` json NULL COMMENT '完整参数组合（含GDP增速等扩展参数）',
  `result_json` json NULL COMMENT '仿真结果摘要',
  `creator_id` bigint NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_region`(`region_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '情景仿真记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sim_result
-- ----------------------------
DROP TABLE IF EXISTS `sim_result`;
CREATE TABLE `sim_result`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenario_id` bigint NOT NULL COMMENT '情景ID',
  `year` smallint NOT NULL COMMENT '年份',
  `emission` decimal(18, 4) NOT NULL COMMENT '该年排放量（tCO2）',
  `lower_bound` decimal(18, 4) NULL DEFAULT NULL COMMENT '蒙特卡洛95%置信下界',
  `upper_bound` decimal(18, 4) NULL DEFAULT NULL COMMENT '蒙特卡洛95%置信上界',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_scenario_year`(`scenario_id` ASC, `year` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 37 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '情景仿真逐年轨迹表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stat_city_year
-- ----------------------------
DROP TABLE IF EXISTS `stat_city_year`;
CREATE TABLE `stat_city_year`  (
  `city_id` int NOT NULL,
  `year` int NOT NULL,
  `emission` decimal(20, 2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (`city_id`, `year`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '姹囨?-甯傜骇骞村害鎬婚噺锛堟帓琛岀敤锛' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stat_month_energy
-- ----------------------------
DROP TABLE IF EXISTS `stat_month_energy`;
CREATE TABLE `stat_month_energy`  (
  `region_id` int NOT NULL COMMENT '1=全国；省ID=该省',
  `energy_id` int NOT NULL COMMENT '能源品种ID',
  `year` int NOT NULL COMMENT '年份',
  `month` int NOT NULL COMMENT '月份',
  `emission` decimal(20, 2) NOT NULL DEFAULT 0.00 COMMENT '月度排放量',
  PRIMARY KEY (`region_id`, `energy_id`, `year`, `month`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '汇总表-月度能源结构' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stat_month_industry
-- ----------------------------
DROP TABLE IF EXISTS `stat_month_industry`;
CREATE TABLE `stat_month_industry`  (
  `region_id` int NOT NULL,
  `industry_id` int NOT NULL,
  `year` int NOT NULL,
  `month` int NOT NULL,
  `emission` decimal(20, 2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (`region_id`, `industry_id`, `year`, `month`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '姹囨?-鏈堝害琛屼笟缁撴瀯' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stat_month_total
-- ----------------------------
DROP TABLE IF EXISTS `stat_month_total`;
CREATE TABLE `stat_month_total`  (
  `region_id` int NOT NULL,
  `year` int NOT NULL,
  `month` int NOT NULL,
  `emission` decimal(20, 2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (`region_id`, `year`, `month`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '姹囨?-鏈堝害鎬婚噺锛堝叏鍥?鐪侊級' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stat_year_energy
-- ----------------------------
DROP TABLE IF EXISTS `stat_year_energy`;
CREATE TABLE `stat_year_energy`  (
  `region_id` int NOT NULL,
  `energy_id` int NOT NULL,
  `year` int NOT NULL,
  `emission` decimal(20, 2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (`region_id`, `energy_id`, `year`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '姹囨?-骞村害鑳芥簮缁撴瀯锛堝惈闂存帴鎺掓斁锛' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stat_year_industry
-- ----------------------------
DROP TABLE IF EXISTS `stat_year_industry`;
CREATE TABLE `stat_year_industry`  (
  `region_id` int NOT NULL,
  `industry_id` int NOT NULL,
  `year` int NOT NULL,
  `emission` decimal(20, 2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (`region_id`, `industry_id`, `year`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '姹囨?-骞村害琛屼笟缁撴瀯' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for stat_year_total
-- ----------------------------
DROP TABLE IF EXISTS `stat_year_total`;
CREATE TABLE `stat_year_total`  (
  `region_id` int NOT NULL COMMENT '1=鍏ㄥ浗锛涚渷ID=璇ョ渷',
  `year` int NOT NULL,
  `emission` decimal(20, 2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (`region_id`, `year`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '姹囨?-骞村害鎬婚噺锛堝叏鍥?鐪侊紝鐩存帴鎺掓斁鍙ｅ緞锛' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NULL DEFAULT NULL COMMENT '操作人ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作人账号',
  `operation` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作内容（如：执行核算、上传知识库文档）',
  `method` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求方法',
  `params` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求参数（截断）',
  `ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'IP地址',
  `duration_ms` int NULL DEFAULT NULL COMMENT '耗时（毫秒）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '结果：0成功 1失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码：ADMIN/USER',
  `role_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_code`(`role_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录账号',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码（BCrypt）',
  `real_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '姓名',
  `email` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像相对路径',
  `role_id` int NOT NULL COMMENT '角色ID',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  INDEX `idx_role`(`role_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
