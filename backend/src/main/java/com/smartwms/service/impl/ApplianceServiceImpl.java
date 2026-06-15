/**
 * 器具包装参数服务实现。
 *
 * @author Focus
 * @date 2026-06-15
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.entity.Appliance;
import com.smartwms.mapper.ApplianceMapper;
import com.smartwms.service.ApplianceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApplianceServiceImpl implements ApplianceService {

    private final ApplianceMapper applianceMapper;

    public ApplianceServiceImpl(ApplianceMapper applianceMapper) {
        this.applianceMapper = applianceMapper;
    }

    @Override
    public Page<Appliance> page(int current, int size, String keyword) {
        Page<Appliance> page = new Page<>(current, size);
        LambdaQueryWrapper<Appliance> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Appliance::getMaterialCode, keyword)
                    .or()
                    .like(Appliance::getPackType, keyword);
        }
        wrapper.orderByDesc(Appliance::getCreatedAt);
        return applianceMapper.selectPage(page, wrapper);
    }

    @Override
    public Appliance getById(Long id) {
        Appliance appliance = applianceMapper.selectById(id);
        if (appliance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "器具配置不存在");
        }
        return appliance;
    }

    @Override
    public void save(Appliance appliance) {
        validateRequiredFields(appliance);
        applianceMapper.insert(appliance);
    }

    @Override
    public void update(Appliance appliance) {
        Appliance existing = applianceMapper.selectById(appliance.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "器具配置不存在");
        }
        validateRequiredFields(appliance);
        applianceMapper.updateById(appliance);
    }

    @Override
    public void delete(Long id) {
        Appliance appliance = applianceMapper.selectById(id);
        if (appliance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "器具配置不存在");
        }
        applianceMapper.deleteById(id);
    }

    @Override
    public Appliance findByMaterialAndSupplier(String materialCode, String supplierCode) {
        return applianceMapper.selectOne(
                new LambdaQueryWrapper<Appliance>()
                        .eq(Appliance::getMaterialCode, materialCode)
                        .eq(Appliance::getSupplierCode, supplierCode)
                        .last("LIMIT 1")
        );
    }

    private void validateRequiredFields(Appliance appliance) {
        if (!StringUtils.hasText(appliance.getMaterialCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入物料号");
        }
        if (!StringUtils.hasText(appliance.getSupplierCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入供应商代码");
        }
        if (!StringUtils.hasText(appliance.getPackType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入包装器具型号");
        }
        if (appliance.getPackCapacity() == null || appliance.getPackCapacity() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "包装容量必须大于0");
        }
    }
}
