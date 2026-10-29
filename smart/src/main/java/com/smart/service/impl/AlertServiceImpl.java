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
import com.smart.entity.DimRegion;
import com.smart.mapper.AlertRecordMapper;
import com.smart.mapper.AlertRuleMapper;
import com.smart.mapper.DimEnergyMapper;
import com.smart.mapper.DimRegionMapper;
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

    /** 全国哨兵值：dim_region 无 id=1 行，stat 表与预警记录以 region_id=1 表示全国口径 */
    private static final int NATIONAL_REGION_ID = 1;

    private final AlertRuleMapper ruleMapper;
    private final AlertRecordMapper recordMapper;
    private final FactEmissionMonthMapper monthMapper;
    private final DimEnergyMapper energyMapper;
    private final DimRegionMapper regionMapper;
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
     * 环比超限规则：行业×能源月度排放环比增幅 > 阈值（全国口径，仅直接能源 energy_id≤6——
     * 电力/热力为间接排放单列展示、不计总量，季节摆动大，不参与环比检测）
     * 规则名标注具体能源（如「行业月度排放环比增幅超限·原煤」），与 AI 检测的命名方式一致
     */
    private int scanMomRule(AlertRule rule) {
        List<AnomalyPointVO> rows = monthMapper.selectIndustryEnergyMonthly(true);
        Map<String, List<AnomalyPointVO>> bySeries = new LinkedHashMap<>();
        for (AnomalyPointVO row : rows) {
            bySeries.computeIfAbsent(row.getIndustryId() + "-" + row.getEnergyId(), k -> new ArrayList<>()).add(row);
        }
        Map<Integer, String> energyNameMap = loadEnergyNameMap();
        int count = 0;
        for (List<AnomalyPointVO> series : bySeries.values()) {
            for (int i = 1; i < series.size(); i++) {
                BigDecimal prev = series.get(i - 1).getEmission();
                BigDecimal cur = series.get(i).getEmission();
                if (prev.signum() <= 0) {
                    continue;
                }
                BigDecimal mom = cur.subtract(prev).multiply(BigDecimal.valueOf(100))
                        .divide(prev, 2, RoundingMode.HALF_UP);
                if (mom.compareTo(rule.getThresholdValue()) > 0) {
                    AnomalyPointVO point = series.get(i);
                    String ruleName = rule.getRuleName() + "·"
                            + energyNameMap.getOrDefault(point.getEnergyId(), "能源" + point.getEnergyId());
                    if (existsRecord(rule.getId(), NATIONAL_REGION_ID, point.getIndustryId(),
                            point.getYear(), point.getMonth(), ruleName)) {
                        continue;
                    }
                    AlertRecord record = new AlertRecord();
                    record.setRuleId(rule.getId());
                    record.setRuleName(ruleName);
                    record.setDetectType(1);
                    record.setRegionId(NATIONAL_REGION_ID);
                    record.setIndustryId(point.getIndustryId());
                    record.setYear(point.getYear());
                    record.setMonth(point.getMonth());
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
     * 总量上限规则：年度区域排放总量 > 阈值（规则名标注区域，如「区域年度排放总量超上限·全国」；
     * 该规则无能源维度，不标注能源，避免与直接能源口径的检测结果混淆）
     */
    private int scanTotalRule(AlertRule rule) {
        List<AnomalyPointVO> rows = monthMapper.selectIndustryEnergyMonthly(false);
        Map<Integer, BigDecimal> yearTotal = new LinkedHashMap<>();
        for (AnomalyPointVO row : rows) {
            yearTotal.merge(row.getYear(), row.getEmission(), BigDecimal::add);
        }
        int count = 0;
        for (Map.Entry<Integer, BigDecimal> entry : yearTotal.entrySet()) {
            if (entry.getValue().compareTo(rule.getThresholdValue()) > 0) {
                int regionId = rule.getDimensionId() != null ? rule.getDimensionId() : NATIONAL_REGION_ID;
                String ruleName = rule.getRuleName() + "·" + regionDisplayName(regionId);
                if (existsRecord(rule.getId(), regionId, 0, entry.getKey(), null, ruleName)) {
                    continue;
                }
                AlertRecord record = new AlertRecord();
                record.setRuleId(rule.getId());
                record.setRuleName(ruleName);
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

    /** 能源名称表（阈值规则名标注用） */
    private Map<Integer, String> loadEnergyNameMap() {
        return energyMapper.selectList(null).stream()
                .collect(Collectors.toMap(DimEnergy::getId, DimEnergy::getEnergyName, (a, b) -> a));
    }

    /** 区域显示名：region_id=1 为全国哨兵值（dim_region 无该行），其余按区域表取名 */
    private String regionDisplayName(int regionId) {
        if (regionId == NATIONAL_REGION_ID) {
            return "全国";
        }
        DimRegion region = regionMapper.selectById(regionId);
        return region != null ? region.getRegionName() : "区域" + regionId;
    }

    @Override
    public Map<String, Object> runAnomalyDetection() {
        long start = System.currentTimeMillis();
        // 1. 全量检测点：区县×行业×直接能源×月（间接能源不参与异常检测）
        List<AnomalyPointVO> points = monthMapper.selectAnomalyPoints();
        if (points.isEmpty()) {
            throw new BizException("暂无核算数据");
        }
        // 2. 按 (industry, energy) 分组建模：同组内各行业的季节规律一致，
        //    区县规模差异由标准化消除
        Map<String, List<AnomalyPointVO>> seriesMap = new LinkedHashMap<>();
        for (AnomalyPointVO p : points) {
            String key = p.getIndustryId() + "-" + p.getEnergyId();
            seriesMap.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        // 3. 分批调用算法服务：全量数据点约 115 万条，一次性序列化会撑爆 JVM 堆，
        //    每批只物化本批 JSON，批间及时被 GC 回收
        List<JSONObject> allAnomalies = callAnomalyBatched(new ArrayList<>(seriesMap.entrySet()));

        // 4. 分批调用后各批 score 仅批内归一：用 raw 重做全局归一化（与单次调用口径一致）
        globalNormalize(allAnomalies);

        // 5. 重扫幂等：清除旧 AI 记录，按分数降序取 top N 入库
        recordMapper.deleteByDetectType(2);
        Map<Integer, String> energyNameMap = loadEnergyNameMap();
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

    /** 每批发送的序列数（约 48k 点/序列，8 序列/批 ≈ 38 万点，JSON 约 40MB，内存可控） */
    private static final int ANOMALY_SERIES_PER_BATCH = 8;

    /**
     * 分批调算法服务检测，合并各批异常点返回
     */
    private List<JSONObject> callAnomalyBatched(List<Map.Entry<String, List<AnomalyPointVO>>> seriesEntries) {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < seriesEntries.size(); i += ANOMALY_SERIES_PER_BATCH) {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (int j = i; j < Math.min(i + ANOMALY_SERIES_PER_BATCH, seriesEntries.size()); j++) {
                Map.Entry<String, List<AnomalyPointVO>> entry = seriesEntries.get(j);
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
                batch.add(series);
            }
            JSONArray data = algoClient.postArray("/api/alg/anomaly", Map.of("series", batch));
            if (data != null) {
                result.addAll(data.toList(JSONObject.class));
            }
        }
        return result;
    }

    /**
     * 分批检测后按 raw 重做全局 min-max 归一化（分数越接近 1 越异常），
     * 与算法服务单次调用时的全局归一化口径一致
     */
    private void globalNormalize(List<JSONObject> anomalies) {
        if (anomalies.isEmpty()) {
            return;
        }
        BigDecimal lo = null;
        BigDecimal hi = null;
        for (JSONObject a : anomalies) {
            BigDecimal raw = a.getBigDecimal("raw");
            if (raw == null) {
                continue;
            }
            lo = lo == null ? raw : lo.min(raw);
            hi = hi == null ? raw : hi.max(raw);
        }
        if (lo == null || hi == null) {
            return;
        }
        BigDecimal span = hi.subtract(lo).signum() > 0
                ? hi.subtract(lo) : new BigDecimal("0.000000001");
        for (JSONObject a : anomalies) {
            BigDecimal raw = a.getBigDecimal("raw");
            if (raw == null) {
                continue;
            }
            a.set("score", hi.subtract(raw).divide(span, 4, RoundingMode.HALF_UP));
            a.remove("raw");
        }
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
        return countSummary(null);
    }

    @Override
    public Map<String, Object> summaryByRegion(Integer regionId) {
        return countSummary(regionId);
    }

    /** 按区域口径统计：null/1=全国不限；省/市包含本级及下级区域 */
    private Map<String, Object> countSummary(Integer regionId) {
        List<Integer> regionIds = null;
        if (regionId != null && regionId > 1) {
            regionIds = new ArrayList<>();
            regionIds.add(regionId);
            for (DimRegion child : regionMapper.selectList(
                    new LambdaQueryWrapper<DimRegion>().eq(DimRegion::getParentId, regionId))) {
                regionIds.add(child.getId());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("pending", recordMapper.selectCount(regionWrapper(regionIds)
                .eq(AlertRecord::getStatus, 0)));
        result.put("ruleCount", recordMapper.selectCount(regionWrapper(regionIds)
                .eq(AlertRecord::getDetectType, 1)));
        result.put("aiCount", recordMapper.selectCount(regionWrapper(regionIds)
                .eq(AlertRecord::getDetectType, 2)));
        return result;
    }

    private LambdaQueryWrapper<AlertRecord> regionWrapper(List<Integer> regionIds) {
        return new LambdaQueryWrapper<AlertRecord>()
                .in(regionIds != null, AlertRecord::getRegionId, regionIds);
    }

    private boolean existsRecord(Integer ruleId, int regionId, int industryId, Integer year, Integer month) {
        return existsRecord(ruleId, regionId, industryId, year, month, null);
    }

    /** ruleName 参与去重：环比规则按「行业×能源」检测，同行业同月不同能源各自成条 */
    private boolean existsRecord(Integer ruleId, int regionId, int industryId, Integer year, Integer month,
                                 String ruleName) {
        return recordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(ruleId != null, AlertRecord::getRuleId, ruleId)
                .eq(AlertRecord::getRegionId, regionId)
                .eq(AlertRecord::getIndustryId, industryId)
                .eq(AlertRecord::getYear, year)
                .eq(month != null, AlertRecord::getMonth, month)
                .isNull(month == null, AlertRecord::getMonth)
                .eq(ruleName != null, AlertRecord::getRuleName, ruleName)) > 0;
    }
}
