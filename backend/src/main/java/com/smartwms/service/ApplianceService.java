/**
 * 器具包装参数服务接口。
 *
 * @author Focus
 * @date 2026-06-15
 */
package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.entity.Appliance;

public interface ApplianceService {

    Page<Appliance> page(int current, int size, String keyword);

    Appliance getById(Long id);

    void save(Appliance appliance);

    void update(Appliance appliance);

    void delete(Long id);

    /**
     * 根据物料号和供应商代码查询器具包装参数。
     */
    Appliance findByMaterialAndSupplier(String materialCode, String supplierCode);
}
