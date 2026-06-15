/**
 * 出库单服务接口。
 *
 * @author Focus
 * @date 2026-06-15
 */
package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.dto.OutboundOrderRequest;
import com.smartwms.entity.OutboundOrder;

public interface OutboundService {

    OutboundOrder create(OutboundOrderRequest request);

    Page<OutboundOrder> page(int current, int size);

    void confirm(Long outboundId);
}
