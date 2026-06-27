/**
 * 入库服务单元测试 — 验证按件数入库（planQty）、自动算箱、末箱零头。
 *
 * @author Focus
 * @date 2026-06-28
 */
package com.smartwms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.dto.InboundOrderRequest;
import com.smartwms.entity.*;
import com.smartwms.mapper.*;
import com.smartwms.service.InboundService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
@Rollback
class InboundServiceTest {

    @Autowired private InboundService inboundService;
    @Autowired private InboundOrderMapper inboundOrderMapper;
    @Autowired private InboundDetailMapper inboundDetailMapper;
    @Autowired private BarcodeMapper barcodeMapper;
    @Autowired private MaterialMapper materialMapper;
    @Autowired private ApplianceMapper applianceMapper;

    private static final String TEST_MATERIAL = "TEST_PART_001";
    private static final String TEST_SUPPLIER = "SUP_TEST";
    private static final int PACK_CAPACITY = 20;

    /**
     * 每个测试前确保物料和器具配置存在。
     */
    @BeforeEach
    void setUp() {
        // 插入测试物料（如已存在则跳过）
        if (materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialCode, TEST_MATERIAL)) == null) {
            Material m = new Material();
            m.setMaterialCode(TEST_MATERIAL);
            m.setMaterialName("测试物料");
            m.setSupplierCode(TEST_SUPPLIER);
            materialMapper.insert(m);
        }
        // 插入测试器具配置（如已存在则跳过）
        if (applianceMapper.selectOne(new LambdaQueryWrapper<Appliance>()
                .eq(Appliance::getMaterialCode, TEST_MATERIAL)) == null) {
            Appliance a = new Appliance();
            a.setMaterialCode(TEST_MATERIAL);
            a.setSupplierCode(TEST_SUPPLIER);
            a.setPackType("塑料周转箱");
            a.setPackCapacity(PACK_CAPACITY);
            applianceMapper.insert(a);
        }
    }

    // ==================== 按 planQty 入库（件数模式） ====================

    @Test
    @Order(1)
    void shouldCreateInboundWithPlanQty_NonDivisible() {
        // 入库 61 件，每箱 20 件 → 应生成 4 箱（3整1零）
        InboundOrderRequest request = new InboundOrderRequest();
        request.setSupplierCode(TEST_SUPPLIER);

        InboundOrderRequest.InboundDetailItem item = new InboundOrderRequest.InboundDetailItem();
        item.setMaterialCode(TEST_MATERIAL);
        item.setPlanQty(61);  // 按件数模式
        request.setDetails(List.of(item));

        InboundOrder order = inboundService.create(request);
        assertNotNull(order.getId());
        assertEquals("未完成", order.getStatus());

        // 验证明细：planQty = 61
        InboundDetail detail = inboundDetailMapper.selectOne(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, order.getId())
        );
        assertNotNull(detail);
        assertEquals(61, detail.getPlanQty());
        assertEquals(PACK_CAPACITY, detail.getPackCapacity());

        // 验证二维码：4 个
        List<Barcode> barcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getInboundId, order.getId())
                        .orderByAsc(Barcode::getBarcode)
        );
        assertEquals(4, barcodes.size());

        // 前 3 箱为整箱（remainingQty = 20），第 4 箱为零头（remainingQty = 1）
        assertEquals(PACK_CAPACITY, barcodes.get(0).getRemainingQty());
        assertEquals(PACK_CAPACITY, barcodes.get(1).getRemainingQty());
        assertEquals(PACK_CAPACITY, barcodes.get(2).getRemainingQty());
        assertEquals(1, barcodes.get(3).getRemainingQty());

        // 所有二维码初始状态为"待入库"
        for (Barcode bc : barcodes) {
            assertEquals("待入库", bc.getStatus());
            assertEquals("inbound", bc.getType());
        }
    }

    @Test
    @Order(2)
    void shouldCreateInboundWithPlanQty_Divisible() {
        // 入库 60 件（恰好 3 整箱，planQty % packCapacity == 0）
        InboundOrderRequest request = new InboundOrderRequest();
        request.setSupplierCode(TEST_SUPPLIER);

        InboundOrderRequest.InboundDetailItem item = new InboundOrderRequest.InboundDetailItem();
        item.setMaterialCode(TEST_MATERIAL);
        item.setPlanQty(60);
        request.setDetails(List.of(item));

        InboundOrder order = inboundService.create(request);

        // 验证二维码：3 个，全是整箱
        List<Barcode> barcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getInboundId, order.getId())
                        .orderByAsc(Barcode::getBarcode)
        );
        assertEquals(3, barcodes.size());
        for (Barcode bc : barcodes) {
            assertEquals(PACK_CAPACITY, bc.getRemainingQty().intValue());
        }
    }

    @Test
    @Order(3)
    void shouldCreateInboundWithPlanQty_SingleItem() {
        // 入库 1 件 → 1 箱，remainingQty = 1
        InboundOrderRequest request = new InboundOrderRequest();
        request.setSupplierCode(TEST_SUPPLIER);

        InboundOrderRequest.InboundDetailItem item = new InboundOrderRequest.InboundDetailItem();
        item.setMaterialCode(TEST_MATERIAL);
        item.setPlanQty(1);
        request.setDetails(List.of(item));

        InboundOrder order = inboundService.create(request);

        List<Barcode> barcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getInboundId, order.getId())
        );
        assertEquals(1, barcodes.size());
        assertEquals(1, barcodes.get(0).getRemainingQty().intValue());
    }

    // ==================== 按 boxCount 入库（箱数模式，向后兼容） ====================

    @Test
    @Order(4)
    void shouldCreateInboundWithBoxCount_BackwardCompatible() {
        // 不传 planQty，只传 boxCount → 旧模式仍然工作
        InboundOrderRequest request = new InboundOrderRequest();
        request.setSupplierCode(TEST_SUPPLIER);

        InboundOrderRequest.InboundDetailItem item = new InboundOrderRequest.InboundDetailItem();
        item.setMaterialCode(TEST_MATERIAL);
        item.setBoxCount(3);  // 旧模式
        // 不设置 planQty
        request.setDetails(List.of(item));

        InboundOrder order = inboundService.create(request);

        List<Barcode> barcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getInboundId, order.getId())
        );
        assertEquals(3, barcodes.size());
        for (Barcode bc : barcodes) {
            assertEquals(PACK_CAPACITY, bc.getRemainingQty().intValue());
        }

        // planQty 应该等于 boxCount × packCapacity = 60
        InboundDetail detail = inboundDetailMapper.selectOne(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, order.getId())
        );
        assertEquals(60, detail.getPlanQty());
    }

    // ==================== planQty 优先于 boxCount ====================

    @Test
    @Order(5)
    void planQtyShouldTakePrecedenceOverBoxCount() {
        // 同时传 planQty 和 boxCount → planQty 生效
        InboundOrderRequest request = new InboundOrderRequest();
        request.setSupplierCode(TEST_SUPPLIER);

        InboundOrderRequest.InboundDetailItem item = new InboundOrderRequest.InboundDetailItem();
        item.setMaterialCode(TEST_MATERIAL);
        item.setBoxCount(100);  // 这个值应被忽略
        item.setPlanQty(35);    // 实际以这个为准
        request.setDetails(List.of(item));

        InboundOrder order = inboundService.create(request);

        // planQty=35, packCapacity=20 → ceil(35/20)=2 箱
        List<Barcode> barcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getInboundId, order.getId())
                        .orderByAsc(Barcode::getBarcode)
        );
        assertEquals(2, barcodes.size());
        assertEquals(20, barcodes.get(0).getRemainingQty().intValue()); // 整箱
        assertEquals(15, barcodes.get(1).getRemainingQty().intValue()); // 零头
    }

    // ==================== 修改入库单（update） ====================

    @Test
    @Order(6)
    void shouldUpdateInboundWithPlanQty() {
        // 先创建一个 3 箱的入库单
        InboundOrderRequest createReq = new InboundOrderRequest();
        createReq.setSupplierCode(TEST_SUPPLIER);
        InboundOrderRequest.InboundDetailItem createItem = new InboundOrderRequest.InboundDetailItem();
        createItem.setMaterialCode(TEST_MATERIAL);
        createItem.setBoxCount(3);
        createReq.setDetails(List.of(createItem));
        InboundOrder order = inboundService.create(createReq);

        // 修改为 61 件（从箱数模式改为件数模式）
        InboundOrderRequest updateReq = new InboundOrderRequest();
        updateReq.setSupplierCode(TEST_SUPPLIER);
        InboundOrderRequest.InboundDetailItem updateItem = new InboundOrderRequest.InboundDetailItem();
        updateItem.setMaterialCode(TEST_MATERIAL);
        updateItem.setPlanQty(61);
        updateReq.setDetails(List.of(updateItem));

        inboundService.update(order.getId(), updateReq);

        // 验证：4 箱，末箱 1 件
        List<Barcode> barcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getInboundId, order.getId())
                        .orderByAsc(Barcode::getBarcode)
        );
        assertEquals(4, barcodes.size());
        assertEquals(1, barcodes.get(3).getRemainingQty().intValue());
    }

    // ==================== 边界条件 ====================

    @Test
    @Order(7)
    void shouldRejectZeroPlanQty() {
        InboundOrderRequest request = new InboundOrderRequest();
        request.setSupplierCode(TEST_SUPPLIER);
        InboundOrderRequest.InboundDetailItem item = new InboundOrderRequest.InboundDetailItem();
        item.setMaterialCode(TEST_MATERIAL);
        item.setBoxCount(0);  // 箱数为 0
        item.setPlanQty(null); // planQty 也为 null
        request.setDetails(List.of(item));

        assertThrows(Exception.class, () -> inboundService.create(request));
    }
}
