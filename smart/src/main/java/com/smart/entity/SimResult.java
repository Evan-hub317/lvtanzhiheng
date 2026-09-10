package com.smart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 情景仿真逐年轨迹表 */
@Data
@TableName("sim_result")
public class SimResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scenarioId;

    private Integer year;

    /** 该年排放量（tCO2） */
    private BigDecimal emission;

    /** 蒙特卡洛95%置信下界 */
    private BigDecimal lowerBound;

    /** 蒙特卡洛95%置信上界 */
    private BigDecimal upperBound;

    private LocalDateTime createTime;
}
