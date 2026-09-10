package com.smart.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.entity.FactEnergyMonth;
import com.smart.mapper.FactEnergyMonthMapper;
import com.smart.service.FactEnergyMonthService;
import org.springframework.stereotype.Service;

@Service
public class FactEnergyMonthServiceImpl extends ServiceImpl<FactEnergyMonthMapper, FactEnergyMonth>
        implements FactEnergyMonthService {
}
