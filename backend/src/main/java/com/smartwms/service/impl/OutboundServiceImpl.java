/**
 * 出库单服务实现。
 *
 * @author Focus
 * @date 2026-06-15
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.dto.OutboundOrderRequest;
import com.smartwms.entity.*;
import com.smartwms.mapper.*;
import com.smartwms.service.OutboundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OutboundServiceImpl implements OutboundService {

    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundDetailMapper outboundDetailMapper;
    private final InventoryMapper inventoryMapper;
    private final BarcodeMapper barcodeMapper;

    public OutboundServiceImpl(OutboundOrderMapper outboundOrderMapper,
                                OutboundDetailMapper outboundDetailMapper,
                                InventoryMapper inventoryMapper,
                                BarcodeMapper barcodeMapper) {
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundDetailMapper = outboundDetailMapper;
        this.inventoryMapper = inventoryMapper;
        this.barcodeMapper = barcodeMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrder create(OutboundOrderRequest request) {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String orderNo = "CK" + datePart + String.format("%03d", System.currentTimeMillis() % 1000);

        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(orderNo);
        order.setStatus("未出库");
        outboundOrderMapper.insert(order);

        for (OutboundOrderRequest.OutboundDetailItem item : request.getDetails()) {
            OutboundDetail detail = new OutboundDetail();
            detail.setOutboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(item.getMaterialCode());
            detail.setPackCapacity(item.getPackCapacity());
            detail.setPlanQty(item.getPlanQty());
            detail.setActualQty(0);
            outboundDetailMapper.insert(detail);
        }

        return order;
    }

    @Override
    public Page<OutboundOrder> page(int current, int size) {
        Page<OutboundOrder> page = new Page<>(current, size);
        LambdaQueryWrapper<OutboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(OutboundOrder::getCreatedAt);
        return outboundOrderMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long outboundId) {
        OutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "出库单不存在");
        }
        if ("已完成".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该出库单已完成核销");
        }

        var details = outboundDetailMapper.selectList(
                new LambdaQueryWrapper<OutboundDetail>()
                        .eq(OutboundDetail::getOutboundId, outboundId)
        );

        for (OutboundDetail detail : details) {
            detail.setActualQty(detail.getPlanQty());
            outboundDetailMapper.updateById(detail);

            // 扣减库存
            Inventory inv = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<Inventory>()
                            .eq(Inventory::getMaterialCode, detail.getMaterialCode())
            );
            if (inv == null) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                        "物料 " + detail.getMaterialCode() + " 无库存记录");
            }
            int remaining = inv.getStockQty() - detail.getActualQty();
            if (remaining < 0) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                        "物料 " + detail.getMaterialCode() + " 库存不足，当前库存 " + inv.getStockQty() + " 件");
            }
            inv.setStockQty(remaining);
            inventoryMapper.updateById(inv);

            // 更新条码状态为"已出库"
            var barcodes = barcodeMapper.selectList(
                    new LambdaQueryWrapper<Barcode>()
                            .eq(Barcode::getMaterialCode, detail.getMaterialCode())
                            .eq(Barcode::getStatus, "在库")
            );
            int boxesToOut = (int) Math.ceil((double) detail.getActualQty() / detail.getPackCapacity());
            int outed = 0;
            for (Barcode bc : barcodes) {
                if (outed >= boxesToOut) break;
                bc.setStatus("已出库");
                barcodeMapper.updateById(bc);
                outed++;
            }
        }

        order.setStatus("已完成");
        outboundOrderMapper.updateById(order);
    }
}
