-- =====================================================================
-- 全国数据生成功能 · 数据库迁移脚本（结构变更）
-- 执行时机：切换全国模式时执行（会清空旧"演示省"数据）
-- 执行顺序：
--   1) docs/sql/init.sql          建库建表（若已执行可跳过）
--   2) 本文件                      结构变更 + 旧数据清理
--   3) docs/sql/regions_cn.sql    全国行政区划 + 省级参数 + 电网因子数据
-- 幂等设计：本文件可重复执行
-- =====================================================================

USE smart_carbon;

-- ---------------------------------------------------------------------
-- 1. 省级参数表（province_param）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS province_param (
    region_id        INT           NOT NULL COMMENT '省级区域ID',
    gdp              DECIMAL(16,2) NOT NULL COMMENT 'GDP（亿元）',
    secondary_ratio  DECIMAL(8,2)  NOT NULL COMMENT '第二产业占GDP比重（%）',
    coal_ratio       DECIMAL(8,2)  NOT NULL COMMENT '煤炭占能源消费比重（%）',
    energy_intensity DECIMAL(8,4)  NOT NULL COMMENT '单位GDP能耗（吨标煤/万元）',
    grid_code        VARCHAR(10)   NOT NULL COMMENT '电网区域：HB/DB/HD/HZ/XB/NF',
    heating          TINYINT       NOT NULL DEFAULT 0 COMMENT '是否供暖省份：1是 0否',
    gdp_growth       DECIMAL(6,2)  NOT NULL DEFAULT 5.0 COMMENT 'GDP年均增速（%）',
    PRIMARY KEY (region_id)
) ENGINE=InnoDB COMMENT='省级经济能源特征参数（公开统计近似值）';

-- ---------------------------------------------------------------------
-- 2. 排放因子表增加电网区域维度（幂等）
-- ---------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'factor_emission' AND column_name = 'grid_code');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE factor_emission ADD COLUMN grid_code VARCHAR(10) DEFAULT NULL COMMENT ''电网区域编码（仅电力因子使用）'' AFTER energy_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2.5 因子表唯一键调整：支持同一能源品种按电网区域多版本
--     原唯一键 uk_energy_year (energy_id, effective_year) 会阻止电力 6 区域因子共存；
--     改为 (energy_id, grid_code, effective_year)，grid_code 为 NULL 时互不冲突（MySQL 特性）
-- ---------------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'factor_emission' AND index_name = 'uk_energy_year');
SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE factor_emission DROP INDEX uk_energy_year',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'factor_emission' AND index_name = 'uk_energy_grid_year');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE factor_emission ADD UNIQUE KEY uk_energy_grid_year (energy_id, grid_code, effective_year)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 清理旧"演示省"数据（用户已确认切换后删除）
--    演示数据特征：区域编码 XX 开头（演示省/市/区县）或 level=3 区县
-- ---------------------------------------------------------------------
DELETE FROM alert_record;                              -- 预警（关联旧区域）
DELETE FROM scenario_record;                           -- 情景记录
DELETE FROM sim_result;                                -- 情景轨迹
DELETE FROM report_record;                             -- 报告
DELETE FROM fact_energy_month WHERE data_source = 1;   -- 活动数据（模拟生成）
DELETE FROM fact_emission_month;                       -- 月度核算
DELETE FROM fact_emission_year;                        -- 年度核算
DELETE FROM dim_region WHERE region_code LIKE 'XX%' OR level = 3;  -- 旧演示区域（保留真实数据）

-- ---------------------------------------------------------------------
-- 4. 活动数据表增加唯一键（增量生成幂等防重；CG-10）
--    唯一口径：同一 区域×行业×能源×年×月 仅一条
-- ---------------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'fact_energy_month' AND index_name = 'uk_rieym');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE fact_energy_month ADD UNIQUE KEY uk_rieym (region_id, industry_id, energy_id, year, month)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 5. 电力因子替换：旧单一全国因子 → 六大区域电网因子（能源品种 7=电力）
--    因子值：生态环境部 2022 年度全国电网平均排放因子公告（tCO2/万千瓦时）
-- ---------------------------------------------------------------------
DELETE FROM factor_emission WHERE energy_id = 7;
INSERT IGNORE INTO factor_emission (energy_id, grid_code, factor_value, oxid_rate, data_source, effective_year, remark) VALUES
(7, 'HB', 9.810000,  1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '华北区域电网 tCO2/万千瓦时'),
(7, 'DB', 10.603000, 1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '东北区域电网 tCO2/万千瓦时'),
(7, 'HD', 7.496000,  1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '华东区域电网 tCO2/万千瓦时'),
(7, 'HZ', 6.231000,  1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '华中区域电网 tCO2/万千瓦时'),
(7, 'XB', 7.094000,  1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '西北区域电网 tCO2/万千瓦时'),
(7, 'NF', 5.217000,  1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '南方区域电网 tCO2/万千瓦时');

-- ---------------------------------------------------------------------
-- 迁移完成校验
-- ---------------------------------------------------------------------
SELECT 'province_param 表' AS item, COUNT(*) AS cnt FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'province_param'
UNION ALL SELECT 'fact_energy_month 唯一键', COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'fact_energy_month' AND index_name = 'uk_rieym'
UNION ALL SELECT 'factor_emission 电网因子行数', COUNT(*) FROM factor_emission WHERE energy_id = 7;
