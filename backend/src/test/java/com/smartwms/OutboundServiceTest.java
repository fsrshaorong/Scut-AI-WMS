/**
 * 出库服务单元测试 — 验证三阶段 FIFO 拣选算法、整箱优先、拆箱、拆分二维码。
 * 每个测试方法独立：先入库造库存，再出库验证拣选逻辑。
 *
 * @author Focus
 * @date 2026-06-28
 */
package com.smartwms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.dto.*;
import com.smartwms.entity.*;
import com.smartwms.mapper.*;
import com.smartwms.service.InboundService;
import com.smartwms.service.OutboundService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
@Rollback
class OutboundServiceTest {

    @Autowired private InboundService inboundService;
    @Autowired private OutboundService outboundService;
    @Autowired private MaterialMapper materialMapper;
    @Autowired private ApplianceMapper applianceMapper;
    @Autowired private InventoryMapper inventoryMapper;
    @Autowired private BarcodeMapper barcodeMapper;
    @Autowired private OutboundDetailMapper outboundDetailMapper;
    @Autowired private OutboundHistoryMapper outboundHistoryMapper;
    @Autowired private InboundDetailMapper inboundDetailMapper;

    private static final String TEST_MATERIAL = "TEST_PART_OUT";
    private static final String TEST_SUPPLIER = "SUP_OUT";
    private static final int PACK_CAPACITY = 20;

    /**
     * 每个测试前确保物料、器具、库存记录都存在。
     * 库存初始化为 0，由各测试自行入库造数据。
     */
    @BeforeEach
    void setUp() {
        // 物料
        if (materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialCode, TEST_MATERIAL)) == null) {
            Material m = new Material();
            m.setMaterialCode(TEST_MATERIAL);
            m.setMaterialName("出库测试物料");
            m.setSupplierCode(TEST_SUPPLIER);
            materialMapper.insert(m);
        }
        // 器具
        if (applianceMapper.selectOne(new LambdaQueryWrapper<Appliance>()
                .eq(Appliance::getMaterialCode, TEST_MATERIAL)) == null) {
            Appliance a = new Appliance();
            a.setMaterialCode(TEST_MATERIAL);
            a.setSupplierCode(TEST_SUPPLIER);
            a.setPackType("塑料周转箱");
            a.setPackCapacity(PACK_CAPACITY);
            applianceMapper.insert(a);
        }
        // 库存记录（如不存在则创建，已存在则清零）
        Inventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, TEST_MATERIAL));
        if (inv == null) {
            inv = new Inventory();
            inv.setMaterialCode(TEST_MATERIAL);
            inv.setStockQty(0);
            inv.setMinStockDays(3);
            inv.setMaxStockDays(15);
            inv.setSafetyStock(0);
            inv.setLeadTimeDays(7);
            inventoryMapper.insert(inv);
        } else {
            inv.setStockQty(0);
            inventoryMapper.updateById(inv);
        }
    }

    /** 创建入库单并手动确认（模拟完整入库流程，使库存生效）。 */
    private void createAndConfirmInbound(int planQty) {
        InboundOrderRequest req = new InboundOrderRequest();
        req.setSupplierCode(TEST_SUPPLIER);
        InboundOrderRequest.InboundDetailItem item = new InboundOrderRequest.InboundDetailItem();
        item.setMaterialCode(TEST_MATERIAL);
        item.setPlanQty(planQty);
        req.setDetails(List.of(item));
        InboundOrder order = inboundService.create(req);

        // 手动确认入库（按计划数全量）
        ConfirmInboundRequest confirmReq = new ConfirmInboundRequest();
        ConfirmInboundRequest.ConfirmDetailItem confirmItem = new ConfirmInboundRequest.ConfirmDetailItem();
        confirmItem.setMaterialCode(TEST_MATERIAL);
        confirmItem.setActualQty(planQty);
        confirmReq.setDetails(List.of(confirmItem));
        inboundService.confirm(order.getId(), confirmReq);
    }

    // ==================== 阶段1：整箱优先 ====================

    @Test
    @Order(1)
    void shouldPickFullBoxesWhenExactMultiple() {
        // 入库 100 件 = 5 整箱
        createAndConfirmInbound(100);

        // 出库 60 件（恰为 3 整箱）
        OutboundOrderRequest outReq = new OutboundOrderRequest();
        OutboundOrderRequest.OutboundDetailItem outItem = new OutboundOrderRequest.OutboundDetailItem();
        outItem.setMaterialCode(TEST_MATERIAL);
        outItem.setPlanQty(60);
        outReq.setDetails(List.of(outItem));

        OutboundOrder order = outboundService.create(outReq);
        assertNotNull(order.getId());

        // 验证：3 个二维码标记为"待出库"，每个扣除 20
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getOutboundId, order.getId())
        );
        assertEquals(3, histories.size());
        for (OutboundHistory h : histories) {
            assertEquals(20, h.getDeductQty().intValue());
            assertEquals(TEST_MATERIAL, h.getMaterialCode());
        }

        // 验证库存扣减：100 - 60 = 40
        Inventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, TEST_MATERIAL));
        assertEquals(40, inv.getStockQty().intValue());

        // 验证明细 planQty = 60
        OutboundDetail detail = outboundDetailMapper.selectOne(
                new LambdaQueryWrapper<OutboundDetail>()
                        .eq(OutboundDetail::getOutboundId, order.getId())
        );
        assertEquals(60, detail.getPlanQty());
    }

    // ==================== 阶段2+4：整箱不足时拆箱 ====================

    @Test
    @Order(2)
    void shouldSplitBoxWhenPartialNeeded() {
        // 入库 100 件 = 5 整箱
        createAndConfirmInbound(100);

        // 出库 61 件 = 3 整箱（60）+ 拆 1 件（1）
        OutboundOrderRequest outReq = new OutboundOrderRequest();
        OutboundOrderRequest.OutboundDetailItem outItem = new OutboundOrderRequest.OutboundDetailItem();
        outItem.setMaterialCode(TEST_MATERIAL);
        outItem.setPlanQty(61);
        outReq.setDetails(List.of(outItem));

        OutboundOrder order = outboundService.create(outReq);

        // 验证流水：4 条（3 条整箱 + 1 条拆分）
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getOutboundId, order.getId())
                        .orderByAsc(OutboundHistory::getId)
        );
        assertTrue(histories.size() >= 4,
                "应至少有 4 条流水（3整箱 + 1拆分），实际: " + histories.size());

        // 统计扣减总量
        int totalDeduct = histories.stream()
                .mapToInt(h -> h.getDeductQty() != null ? h.getDeductQty() : 0)
                .sum();
        assertEquals(61, totalDeduct, "总扣减量应等于 61");

        // 验证拆分二维码（含 _S 后缀）
        boolean hasSplitBarcode = histories.stream()
                .anyMatch(h -> h.getBarcode() != null && h.getBarcode().contains("_S"));
        assertTrue(hasSplitBarcode, "应存在拆分二维码（_S 后缀）");

        // 验证库存：100 - 61 = 39
        Inventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, TEST_MATERIAL));
        assertEquals(39, inv.getStockQty().intValue());

        // 验证被拆分的原箱 remainingQty 减少
        // 5 整箱入库，3 箱全取出 → "待出库"，1 箱被拆 1 件 → remainingQty = 19（在库），1 箱未动 → 20（在库）
        List<Barcode> inStockBarcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getMaterialCode, TEST_MATERIAL)
                        .eq(Barcode::getStatus, "在库")
        );
        int inStockTotal = inStockBarcodes.stream()
                .mapToInt(bc -> bc.getRemainingQty() != null ? bc.getRemainingQty() : 0)
                .sum();
        assertEquals(39, inStockTotal, "在库 remainingQty 总和应为 39（20 + 19）");
    }

    // ==================== 阶段3：部分箱补充 ====================

    @Test
    @Order(3)
    void shouldPickPartialBoxWhenExists() {
        // 先入库 61 件 → 生成 4 箱：3×20 + 1×1（零头箱）
        createAndConfirmInbound(61);

        // 再入库 100 件（较晚批次 → FIFO 后出）
        // 等这批也入库后，库存 = 161 件
        createAndConfirmInbound(100);

        // 出库 61 件：应先取 4 箱（FIFO 最早的那批：3×20 + 1×1 = 61）
        OutboundOrderRequest outReq = new OutboundOrderRequest();
        OutboundOrderRequest.OutboundDetailItem outItem = new OutboundOrderRequest.OutboundDetailItem();
        outItem.setMaterialCode(TEST_MATERIAL);
        outItem.setPlanQty(61);
        outReq.setDetails(List.of(outItem));

        OutboundOrder order = outboundService.create(outReq);

        // 验证：应从最早批次（61 件入库那批）取走所有箱
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getOutboundId, order.getId())
        );
        int totalDeduct = histories.stream()
                .mapToInt(h -> h.getDeductQty() != null ? h.getDeductQty() : 0)
                .sum();
        assertEquals(61, totalDeduct);

        // 第一批的 4 个二维码应被全取：3 个整箱 → 待出库，1 个零头箱 → 待出库
        long pickedFullBoxes = histories.stream()
                .filter(h -> h.getDeductQty() != null && h.getDeductQty() == 20)
                .count();
        assertEquals(3, pickedFullBoxes, "应有 3 个整箱被全取");

        // 第一批的零头箱（1 件）也应被全取
        boolean hasPartialPick = histories.stream()
                .anyMatch(h -> h.getDeductQty() != null && h.getDeductQty() == 1);
        assertTrue(hasPartialPick, "应有 1 个零头箱被全取（deductQty=1）");

        // 库存：161 - 61 = 100（第二批完整保留）
        Inventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, TEST_MATERIAL));
        assertEquals(100, inv.getStockQty().intValue());
    }

    // ==================== 按 boxCount 出库（向后兼容） ====================

    @Test
    @Order(4)
    void shouldCreateOutboundWithBoxCount_BackwardCompatible() {
        createAndConfirmInbound(100);

        OutboundOrderRequest outReq = new OutboundOrderRequest();
        OutboundOrderRequest.OutboundDetailItem outItem = new OutboundOrderRequest.OutboundDetailItem();
        outItem.setMaterialCode(TEST_MATERIAL);
        outItem.setBoxCount(2);  // 旧模式：2 箱 = 40 件
        outReq.setDetails(List.of(outItem));

        OutboundOrder order = outboundService.create(outReq);

        OutboundDetail detail = outboundDetailMapper.selectOne(
                new LambdaQueryWrapper<OutboundDetail>()
                        .eq(OutboundDetail::getOutboundId, order.getId())
        );
        assertEquals(40, detail.getPlanQty());
    }

    // ==================== 出库单修改（update） ====================

    @Test
    @Order(5)
    void shouldRollbackAndRePickOnUpdate() {
        createAndConfirmInbound(100);

        // 先创建出库 40 件
        OutboundOrderRequest outReq = new OutboundOrderRequest();
        OutboundOrderRequest.OutboundDetailItem outItem = new OutboundOrderRequest.OutboundDetailItem();
        outItem.setMaterialCode(TEST_MATERIAL);
        outItem.setPlanQty(40);
        outReq.setDetails(List.of(outItem));
        OutboundOrder order = outboundService.create(outReq);

        // 修改为 61 件 → 应回退 40 件的拣选，重新拣 61 件
        OutboundOrderRequest updateReq = new OutboundOrderRequest();
        OutboundOrderRequest.OutboundDetailItem updateItem = new OutboundOrderRequest.OutboundDetailItem();
        updateItem.setMaterialCode(TEST_MATERIAL);
        updateItem.setPlanQty(61);
        updateReq.setDetails(List.of(updateItem));

        outboundService.update(order.getId(), updateReq);

        // 验证最终状态：库存 = 100 - 61 = 39
        Inventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, TEST_MATERIAL));
        assertEquals(39, inv.getStockQty().intValue());

        // 更新后的流水总量应为 61
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getOutboundId, order.getId())
        );
        int totalDeduct = histories.stream()
                .mapToInt(h -> h.getDeductQty() != null ? h.getDeductQty() : 0)
                .sum();
        assertEquals(61, totalDeduct);
    }

    // ==================== 删除出库单（回退拣选） ====================

    @Test
    @Order(6)
    void shouldRollbackInventoryOnDelete() {
        createAndConfirmInbound(100);

        OutboundOrderRequest outReq = new OutboundOrderRequest();
        OutboundOrderRequest.OutboundDetailItem outItem = new OutboundOrderRequest.OutboundDetailItem();
        outItem.setMaterialCode(TEST_MATERIAL);
        outItem.setPlanQty(35);
        outReq.setDetails(List.of(outItem));
        OutboundOrder order = outboundService.create(outReq);

        // 删除前库存：100 - 35 = 65
        Inventory invBefore = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, TEST_MATERIAL));
        assertEquals(65, invBefore.getStockQty().intValue());

        // 删除出库单
        outboundService.delete(order.getId());

        // 删除后库存应恢复：100
        Inventory invAfter = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, TEST_MATERIAL));
        assertEquals(100, invAfter.getStockQty().intValue());

        // 所有被拣选的二维码应恢复为"在库"
        List<Barcode> allBarcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getMaterialCode, TEST_MATERIAL)
        );
        for (Barcode bc : allBarcodes) {
            // 拆分二维码（_S后缀）应被删除
            if (bc.getBarcode() != null && bc.getBarcode().contains("_S")) {
                fail("拆分二维码应在回退时被删除: " + bc.getBarcode());
            }
            assertEquals("在库", bc.getStatus(), "所有二维码应恢复为在库");
        }
    }

    // ==================== 库存不足时应拒绝 ====================

    @Test
    @Order(7)
    void shouldRejectOutboundWhenInsufficientStock() {
        createAndConfirmInbound(20); // 仅 20 件

        OutboundOrderRequest outReq = new OutboundOrderRequest();
        OutboundOrderRequest.OutboundDetailItem outItem = new OutboundOrderRequest.OutboundDetailItem();
        outItem.setMaterialCode(TEST_MATERIAL);
        outItem.setPlanQty(21); // 需要 21 件
        outReq.setDetails(List.of(outItem));

        assertThrows(Exception.class, () -> outboundService.create(outReq));
    }
}
