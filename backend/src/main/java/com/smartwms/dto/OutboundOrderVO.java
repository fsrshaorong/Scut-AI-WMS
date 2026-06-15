package com.smartwms.dto;

import com.smartwms.entity.Barcode;
import com.smartwms.entity.OutboundDetail;
import com.smartwms.entity.OutboundOrder;

import java.util.List;

/**
 * 出库单详情视图对象。
 */
public class OutboundOrderVO {

    private Long id;
    private String orderNo;
    private String status;
    private String createdAt;
    private List<OutboundDetail> details;
    private List<Barcode> barcodes;
    private List<OutboundHistoryVO> histories;

    public static OutboundOrderVO from(OutboundOrder order,
                                       List<OutboundDetail> details,
                                       List<Barcode> barcodes,
                                       List<OutboundHistoryVO> histories) {
        OutboundOrderVO vo = new OutboundOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        vo.setDetails(details);
        vo.setBarcodes(barcodes);
        vo.setHistories(histories);
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public List<OutboundDetail> getDetails() { return details; }
    public void setDetails(List<OutboundDetail> details) { this.details = details; }
    public List<Barcode> getBarcodes() { return barcodes; }
    public void setBarcodes(List<Barcode> barcodes) { this.barcodes = barcodes; }
    public List<OutboundHistoryVO> getHistories() { return histories; }
    public void setHistories(List<OutboundHistoryVO> histories) { this.histories = histories; }
}
