/**
 * 新建出库单请求 DTO。
 *
 * @author Focus
 * @date 2026-06-15
 */
package com.smartwms.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class OutboundOrderRequest {

    @NotEmpty(message = "出库明细不能为空")
    private List<OutboundDetailItem> details;

    public List<OutboundDetailItem> getDetails() { return details; }
    public void setDetails(List<OutboundDetailItem> details) { this.details = details; }

    /**
     * 出库单明细项。
     */
    public static class OutboundDetailItem {
        private String materialCode;
        private Integer packCapacity;
        private Integer planQty;

        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

        public Integer getPackCapacity() { return packCapacity; }
        public void setPackCapacity(Integer packCapacity) { this.packCapacity = packCapacity; }

        public Integer getPlanQty() { return planQty; }
        public void setPlanQty(Integer planQty) { this.planQty = planQty; }
    }
}
