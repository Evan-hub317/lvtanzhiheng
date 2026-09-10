package com.smart.service;

import com.smart.dto.SaveScenarioDTO;
import com.smart.dto.SimulateDTO;
import com.smart.vo.PredictVO;
import com.smart.vo.ScenarioVO;
import com.smart.vo.SimResultVO;

import java.util.List;

public interface SimulationService {

    /**
     * 排放预测：月度历史序列 → LSTM 预测 120 个月 → 聚合成年度
     * 含蒙特卡洛 95% 置信区间与达峰年份识别
     */
    PredictVO predict(int regionId);

    /**
     * 情景仿真：调整能源结构/产业结构/能效参数，重算未来排放轨迹
     */
    SimResultVO simulate(SimulateDTO dto);

    /**
     * 保存情景（后端重算轨迹落库）
     */
    ScenarioVO save(SaveScenarioDTO dto);

    /**
     * 情景列表（不含轨迹）
     */
    List<ScenarioVO> list(int regionId);

    /**
     * 情景详情（含轨迹）
     */
    ScenarioVO detail(long id);
}
