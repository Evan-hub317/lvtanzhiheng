package com.smart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.entity.AlertRecord;
import com.smart.vo.AlertPageVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AlertRecordMapper extends BaseMapper<AlertRecord> {

    /** 预警分页（含区域/行业名称） */
    @Select("<script>" +
            "SELECT a.id, a.detect_type AS detectType, a.rule_name AS ruleName, " +
            "a.region_id AS regionId, r.region_name AS regionName, " +
            "a.industry_id AS industryId, i.industry_name AS industryName, " +
            "a.year, a.month, a.actual_value AS actualValue, a.threshold_value AS thresholdValue, " +
            "a.anomaly_score AS anomalyScore, a.status, a.handler, " +
            "a.handle_time AS handleTime, a.create_time AS createTime " +
            "FROM alert_record a " +
            "LEFT JOIN dim_region r ON r.id = a.region_id " +
            "LEFT JOIN dim_industry i ON i.id = a.industry_id " +
            "WHERE 1=1 " +
            "<if test='detectType != null'> AND a.detect_type = #{detectType}</if> " +
            "<if test='status != null'> AND a.status = #{status}</if> " +
            "<if test='regionId != null and regionId != 1'> " +
            "AND (a.region_id = #{regionId} OR a.region_id IN (SELECT id FROM dim_region WHERE parent_id = #{regionId}))" +
            "</if> " +
            "ORDER BY a.create_time DESC" +
            "</script>")
    IPage<AlertPageVO> selectAlertPage(Page<AlertPageVO> page,
                                       @Param("detectType") Integer detectType,
                                       @Param("status") Integer status,
                                       @Param("regionId") Integer regionId);

    /** 清除指定检测方式的预警（重扫幂等） */
    @Delete("DELETE FROM alert_record WHERE detect_type = #{detectType}")
    int deleteByDetectType(@Param("detectType") int detectType);
}
