#!/usr/bin/env python3
"""
智库WMS 库存准确性测试脚本
测试出入库+封存解封各种场景，每次操作后严格校验库存数量。
@author Focus
@date 2026-06-28
"""
import requests
import json
import sys
import time
import io
from datetime import datetime

# Fix Windows GBK encoding issue
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE = "http://localhost:8080"
SUP = "SUP_VWG_09"
PACK_TYPE = "铁箱"
PACK_CAP = 10

# 全局统计
passed = 0
failed = 0
errors = []
warnings = []


def login():
    resp = requests.post(f"{BASE}/api/auth/login",
                         json={"username": "admin", "password": "admin123"})
    data = resp.json()
    return data["data"]["token"]


TOKEN = login()
HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json"
}

TS = datetime.now().strftime("%H%M%S")
MAT_A = f"T_A_{TS}"
MAT_B = f"T_B_{TS}"
MAT_C = f"T_C_{TS}"


def check_eq(label, expected, actual):
    global passed, failed, errors
    if str(expected) == str(actual):
        print(f"  ✅ {label}: {expected} == {actual}")
        passed += 1
    else:
        print(f"  ❌ {label}: 期望={expected} 实际={actual}")
        failed += 1
        errors.append(f"[{label}] expected={expected} actual={actual}")


def check_ne(label, not_expected, actual):
    global passed, failed, errors
    if str(not_expected) != str(actual):
        print(f"  ✅ {label}: {actual} != {not_expected}")
        passed += 1
    else:
        print(f"  ❌ {label}: 不应等于 {not_expected}, 实际={actual}")
        failed += 1
        errors.append(f"[{label}] should_not={not_expected} actual={actual}")


def api(method, path, **kwargs):
    """统一 API 调用，自动处理 JSON 解析"""
    url = f"{BASE}{path}"
    kwargs.setdefault("headers", HEADERS)
    resp = requests.request(method, url, **kwargs)
    try:
        return resp, resp.json()
    except:
        return resp, {"raw": resp.text}


def get_stock(material_code):
    _, data = api("GET", f"/api/stock/report?materialCode={material_code}")
    items = data.get("data", [])
    if items and len(items) > 0:
        return items[0].get("stockQty", 0)
    return 0


def get_barcode_info(barcode):
    _, data = api("GET", f"/api/inbound/trace?barcode={barcode}")
    items = data.get("data", {}).get("items", [])
    if items:
        return items[0]
    return None


def find_barcode(material_code, status="在库", min_remaining=0):
    """查找符合条件的条码。对于已出库状态，不检查 remainingQty（已出库条码 remaining 为 0）。"""
    _, data = api("GET", f"/api/inbound/trace?materialCode={material_code}&page=1&size=100")
    items = data.get("data", {}).get("items", [])
    for item in items:
        if item.get("status") == status:
            if status == "已出库":
                return item["barcode"]  # 已出库条码 remainingQty 始终为 0
            if item.get("remainingQty", 0) > min_remaining:
                return item["barcode"]
    return None


def barcode_sum(material_code):
    """计算在库+冻结+待出库条码的 remainingQty 总和"""
    _, data = api("GET", f"/api/inbound/trace?materialCode={material_code}&page=1&size=100")
    items = data.get("data", {}).get("items", [])
    total = 0
    for item in items:
        if item.get("status") in ("在库", "FROZEN", "待出库"):
            total += item.get("remainingQty", 0)
    return total


def create_material(code, name, supplier):
    resp, data = api("POST", "/api/materials", json={
        "materialCode": code,
        "materialName": name,
        "supplierCode": supplier
    })
    return data.get("code") == 0


def create_appliance(mat_code, supplier, pack_type, pack_capacity):
    resp, data = api("POST", "/api/appliances", json={
        "materialCode": mat_code,
        "supplierCode": supplier,
        "packType": pack_type,
        "packCapacity": pack_capacity
    })
    return data.get("code") == 0


def create_inbound(mat_code, plan_qty, supplier=SUP):
    resp, data = api("POST", "/api/inbound/orders", json={
        "supplierCode": supplier,
        "details": [{"materialCode": mat_code, "planQty": plan_qty}]
    })
    return data.get("data", {}).get("id")


def confirm_inbound(order_id):
    resp, data = api("PUT", f"/api/inbound/orders/{order_id}/confirm", json={})
    return data.get("code") == 0


def get_inbound_detail(order_id):
    resp, data = api("GET", f"/api/inbound/orders/{order_id}")
    return data.get("data", {})


def create_outbound(mat_code, plan_qty):
    resp, data = api("POST", "/api/outbound/orders", json={
        "details": [{"materialCode": mat_code, "planQty": plan_qty}]
    })
    return data.get("data", {}).get("id")


def get_outbound_detail(order_id):
    resp, data = api("GET", f"/api/outbound/orders/{order_id}")
    return data.get("data", {})


def confirm_outbound(order_id, detail_id, actual_qty):
    resp, data = api("PUT", f"/api/outbound/orders/{order_id}/confirm", json={
        "details": [{"detailId": detail_id, "actualQty": actual_qty}]
    })
    return data.get("code") == 0, data.get("message", "")


def freeze_barcode(barcodes, freeze_type="QUALITY", reason="测试封存"):
    resp, data = api("POST", "/api/freeze/seal", json={
        "barcodes": barcodes if isinstance(barcodes, list) else [barcodes],
        "freezeType": freeze_type,
        "reason": reason
    })
    return data.get("code") == 0, data.get("message", "")


def unfreeze_barcode(barcode):
    resp, data = api("POST", f"/api/freeze/unseal?barcode={barcode}")
    return data.get("code") == 0, data.get("message", "")


def delete_inbound(order_id):
    resp, data = api("DELETE", f"/api/inbound/orders/{order_id}")
    return data.get("code") == 0


def delete_outbound(order_id):
    resp, data = api("DELETE", f"/api/outbound/orders/{order_id}")
    return data.get("code") == 0


def scan_outbound(barcode):
    resp, data = api("POST", "/api/outbound/scan/wms", json={"barcode": barcode})
    return data.get("code") == 0, data.get("message", "")


# =====================================================================
# MAIN TEST SCRIPT
# =====================================================================
print("=" * 60)
print(f" 智库WMS 库存准确性测试 — {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
print(f" 物料: {MAT_A}, {MAT_B}, {MAT_C}")
print(f" 供应商: {SUP}, 箱容: {PACK_CAP}, 箱型: {PACK_TYPE}")
print("=" * 60)

# ============================
# Phase 0: Setup
# ============================
print("\n--- Phase 0: 创建测试数据 ---")

for mat_code, mat_name in [(MAT_A, f"TestA_{TS}"), (MAT_B, f"TestB_{TS}"), (MAT_C, f"TestC_{TS}")]:
    ok = create_material(mat_code, mat_name, SUP)
    print(f"  物料 {mat_code}: {'✅' if ok else '❌'}")

for mat in [MAT_A, MAT_B, MAT_C]:
    ok = create_appliance(mat, SUP, PACK_TYPE, PACK_CAP)
    print(f"  器具 {mat}: {'✅' if ok else '❌'}")

print("  初始库存:", {m: get_stock(m) for m in [MAT_A, MAT_B, MAT_C]})

# ============================
# Phase 1: 入库测试
# ============================
print("\n" + "=" * 60)
print(" Phase 1: 基础入库测试")
print("=" * 60)

# 1.1: 整除入库 (50件 = 5整箱)
print("\n--- Test 1.1: 整除入库 (50件, 5整箱) ---")
oid = create_inbound(MAT_A, 50)
print(f"  入库单ID: {oid}")
detail = get_inbound_detail(oid)
bc_count = len(detail.get("barcodes", []))
check_eq("1.1-条码数量", 5, bc_count)
ok = confirm_inbound(oid)
print(f"  确认入库: {'✅' if ok else '❌'}")
check_eq("1.1-库存=50", 50, get_stock(MAT_A))

# 1.2: 不整除入库 (23件 = 2整箱 + 1零头箱3件)
print("\n--- Test 1.2: 不整除入库 (23件, 末箱3件) ---")
oid = create_inbound(MAT_B, 23)
detail = get_inbound_detail(oid)
barcodes = detail.get("barcodes", [])
bc_count = len(barcodes)
check_eq("1.2-条码数量", 3, bc_count)
last_bc_qty = barcodes[-1].get("remainingQty", 0) if barcodes else 0
check_eq("1.2-末箱零头件数", 3, last_bc_qty)
ok = confirm_inbound(oid)
check_eq("1.2-库存=23", 23, get_stock(MAT_B))

# 1.3: 单件入库
print("\n--- Test 1.3: 单件入库 (1件) ---")
oid = create_inbound(MAT_C, 1)
ok = confirm_inbound(oid)
check_eq("1.3-库存=1", 1, get_stock(MAT_C))

# 1.4: 追加入库
print("\n--- Test 1.4: 追加入库 (+20件) ---")
oid = create_inbound(MAT_C, 20)
ok = confirm_inbound(oid)
check_eq("1.4-库存=21", 21, get_stock(MAT_C))

# 1.5: 删除未完成入库单不影响库存
print("\n--- Test 1.5: 删除未完成入库单 ---")
stk_before = get_stock(MAT_C)
oid = create_inbound(MAT_C, 100)
ok = delete_inbound(oid)
stk_after = get_stock(MAT_C)
check_eq("1.5-删除后库存不变", stk_before, stk_after)

# ============================
# Phase 2: 出库测试
# ============================
print("\n" + "=" * 60)
print(" Phase 2: 基础出库测试")
print("=" * 60)
# 当前: A=50, B=23, C=21

# 2.1: 整箱出库
print("\n--- Test 2.1: 整箱出库 (20件=2整箱) ---")
oid = create_outbound(MAT_A, 20)
print(f"  出库单ID: {oid}")
stk_pick = get_stock(MAT_A)
check_eq("2.1-拣选后库存不变", 50, stk_pick)
out_detail = get_outbound_detail(oid)
det_id = out_detail.get("details", [{}])[0].get("id", 0)
ok, msg = confirm_outbound(oid, det_id, 20)
print(f"  确认出库: {'✅' if ok else '❌ ' + msg}")
check_eq("2.1-库存=30", 30, get_stock(MAT_A))

# 2.2: 部分箱出库 (需要从零头箱+拆整箱)
print("\n--- Test 2.2: 部分箱出库 (5件, 应优先取零头) ---")
oid = create_outbound(MAT_B, 5)
stk_pick = get_stock(MAT_B)
check_eq("2.2-拣选后库存不变", 23, stk_pick)
out_detail = get_outbound_detail(oid)
det_id = out_detail.get("details", [{}])[0].get("id", 0)
ok, msg = confirm_outbound(oid, det_id, 5)
print(f"  确认出库: {'✅' if ok else '❌ ' + msg}")
check_eq("2.2-库存=18", 18, get_stock(MAT_B))

# 2.3: 删除未完成出库单退回库存
print("\n--- Test 2.3: 删除出库单退回库存 ---")
stk_before = get_stock(MAT_C)
oid = create_outbound(MAT_C, 10)
ok = delete_outbound(oid)
stk_after = get_stock(MAT_C)
check_eq("2.3-退回后库存不变", stk_before, stk_after)

# 2.4: 部分确认出库
print("\n--- Test 2.4: 部分确认出库 (10件→确认6件) ---")
stk_before = get_stock(MAT_C)
oid = create_outbound(MAT_C, 10)
out_detail = get_outbound_detail(oid)
det_id = out_detail.get("details", [{}])[0].get("id", 0)
ok, msg = confirm_outbound(oid, det_id, 6)
print(f"  确认出库6件: {'✅' if ok else '❌ ' + msg}")
stk_after = get_stock(MAT_C)
check_eq("2.4-库存扣减6", stk_before - 6, stk_after)

# ============================
# Phase 3: 封存解封测试 (核心)
# ============================
print("\n" + "=" * 60)
print(" Phase 3: 封存解封测试")
print("=" * 60)
# 当前: A=30(3整箱), B=18, C=15(21-6)

# 3.1: 封存在库条码
print("\n--- Test 3.1: 封存在库条码 ---")
bc_to_freeze = find_barcode(MAT_A, "在库")
print(f"  封存条码: {bc_to_freeze[:50] if bc_to_freeze else 'NOT_FOUND'}...")
if bc_to_freeze:
    bc_info = get_barcode_info(bc_to_freeze)
    fz_qty = bc_info.get("remainingQty", 0) if bc_info else 0
    print(f"  冻结件数: {fz_qty}")

    stk_before = get_stock(MAT_A)
    ok, msg = freeze_barcode(bc_to_freeze)
    print(f"  封存: {'✅' if ok else '❌ ' + msg}")
    stk_after = get_stock(MAT_A)
    check_eq("3.1-库存不变", stk_before, stk_after)

    bc_info2 = get_barcode_info(bc_to_freeze)
    bc_status = bc_info2.get("status", "?") if bc_info2 else "?"
    check_eq("3.1-状态→FROZEN", "FROZEN", bc_status)

    # 3.2: 出库排除冻结条码
    print(f"\n--- Test 3.2: 出库排除FROZEN (总库存={stk_after}, 冻结={fz_qty}) ---")
    unfrozen = stk_after - fz_qty
    print(f"  可用库存(非冻结): {unfrozen}")

    # 先验证：尝试出库全部库存应被拒绝（因为有冻结条码）
    resp, data = api("POST", "/api/outbound/orders", json={
        "details": [{"materialCode": MAT_A, "planQty": stk_after}]
    })
    check_eq("3.2-超可用出库拒绝(3001)", 3001, data.get("code"))

    # 按可用库存出库，应仅选取非冻结条码
    oid = create_outbound(MAT_A, unfrozen)
    out_detail = get_outbound_detail(oid)
    picked_qty = sum(b.get("remainingQty", 0) for b in out_detail.get("barcodes", []))
    print(f"  出库{unfrozen}件, 实际拣选{picked_qty}件")
    check_eq("3.2-拣选排除冻结", unfrozen, picked_qty)

    det_id = out_detail.get("details", [{}])[0].get("id", 0)
    ok, _ = confirm_outbound(oid, det_id, picked_qty)
    stk_after_out = get_stock(MAT_A)
    check_eq("3.2-出库后库存=冻结数", fz_qty, stk_after_out)

    # 3.3: 解封
    print("\n--- Test 3.3: 解封 ---")
    ok, msg = unfreeze_barcode(bc_to_freeze)
    print(f"  解封: {'✅' if ok else '❌ ' + msg}")
    bc_info3 = get_barcode_info(bc_to_freeze)
    bc_status3 = bc_info3.get("status", "?") if bc_info3 else "?"
    check_eq("3.3-状态→在库", "在库", bc_status3)
    check_eq("3.3-库存不变", fz_qty, get_stock(MAT_A))

    # 3.4: 解封后可出库
    print("\n--- Test 3.4: 解封后出库 ---")
    oid = create_outbound(MAT_A, fz_qty)
    out_detail = get_outbound_detail(oid)
    det_id = out_detail.get("details", [{}])[0].get("id", 0)
    ok, _ = confirm_outbound(oid, det_id, fz_qty)
    check_eq("3.4-库存归零", 0, get_stock(MAT_A))

    # 3.5: 拒绝封存已出库
    print("\n--- Test 3.5: 拒绝封存已出库条码 ---")
    out_bc = find_barcode(MAT_A, "已出库")
    if out_bc:
        ok, msg = freeze_barcode(out_bc)
        check_eq("3.5-拒绝封存已出库", False, ok)
    else:
        print("  ⚠️ 未找到已出库条码, 跳过")
        warnings.append("[3.5] 无已出库条码可测试")

    # 3.6: 拒绝解封非封存条码
    print("\n--- Test 3.6: 拒绝解封在库条码 ---")
    in_stock_bc = find_barcode(MAT_B, "在库")
    if in_stock_bc:
        ok, msg = unfreeze_barcode(in_stock_bc)
        check_eq("3.6-拒绝解封非封存", False, ok)
    else:
        print("  ⚠️ 未找到在库条码, 跳过")
        warnings.append("[3.6] 无在库条码可测试")
else:
    print("  ❌ 未找到在库条码!")
    failed += 1

# ============================
# Phase 4: 复杂场景
# ============================
print("\n" + "=" * 60)
print(" Phase 4: 复杂场景组合")
print("=" * 60)
# 当前: A=0, B=18, C=15

# 4.1: 完整周期
print("\n--- Test 4.1: 完整周期 (入库→出库→封存→解封→出库) ---")
oid = create_inbound(MAT_A, 30)
ok = confirm_inbound(oid)
check_eq("4.1-入库30", 30, get_stock(MAT_A))

oid = create_outbound(MAT_A, 10)
out_detail = get_outbound_detail(oid)
det_id = out_detail.get("details", [{}])[0].get("id", 0)
ok, _ = confirm_outbound(oid, det_id, 10)
check_eq("4.1-出库10→20", 20, get_stock(MAT_A))

bc_fz2 = find_barcode(MAT_A, "在库")
if bc_fz2:
    fz_qty2 = get_barcode_info(bc_fz2).get("remainingQty", 0) if get_barcode_info(bc_fz2) else 0
    ok, _ = freeze_barcode(bc_fz2)
    check_eq("4.1-封存后库存不变", 20, get_stock(MAT_A))

    avail = 20 - fz_qty2
    print(f"  冻结={fz_qty2}, 可用={avail}")

    if avail > 0:
        oid = create_outbound(MAT_A, avail)
        out_detail = get_outbound_detail(oid)
        det_id = out_detail.get("details", [{}])[0].get("id", 0)
        ok, _ = confirm_outbound(oid, det_id, avail)
        check_eq(f"4.1-出库可用后={fz_qty2}", fz_qty2, get_stock(MAT_A))

    ok, _ = unfreeze_barcode(bc_fz2)
    bc_info = get_barcode_info(bc_fz2)
    bc_st = bc_info.get("status", "?") if bc_info else "?"
    check_eq("4.1-解封→在库", "在库", bc_st)

    oid = create_outbound(MAT_A, fz_qty2)
    out_detail = get_outbound_detail(oid)
    det_id = out_detail.get("details", [{}])[0].get("id", 0)
    ok, _ = confirm_outbound(oid, det_id, fz_qty2)
    check_eq("4.1-最终归零", 0, get_stock(MAT_A))
else:
    print("  ❌ 未找到在库条码!")
    failed += 1

# ============================
# Phase 5: 全局一致性
# ============================
print("\n" + "=" * 60)
print(" Phase 5: 全局库存一致性")
print("=" * 60)

# 5.1: 负数库存检查
print("\n--- 5.1: 负数库存检查 ---")
_, data = api("GET", "/api/stock/report")
all_stocks = data.get("data", [])
neg = [(item["materialCode"], item["stockQty"]) for item in all_stocks if item.get("stockQty", 0) < 0]
if neg:
    for code, qty in neg:
        print(f"  ❌ {code}: stockQty={qty}")
    failed += 1
    errors.append(f"[5.1] {len(neg)} 个负数库存")
else:
    print("  ✅ 所有物料库存 >= 0")
    passed += 1

# 5.2: 条码剩余 vs 库存
print("\n--- 5.2: 条码剩余合计 vs 库存 ---")
for mat in [MAT_A, MAT_B, MAT_C]:
    stk = get_stock(mat)
    bc_sum = barcode_sum(mat)
    diff = stk - bc_sum
    status = "✅" if diff == 0 else f"❌ 差{diff}"
    print(f"  {mat}: stock={stk}, barcodes={bc_sum} {status}")
    if diff == 0:
        passed += 1
    else:
        failed += 1
        errors.append(f"[{mat}] stock={stk} barcodes={bc_sum} diff={diff}")

# ============================
# Phase 6: ScanOutbound BUG
# ============================
print("\n" + "=" * 60)
print(" Phase 6: ScanOutbound 库存扣减 (BUG检查)")
print("=" * 60)

print("\n--- 6.1: ScanOutbound 库存扣减 ---")
oid = create_inbound(MAT_A, 10)
ok = confirm_inbound(oid)
stk_before = get_stock(MAT_A)
print(f"  入库后库存: {stk_before}")

oid = create_outbound(MAT_A, 10)
# 获取待出库条码
out_detail = get_outbound_detail(oid)
scan_bc = None
for bc in out_detail.get("barcodes", []):
    if bc.get("status") == "待出库":
        scan_bc = bc["barcode"]
        break

print(f"  扫码条码: {scan_bc[:50] if scan_bc else 'NOT_FOUND'}...")

if scan_bc:
    ok, msg = scan_outbound(scan_bc)
    print(f"  扫码出库: {'✅' if ok else '❌ ' + msg}")
    stk_after = get_stock(MAT_A)
    print(f"\n  ⚠️ 关键BUG检查:")
    print(f"  扫码前库存: {stk_before}")
    print(f"  扫码后库存: {stk_after}")
    if stk_before == stk_after:
        print(f"  ❌ BUG确认: scanOutbound 没有扣减库存! (多出={stk_before}件)")
        failed += 1
        errors.append(f"[BUG] scanOutbound不扣库存: before={stk_before} after={stk_after}")
    else:
        print(f"  ✅ scanOutbound正确扣减了库存")
        passed += 1
else:
    print("  ⚠️ 未找到待出库条码")
    warnings.append("[6.1] 无待出库条码")

# ============================
# Phase 7: 异常处理
# ============================
print("\n" + "=" * 60)
print(" Phase 7: 异常处理")
print("=" * 60)

# 7.1: 已确认出库不可删除 (需确保出库能全箱取走以产生待出库状态)
print("\n--- Test 7.1: 已完成出库不可删除 ---")
# MAT_B=18(1整箱10+1部分箱8), 出库10件正好取整箱→状态变为待出库→确认成功→状态变为已完成
stk_before = get_stock(MAT_B)
oid = create_outbound(MAT_B, 10)  # 取整箱10件, fully consumed → 待出库
out_detail = get_outbound_detail(oid)
det_id = out_detail.get("details", [{}])[0].get("id", 0)
ok, msg = confirm_outbound(oid, det_id, 10)
print(f"  确认出库10件: {'✅' if ok else '❌ ' + msg}")
stk_after = get_stock(MAT_B)
check_eq("7.1-库存扣减10", stk_before - 10, stk_after)

# 尝试删除已完成出库单
resp, data = api("DELETE", f"/api/outbound/orders/{oid}")
check_eq("7.1-拒绝删除已完成(400)", 400, data.get("code"))

# 7.2: 库存不足拒绝 (STOCK_INSUFFICIENT = 3001)
print("\n--- Test 7.2: 库存不足拒绝 ---")
resp, data = api("POST", "/api/outbound/orders", json={
    "details": [{"materialCode": MAT_C, "planQty": 99999}]
})
check_eq("7.2-库存不足(3001)", 3001, data.get("code"))

# 7.3: 重复确认入库
print("\n--- Test 7.3: 重复确认入库 ---")
oid = create_inbound(MAT_A, 5)
ok = confirm_inbound(oid)
resp, data = api("PUT", f"/api/inbound/orders/{oid}/confirm", json={})
check_eq("7.3-重复确认拒绝(400)", 400, data.get("code"))

# ============================
# Final Report
# ============================
print("\n" + "=" * 60)
print(" 最终测试报告")
print("=" * 60)
print(f"  ✅ 通过: {passed}")
print(f"  ❌ 失败: {failed}")

if warnings:
    print(f"\n ⚠️ 警告 ({len(warnings)}):")
    for w in warnings:
        print(f"   {w}")

if errors:
    print(f"\n ❌ 失败详情 ({len(errors)}):")
    for e in errors:
        print(f"   {e}")

print(f"\n 测试物料: {MAT_A}, {MAT_B}, {MAT_C}")
print("=" * 60)

sys.exit(0 if failed == 0 else 1)
