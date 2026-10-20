package com.smart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 大屏时段聚合数据（碳排放热力图：任意起止月口径）
 */
@Data
public class ScreenPeriodVO {

    /** 时段排放总量（tCO2，直接口径 energy_id ≤ 6） */
    private BigDecimal total;

    /** 碳强度（tCO2/万元GDP；GDP 为年值，按时段月数占比折算） */
    private BigDecimal intensity;

    /** 行业结构（直接口径） */
    private List<StructureVO> industries;

    /** 能源结构（直接排放 energy 1-6） */
    private List<EnergyStructureVO> direct;

    /** 能源结构（间接排放 energy 7-8，展示口径不计入总量） */
    private List<EnergyStructureVO> indirect;

    /** 区域时段总量排行（regionId=1 时为各省；供地图热力与 TOP10 同源） */
    private List<RegionRankVO> regions;
}
