-- 清理旧业务数据，为统一看板编码格式做准备
DELETE FROM outbound_histories;
DELETE FROM outbound_details;
DELETE FROM outbound_orders;
DELETE FROM inventory_freezes;
DELETE FROM barcodes;
DELETE FROM inbound_details;
DELETE FROM inbound_orders;
DELETE FROM ai_inventory_reports;
DELETE FROM demand_forecasts;
SELECT '数据已清理' AS status;
