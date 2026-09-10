package com.smart.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.client.AlgoClient;
import com.smart.common.BizException;
import com.smart.dto.SaveScenarioDTO;
import com.smart.dto.SimulateDTO;
import com.smart.entity.ScenarioRecord;
import com.smart.entity.SimResult;
import com.smart.mapper.FactEmissionMonthMapper;
import com.smart.mapper.FactEnergyMonthMapper;
import com.smart.mapper.ScenarioRecordMapper;
import com.smart.mapper.SimResultMapper;
import com.smart.service.AnalysisService;
import com.smart.service.SimulationService;
import com.smart.vo.DataStatusVO;
import com.smart.vo.MonthPointVO;
import com.smart.vo.PredictVO;
import com.smart.vo.ScenarioVO;
import com.smart.vo.SimResultVO;
import com.smart.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 情景仿真引擎（Java 调度层，核心计算在 Python 算法服务）
 * <p>
 * 预测链路：月度历史序列 → LSTM（含蒙特卡洛置信区间）→ 年度聚合 → 达峰识别
 * 仿真链路：最新年总排放为基准 → Kaya 式模型（能源结构×产业结构×技术效率）→ 未来轨迹
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationServiceImpl implements SimulationService {

    /** 省级区域ID */
    private static final int PROVINCE_REGION_ID = 1;

    private final AlgoClient algoClient;
    private final FactEnergyMonthMapper energyMonthMapper;
    private final FactEmissionMonthMapper emissionMonthMapper;
    private final ScenarioRecordMapper scenarioMapper;
    private final SimResultMapper simResultMapper;
    private final AnalysisService analysisService;

    @Override
    public PredictVO predict(int regionId) {
        DataStatusVO status = requireData();
        int startYear = status.getMinYear();
        int endYear = status.getMaxYear();

        // 1. 月度历史序列（t 为连续月份序号）
        List<MonthPointVO> months = regionId == PROVINCE_REGION_ID
                ? emissionMonthMapper.selectMonthlyRangeAll(startYear, endYear)
                : emissionMonthMapper.selectMonthlyRangeByRegion(regionId, startYear, endYear);
        if (months.size() < 12) {
            throw new BizException("历史数据不足一年，无法预测");
        }

        // 2. 调算法服务预测 120 个月（10 年）
        List<Map<String, Object>> history = months.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("t", m.getT());
            item.put("emission", m.getEmission());
            return item;
        }).collect(Collectors.toList());
        JSONObject data = algoClient.post("/api/alg/predict",
                JSONUtil.parseObj(JSONUtil.toJsonStr(Map.of("history", history, "future_steps", 72))));

        // 3. 月度结果聚合为年度
        PredictVO vo = new PredictVO();
        vo.setMethod(data.getStr("method"));
        // 历史年度（t → 年）
        Map<Integer, BigDecimal> histYearMap = new TreeMap<>();
        for (MonthPointVO m : months) {
            int year = startYear + (m.getT() - 1) / 12;
            histYearMap.merge(year, m.getEmission(), BigDecimal::add);
        }
        vo.setHistoryYears(new ArrayList<>(histYearMap.keySet()));
        vo.setHistoryValues(histYearMap.values().stream()
                .map(v -> v.setScale(2, RoundingMode.HALF_UP)).collect(Collectors.toList()));

        // 预测年度（每 12 个月求和）
        List<Integer> ts = data.getJSONArray("t").toList(Integer.class);
        List<BigDecimal> values = data.getJSONArray("values").toList(BigDecimal.class);
        List<BigDecimal> lowers = data.getJSONArray("lower").toList(BigDecimal.class);
        List<BigDecimal> uppers = data.getJSONArray("upper").toList(BigDecimal.class);
        Map<Integer, BigDecimal[]> forecastYearMap = new TreeMap<>();
        for (int i = 0; i < ts.size(); i++) {
            int year = startYear + (ts.get(i) - 1) / 12;
            BigDecimal[] acc = forecastYearMap.computeIfAbsent(year,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(values.get(i));
            acc[1] = acc[1].add(lowers.get(i));
            acc[2] = acc[2].add(uppers.get(i));
        }
        vo.setYears(new ArrayList<>());
        vo.setValues(new ArrayList<>());
        vo.setLower(new ArrayList<>());
        vo.setUpper(new ArrayList<>());
        forecastYearMap.forEach((year, acc) -> {
            vo.getYears().add(year);
            vo.getValues().add(acc[0].setScale(2, RoundingMode.HALF_UP));
            vo.getLower().add(acc[1].setScale(2, RoundingMode.HALF_UP));
            vo.getUpper().add(acc[2].setScale(2, RoundingMode.HALF_UP));
        });

        // 4. 达峰识别（预测段年度序列）
        detectPeak(vo.getYears(), vo.getValues(), vo);
        return vo;
    }

    @Override
    public SimResultVO simulate(SimulateDTO dto) {
        validateParams(dto);
        DataStatusVO status = requireData();
        int baseYear = status.getMaxYear();

        // 最新年总排放为基准
        List<TrendVO> rows = analysisService.trend(dto.getRegionId(), baseYear, baseYear, null);
        if (rows.isEmpty()) {
            throw new BizException("暂无核算数据，请先生成模拟数据并执行核算");
        }
        BigDecimal baseEmission = rows.get(0).getEmission();

        Map<String, Object> req = new HashMap<>();
        req.put("base_year", baseYear);
        req.put("base_emission", baseEmission.doubleValue());
        req.put("coal_ratio", dto.getCoalRatio());
        req.put("industry_ratio", dto.getIndustryRatio());
        req.put("tech_efficiency", dto.getTechEfficiency());
        req.put("gdp_growth", dto.getGdpGrowth());
        req.put("years", dto.getYears());
        req.put("mc_iters", dto.getMcIters());
        JSONObject data = algoClient.post("/api/alg/simulate", req);

        SimResultVO vo = new SimResultVO();
        vo.setBaseYear(baseYear);
        vo.setBaseEmission(baseEmission);
        vo.setYears(data.getJSONArray("years").toList(Integer.class));
        vo.setValues(data.getJSONArray("values").toList(BigDecimal.class));
        vo.setLower(data.getJSONArray("lower").toList(BigDecimal.class));
        vo.setUpper(data.getJSONArray("upper").toList(BigDecimal.class));
        detectPeak(vo.getYears(), vo.getValues(), vo);
        return vo;
    }

    @Override
    public ScenarioVO save(SaveScenarioDTO dto) {
        SimulateDTO sim = BeanUtil.copyProperties(dto, SimulateDTO.class);
        SimResultVO result = simulate(sim);

        ScenarioRecord record = new ScenarioRecord();
        record.setScenarioName(dto.getScenarioName());
        record.setRegionId(dto.getRegionId());
        record.setPresetType(dto.getPresetType());
        record.setCoalRatio(BigDecimal.valueOf(dto.getCoalRatio()));
        record.setIndustryRatio(BigDecimal.valueOf(dto.getIndustryRatio()));
        record.setTechEfficiency(BigDecimal.valueOf(dto.getTechEfficiency()));
        record.setPeakYear(result.getPeakYear());
        record.setPeakEmission(result.getPeakValue());
        record.setParamsJson(JSONUtil.toJsonStr(dto));
        scenarioMapper.insert(record);

        List<SimResult> rows = new ArrayList<>();
        for (int i = 0; i < result.getYears().size(); i++) {
            SimResult row = new SimResult();
            row.setScenarioId(record.getId());
            row.setYear(result.getYears().get(i));
            row.setEmission(result.getValues().get(i));
            row.setLowerBound(result.getLower().get(i));
            row.setUpperBound(result.getUpper().get(i));
            rows.add(row);
        }
        simResultMapper.insertBatch(rows);
        log.info("情景已保存：{}（id={}，达峰 {}）", dto.getScenarioName(), record.getId(), result.getPeakYear());
        return toVO(record, result);
    }

    @Override
    public List<ScenarioVO> list(int regionId) {
        List<ScenarioRecord> records = scenarioMapper.selectList(
                new LambdaQueryWrapper<ScenarioRecord>()
                        .eq(ScenarioRecord::getRegionId, regionId)
                        .orderByDesc(ScenarioRecord::getCreateTime));
        return records.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public ScenarioVO detail(long id) {
        ScenarioRecord record = scenarioMapper.selectById(id);
        if (record == null) {
            throw new BizException("情景不存在");
        }
        List<SimResult> rows = simResultMapper.selectList(
                new LambdaQueryWrapper<SimResult>()
                        .eq(SimResult::getScenarioId, id)
                        .orderByAsc(SimResult::getYear));
        ScenarioVO vo = toVO(record);
        vo.setYears(new ArrayList<>());
        vo.setValues(new ArrayList<>());
        vo.setLower(new ArrayList<>());
        vo.setUpper(new ArrayList<>());
        for (SimResult row : rows) {
            vo.getYears().add(row.getYear());
            vo.getValues().add(row.getEmission());
            vo.getLower().add(row.getLowerBound());
            vo.getUpper().add(row.getUpperBound());
        }
        return vo;
    }

    // ===== 内部方法 =====

    private DataStatusVO requireData() {
        DataStatusVO status = energyMonthMapper.selectStatus();
        if (status == null || status.getMinYear() == null) {
            throw new BizException("暂无活动数据，请先生成模拟数据");
        }
        return status;
    }

    private void validateParams(SimulateDTO dto) {
        if (dto.getCoalRatio() <= 0 || dto.getIndustryRatio() <= 0 || dto.getTechEfficiency() < 0) {
            throw new BizException("仿真参数不合法");
        }
        if (dto.getYears() < 1 || dto.getYears() > 50) {
            throw new BizException("仿真年数需在 1-50 之间");
        }
    }

    /** 达峰识别：年度序列峰值出现在非最后一年则视为达峰 */
    private void detectPeak(List<Integer> years, List<BigDecimal> values, Object target) {
        if (values.isEmpty()) {
            return;
        }
        int peakIdx = 0;
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i).compareTo(values.get(peakIdx)) > 0) {
                peakIdx = i;
            }
        }
        if (peakIdx < values.size() - 1) {
            BeanUtil.setProperty(target, "peakYear", years.get(peakIdx));
            BeanUtil.setProperty(target, "peakValue", values.get(peakIdx));
        }
    }

    private ScenarioVO toVO(ScenarioRecord record) {
        ScenarioVO vo = new ScenarioVO();
        vo.setId(record.getId());
        vo.setScenarioName(record.getScenarioName());
        vo.setPresetType(record.getPresetType());
        vo.setCoalRatio(record.getCoalRatio() == null ? 0 : record.getCoalRatio().doubleValue());
        vo.setIndustryRatio(record.getIndustryRatio() == null ? 0 : record.getIndustryRatio().doubleValue());
        vo.setTechEfficiency(record.getTechEfficiency() == null ? 0 : record.getTechEfficiency().doubleValue());
        vo.setPeakYear(record.getPeakYear());
        vo.setPeakEmission(record.getPeakEmission());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private ScenarioVO toVO(ScenarioRecord record, SimResultVO result) {
        ScenarioVO vo = toVO(record);
        vo.setYears(result.getYears());
        vo.setValues(result.getValues());
        vo.setLower(result.getLower());
        vo.setUpper(result.getUpper());
        return vo;
    }
}
