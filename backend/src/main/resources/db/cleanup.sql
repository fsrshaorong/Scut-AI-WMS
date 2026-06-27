-- ===================================================================
-- 数据清理与诊断脚本
-- ===================================================================

-- 1. 检测 8 段格式条码（含订单号，已被 7 段格式取代）
SELECT '=== 8段格式条码（含订单号） ===' AS '';
SELECT id, barcode, status FROM barcodes
WHERE barcode LIKE 'WMS|%|%|%|%|%|%|%|%';

-- 2. 检测拆分条码（_S 后缀附加在末尾的旧格式）
SELECT '=== 旧格式拆分条码 ===' AS '';
SELECT id, barcode, status FROM barcodes
WHERE barcode LIKE '%_S%' AND barcode NOT LIKE '%|%_S%';

-- 3. 清理旧业务数据（谨慎使用！）
-- DELETE FROM outbound_histories;
-- DELETE FROM outbound_details;
-- DELETE FROM outbound_orders;
-- DELETE FROM inventory_freezes;
-- DELETE FROM barcodes;
-- DELETE FROM inbound_details;
-- DELETE FROM inbound_orders;
-- DELETE FROM ai_inventory_reports;
-- DELETE FROM demand_forecasts;
-- SELECT '数据已清理' AS status;
