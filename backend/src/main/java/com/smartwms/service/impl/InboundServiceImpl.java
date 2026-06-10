/**
 * 入库单服务实现（桩实现，后续完善）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.dto.ConfirmInboundRequest;
import com.smartwms.dto.InboundOrderRequest;
import com.smartwms.dto.InboundOrderVO;
import com.smartwms.entity.*;
import com.smartwms.mapper.*;
import com.smartwms.service.InboundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InboundServiceImpl implements InboundService {

    private final InboundOrderMapper inboundOrderMapper;
    private final InboundDetailMapper inboundDetailMapper;
    private final InventoryMapper inventoryMapper;
    private final BarcodeMapper barcodeMapper;

    public InboundServiceImpl(InboundOrderMapper inboundOrderMapper,
                               InboundDetailMapper inboundDetailMapper,
                               InventoryMapper inventoryMapper,
                               BarcodeMapper barcodeMapper) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.inventoryMapper = inventoryMapper;
        this.barcodeMapper = barcodeMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrder create(InboundOrderRequest request) {
        // 生成唯一入库单号 RK + 日期 + 序号
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String orderNo = "RK" + datePart + String.format("%03d", System.currentTimeMillis() % 1000);

        InboundOrder order = new InboundOrder();
        order.setOrderNo(orderNo);
        order.setStatus("未入库");
        order.setSupplierCode(request.getSupplierCode());
        inboundOrderMapper.insert(order);

        // 创建明细
        for (InboundOrderRequest.InboundDetailItem item : request.getDetails()) {
            InboundDetail detail = new InboundDetail();
            detail.setInboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(item.getMaterialCode());
            detail.setPackCapacity(item.getPackCapacity());
            detail.setPlanQty(item.getPlanQty());
            detail.setActualQty(0);
            inboundDetailMapper.insert(detail);

            // 自动生成条码
            int boxCount = (int) Math.ceil((double) item.getPlanQty() / item.getPackCapacity());
            for (int i = 0; i < boxCount; i++) {
                Barcode barcode = new Barcode();
                barcode.setMaterialCode(item.getMaterialCode());
                barcode.setSupplierCode(request.getSupplierCode());
                barcode.setBarcode(orderNo + "-" + item.getMaterialCode() + "-" + (i + 1));
                barcode.setStatus("待入库");
                barcodeMapper.insert(barcode);
            }
        }

        return order;
    }

    @Override
    public InboundOrderVO getById(Long id) {
        InboundOrder order = inboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
        }
        List<InboundDetail> details = inboundDetailMapper.selectList(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, id)
        );
        return InboundOrderVO.from(order, details);
    }

    @Override
    public Page<InboundOrder> page(int current, int size) {
        Page<InboundOrder> page = new Page<>(current, size);
        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(InboundOrder::getCreatedAt);
        return inboundOrderMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long inboundId, ConfirmInboundRequest request) {
        InboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
        }
        if ("已完成".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该入库单已完成核销");
        }

        // 查询该入库单的所有明细行
        var details = inboundDetailMapper.selectList(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, inboundId)
        );

        // 若前端传入了明细行实际数量，则按 materialCode 匹配更新；否则默认按计划数全量入库
        Map<String, Integer> actualQtyMap = null;
        if (request != null && request.getDetails() != null && !request.getDetails().isEmpty()) {
            actualQtyMap = request.getDetails().stream()
                    .collect(Collectors.toMap(
                            ConfirmInboundRequest.ConfirmDetailItem::getMaterialCode,
                            ConfirmInboundRequest.ConfirmDetailItem::getActualQty,
                            (a, b) -> a
                    ));
        }

        for (InboundDetail detail : details) {
            // 确定实际入库数：优先取前端传入值，否则取计划数
            int actualQty = (actualQtyMap != null && actualQtyMap.containsKey(detail.getMaterialCode()))
                    ? actualQtyMap.get(detail.getMaterialCode())
                    : detail.getPlanQty();
            detail.setActualQty(actualQty);
            inboundDetailMapper.updateById(detail);

            // 更新条码状态为 "在库"
            var barcodes = barcodeMapper.selectList(
                    new LambdaQueryWrapper<Barcode>()
                            .eq(Barcode::getMaterialCode, detail.getMaterialCode())
                            .eq(Barcode::getStatus, "待入库")
            );
            for (Barcode bc : barcodes) {
                bc.setStatus("在库");
                barcodeMapper.updateById(bc);
            }

            // 增加库存
            Inventory inv = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<Inventory>()
                            .eq(Inventory::getMaterialCode, detail.getMaterialCode())
            );
            if (inv != null) {
                int oldQty = inv.getStockQty();
                inv.setStockQty(oldQty + actualQty);
                inventoryMapper.updateById(inv);
            }
        }

        // 更新入库单状态
        order.setStatus("已完成");
        inboundOrderMapper.updateById(order);
    }
}
