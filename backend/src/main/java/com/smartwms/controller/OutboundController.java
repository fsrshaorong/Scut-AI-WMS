/**
 * 出库单控制器。
 *
 * @author Focus
 * @date 2026-06-15
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.dto.OutboundOrderRequest;
import com.smartwms.entity.OutboundOrder;
import com.smartwms.service.OutboundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    private final OutboundService outboundService;

    public OutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    @GetMapping("/orders")
    public Result<Page<OutboundOrder>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(outboundService.page(page, size));
    }

    @PostMapping("/orders")
    public Result<OutboundOrder> create(@Valid @RequestBody OutboundOrderRequest request) {
        OutboundOrder order = outboundService.create(request);
        return Result.success("出库单创建成功", order);
    }

    @PutMapping("/orders/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        outboundService.confirm(id);
        return Result.success("出库确认成功", null);
    }
}
