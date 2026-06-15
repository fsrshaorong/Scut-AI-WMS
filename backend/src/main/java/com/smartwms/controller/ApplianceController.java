/**
 * 器具包装参数控制器。
 *
 * @author Focus
 * @date 2026-06-15
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.entity.Appliance;
import com.smartwms.service.ApplianceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appliances")
public class ApplianceController {

    private final ApplianceService applianceService;

    public ApplianceController(ApplianceService applianceService) {
        this.applianceService = applianceService;
    }

    @GetMapping
    public Result<Page<Appliance>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(applianceService.page(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<Appliance> getById(@PathVariable Long id) {
        return Result.success(applianceService.getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody Appliance appliance) {
        applianceService.save(appliance);
        return Result.success("器具配置创建成功", null);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Appliance appliance) {
        appliance.setId(id);
        applianceService.update(appliance);
        return Result.success("器具配置更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        applianceService.delete(id);
        return Result.success("器具配置删除成功", null);
    }

    /**
     * 根据物料号和供应商代码查询器具包装参数（入库单自动填充包装容量用）。
     */
    @GetMapping("/lookup")
    public Result<Appliance> lookup(
            @RequestParam String materialCode,
            @RequestParam String supplierCode) {
        return Result.success(applianceService.findByMaterialAndSupplier(materialCode, supplierCode));
    }
}
