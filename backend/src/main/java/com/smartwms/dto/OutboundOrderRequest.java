/**
 * 创建出库单请求（整箱出库，单箱容量由器具配置决定）。
 *
 * @author Focus
 * @date 2026-06-23
 */
package com.smartwms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OutboundOrderRequest {

    @NotEmpty(message = "出库明细不能为空")
    @Valid
    private List<OutboundDetailItem> details;

    public List<OutboundDetailItem> getDetails() { return details; }
    public void setDetails(List<OutboundDetailItem> details) { this.details = details; }

    /**
     * 出库单明细项（支持按箱或按件出库，优先整箱、FIFO 拆零）。
     * planQty 与 boxCount 二选一，planQty 优先级更高。
     */
    public static class OutboundDetailItem {
        @NotBlank(message = "物料号不能为空")
        private String materialCode;

        @Min(value = 1, message = "出库箱数必须大于 0")
        private Integer boxCount;

        /** 计划出库总件数，优先级高于 boxCount。系统自动按 FIFO 整箱优先→拆零拣选 */
        @Min(value = 1, message = "计划出库件数必须大于 0")
        private Integer planQty;

        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

        public Integer getBoxCount() { return boxCount; }
        public void setBoxCount(Integer boxCount) { this.boxCount = boxCount; }

        public Integer getPlanQty() { return planQty; }
        public void setPlanQty(Integer planQty) { this.planQty = planQty; }
    }
}
