package com.smart.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.client.AlgoClient;
import com.smart.common.BizException;
import com.smart.entity.AlertRecord;
import com.smart.entity.AlertRule;
import com.smart.entity.DimEnergy;
import com.smart.mapper.AlertRecordMapper;
import com.smart.mapper.AlertRuleMapper;
import com.smart.mapper.DimEnergyMapper;
import com.smart.mapper.FactEmissionMonthMapper;
import com.smart.service.AlertService;
import com.smart.vo.AlertPageVO;
import com.smart.vo.AnomalyPointVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预警服务：阈值规则扫描 + 孤立森林 AI 异常检测
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    /** AI 检测结果入库上限（按异常分数降序取前 N） */
    private static final int AI_TOP_LIMIT = 100;

    /** 省级区域ID（行业级预警归属口径） */
    private static final int PROVINCE_REGION_ID = 1;

    private final AlertRuleMapper ruleMapper;
    private final AlertRecordMapper recordMapper;
    private final FactEmissionMonthMapper monthMapper;
    private final DimEnergyMapper energyMapper;
    private final AlgoClient algoClient;

    /**
     * 定时阈值扫描：每天 02:00 自动执行（演示可手动触发）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledScan() {
        try {
            int count = scanRules();
            log.info("定时阈值扫描完成，新增预警 {} 条", count);
        } catch (Exception e) {
            log.error("定时阈值扫描失败", e);
        }
    }

    @Override
    public int scanRules() {
        List<AlertRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getStatus, 1));
        int total = 0;
        for (AlertRule rule : rules) {
            if (rule.getRuleType() == null) {
                continue;
            }
            total += rule.getRuleType() == 1 ? scanMomRule(rule) : scanTotalRule(rule);
        }
        log.info("阈值扫描完成：规则 {} 条，新增预警 {} 条", rules.size(), total);
        return total;
    }

    /**
     * 环比超限规则：行业月度排放环比增幅 > 阈值
     */
    private int scanMomRule(AlertRule rule) {
        List<AnomalyPointVO> rows = monthMapper.selectIndustryMonthly();
        Map<Integer, List<AnomalyPointVO>> byIndustry = new LinkedHashMap<>();
        for (AnomalyPointVO row : rows) {
            byIndustry.computeIfAbsent(row.getIndustryId(), k -> new ArrayList<>()).add(row);
        }
        int count = 0;
        for (Map.Entry<Integer, List<AnomalyPointVO>> entry : byIndustry.entrySet()) {
            List<AnomalyPointVO> series = entry.getValue();
            for (int i = 1; i < series.size(); i++) {
                BigDecimal prev = series.get(i - 1).getEmission();
                BigDecimal cur = series.get(i).getEmission();
                if (prev.signum() <= 0) {
                    continue;
                }
                BigDecimal mom = cur.subtract(prev).multiply(BigDecimal.valueOf(100))
                        .divide(prev, 2, RoundingMode.HALF_UP);
                if (mom.compareTo(rule.getThresholdValue()) > 0) {
                    if (existsRecord(rule.getId(), PROVINCE_REGION_ID, entry.getKey(),
                            series.get(i).getYear(), series.get(i).getMonth())) {
                        continue;
                    }
                    AlertRecord record = new AlertRecord();
                    record.setRuleId(rule.getId());
                    record.setRuleName(rule.getRuleName());
                    record.setDetectType(1);
                    record.setRegionId(PROVINCE_REGION_ID);
                    record.setIndustryId(entry.getKey());
                    record.setYear(series.get(i).getYear());
                    record.setMonth(series.get(i).getMonth());
                    record.setActualValue(mom);
                    record.setThresholdValue(rule.getThresholdValue());
                    record.setStatus(0);
                    recordMapper.insert(record);
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 总量上限规则：年度区域排放总量 > 阈值
     */
    private int scanTotalRule(AlertRule rule) {
        List<AnomalyPointVO> rows = monthMapper.selectIndustryMonthly();
        Map<Integer, BigDecimal> yearTotal = new LinkedHashMap<>();
        for (AnomalyPointVO row : rows) {
            yearTotal.merge(row.getYear(), row.getEmission(), BigDecimal::add);
        }
        int count = 0;
        for (Map.Entry<Integer, BigDecimal> entry : yearTotal.entrySet()) {
            if (entry.getValue().compareTo(rule.getThresholdValue()) > 0) {
                int regionId = rule.getDimensionId() != null ? rule.getDimensionId() : PROVINCE_REGION_ID;
                if (existsRecord(rule.getId(), regionId, 0, entry.getKey(), null)) {
                    continue;
                }
                AlertRecord record = new AlertRecord();
                record.setRuleId(rule.getId());
                record.setRuleName(rule.getRuleName());
                record.setDetectType(1);
                record.setRegionId(regionId);
                record.setIndustryId(0);
                record.setYear(entry.getKey());
                record.setActualValue(entry.getValue());
                record.setThresholdValue(rule.getThresholdValue());
                record.setStatus(0);
                recordMapper.insert(record);
                count++;
            }
        }
        return count;
    }

    @Override
    public Map<String, Object> runAnomalyDetection() {
        long start = System.currentTimeMillis();
        // 1. 全量检测点：区县×行业×能源×月
        List<AnomalyPointVO> points = monthMapper.selectAnomalyPoints();
        if (points.isEmpty()) {
            throw new BizException("暂无核算数据，请先生成模拟数据并执行核算");
        }
        // 2. 按 (industry, energy) 分组建模：同组内各行业的季节规律一致，
        //    区县规模差异由标准化消除；24 组一次批量调用，秒级完成
        Map<String, List<AnomalyPointVO>> seriesMap = new LinkedHashMap<>();
        for (AnomalyPointVO p : points) {
            String key = p.getIndustryId() + "-" + p.getEnergyId();
            seriesMap.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        List<Map<String, Object>> seriesList = seriesMap.entrySet().stream().map(entry -> {
            Map<String, Object> series = new HashMap<>();
            series.put("key", entry.getKey());
            series.put("points", entry.getValue().stream().map(p -> {
                Map<String, Object> pt = new HashMap<>();
                pt.put("regionId", p.getRegionId());
                pt.put("year", p.getYear());
                pt.put("month", p.getMonth());
                pt.put("value", p.getEmission());
                return pt;
            }).collect(Collectors.toList()));
            return series;
        }).collect(Collectors.toList());

        // 3. 一次批量调用算法服务
        List<JSONObject> allAnomalies = callAnomalyBatch(seriesList);

        // 4. 重扫幂等：清除旧 AI 记录，按分数降序取 top N 入库
        recordMapper.deleteByDetectType(2);
        Map<Integer, String> energyNameMap = energyMapper.selectList(null).stream()
                .collect(Collectors.toMap(DimEnergy::getId, DimEnergy::getEnergyName, (a, b) -> a));
        allAnomalies.sort(Comparator.comparing(a -> a.getBigDecimal("score"), Comparator.reverseOrder()));
        int top = Math.min(allAnomalies.size(), AI_TOP_LIMIT);
        for (int i = 0; i < top; i++) {
            JSONObject a = allAnomalies.get(i);
            String[] keyParts = a.getStr("key", "--").split("-");
            if (keyParts.length < 2) {
                continue;
            }
            int industryId = Integer.parseInt(keyParts[0]);
            int energyId = Integer.parseInt(keyParts[1]);
            AlertRecord record = new AlertRecord();
            record.setRuleName("AI 异常检测·" + energyNameMap.getOrDefault(energyId, "能源" + energyId));
            record.setDetectType(2);
            record.setRegionId(a.getInt("regionId"));
            record.setIndustryId(industryId);
            record.setYear(a.getInt("year"));
            record.setMonth(a.getInt("month"));
            record.setActualValue(a.getBigDecimal("value"));
            record.setAnomalyScore(a.getBigDecimal("score"));
            record.setStatus(0);
            recordMapper.insert(record);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("series", seriesMap.size());
        result.put("anomalies", allAnomalies.size());
        result.put("saved", top);
        result.put("seconds", (System.currentTimeMillis() - start) / 1000);
        log.info("AI 异常检测完成：{} 个序列，检出 {} 条，入库 {} 条，耗时 {}s",
                seriesMap.size(), allAnomalies.size(), top, result.get("seconds"));
        return result;
    }

    /**
     * 调算法服务检测一批序列，返回异常点列表
     */
    private List<JSONObject> callAnomalyBatch(List<Map<String, Object>> batch) {
        JSONArray data = algoClient.postArray("/api/alg/anomaly",
                cn.hutool.json.JSONUtil.parseObj(cn.hutool.json.JSONUtil.toJsonStr(Map.of("series", batch))));
        return data == null ? new ArrayList<>() : data.toList(JSONObject.class);
    }

    @Override
    public IPage<AlertPageVO> page(int pageNum, int pageSize, Integer detectType, Integer status, Integer regionId) {
        return recordMapper.selectAlertPage(new Page<>(pageNum, pageSize), detectType, status, regionId);
    }

    @Override
    public void handle(long id, String username) {
        AlertRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException("预警不存在");
        }
        record.setStatus(1);
        record.setHandler(username);
        record.setHandleTime(LocalDateTime.now());
        recordMapper.updateById(record);
    }

    @Override
    public Map<String, Object> summary() {
        Map<String, Object> result = new HashMap<>();
        result.put("pending", recordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getStatus, 0)));
        result.put("ruleCount", recordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getDetectType, 1)));
        result.put("aiCount", recordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getDetectType, 2)));
        return result;
    }

    private boolean existsRecord(Integer ruleId, int regionId, int industryId, Integer year, Integer month) {
        return recordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(ruleId != null, AlertRecord::getRuleId, ruleId)
                .eq(AlertRecord::getRegionId, regionId)
                .eq(AlertRecord::getIndustryId, industryId)
                .eq(AlertRecord::getYear, year)
                .eq(month != null, AlertRecord::getMonth, month)
                .isNull(month == null, AlertRecord::getMonth)) > 0;
    }
}
