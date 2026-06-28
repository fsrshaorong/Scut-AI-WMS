#!/bin/bash
# =============================================================================
# 智库WMS 库存准确性测试脚本 v2
# 测试出入库+封存解封各种场景，每次操作后严格验证库存数量
# @author Focus
# @date 2026-06-28
# =============================================================================

BASE="http://localhost:8080"
TOKEN="eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl0sImlhdCI6MTc4MjY1MDc3MSwiZXhwIjoxNzgyNjU3OTcxfQ.1sl7czg0ZJPLu15dnf00Qegr9txjft8m3gOmtuRpEvi_3F1n3TEeJh_xC436q4TD"
AUTH="Authorization: Bearer $TOKEN"
CT="Content-Type: application/json"

PASS=0
FAIL=0
ERRORS=""
WARNINGS=""

TS=$(date +%H%M%S)
MAT_A="T_A_${TS}"
MAT_B="T_B_${TS}"
MAT_C="T_C_${TS}"
SUP="SUP_VWG_09"
PACK_TYPE="铁箱"

# ---- 工具函数 ----
check_eq() {
    local label="$1" expected="$2" actual="$3"
    if [ "$expected" = "$actual" ]; then
        echo "  ✅ $label: $expected == $actual"
        PASS=$((PASS + 1))
    else
        echo "  ❌ $label: 期望=$expected 实际=$actual"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  [$label] expected=$expected actual=$actual"
    fi
}

check_contains() {
    local label="$1" pattern="$2" text="$3"
    if echo "$text" | grep -q "$pattern"; then
        echo "  ✅ $label: 包含 '$pattern'"
        PASS=$((PASS + 1))
    else
        echo "  ❌ $label: 不包含 '$pattern'"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  [$label] missing '$pattern'"
    fi
}

get_json_field() {
    local json="$1" field="$2"
    echo "$json" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$field',''))" 2>/dev/null
}

get_data_field() {
    local json="$1" field="$2"
    echo "$json" | python3 -c "import sys,json; d=json.load(sys.stdin); dd=d.get('data',{}); print(dd.get('$field','') if isinstance(dd,dict) else '')" 2>/dev/null
}

get_stock() {
    local mat="$1"
    local resp=$(curl -s "$BASE/api/stock/report?materialCode=$mat" -H "$AUTH")
    echo "$resp" | python3 -c "
import sys,json
d=json.load(sys.stdin)
data=d.get('data',[])
if data and len(data)>0:
    print(data[0].get('stockQty',0))
else:
    print('0')
" 2>/dev/null
}

get_barcode_status() {
    local bc="$1"
    local resp=$(curl -s "$BASE/api/inbound/trace?barcode=$bc" -H "$AUTH")
    echo "$resp" | python3 -c "
import sys,json
d=json.load(sys.stdin)
items=d.get('data',{}).get('items',[])
print(items[0]['status'] if items else 'NOT_FOUND')
" 2>/dev/null
}

get_barcode_remaining() {
    local bc="$1"
    local resp=$(curl -s "$BASE/api/inbound/trace?barcode=$bc" -H "$AUTH")
    echo "$resp" | python3 -c "
import sys,json
d=json.load(sys.stdin)
items=d.get('data',{}).get('items',[])
print(items[0]['remainingQty'] if items else '0')
" 2>/dev/null
}

echo "============================================================"
echo " 智库WMS 库存准确性测试 v2 — $(date '+%Y-%m-%d %H:%M:%S')"
echo " 测试物料: $MAT_A, $MAT_B, $MAT_C"
echo " 供应商: $SUP, 箱容量: 10, 箱类型: $PACK_TYPE"
echo "============================================================"

# ==========================================================================
# Phase 0: 准备测试基础数据
# ==========================================================================
echo ""
echo "--- Phase 0: 创建测试物料和器具 ---"

# 创建物料
for MAT in $MAT_A $MAT_B $MAT_C; do
    RESP=$(curl -s -X POST "$BASE/api/materials" -H "$AUTH" -H "$CT" \
        -d "{\"materialCode\":\"$MAT\",\"materialName\":\"Test_$MAT\",\"supplierCode\":\"$SUP\"}")
    MSG=$(get_json_field "$RESP" "message")
    echo "  物料 $MAT: $MSG"
done

# 创建器具（必须包含 packType）
for MAT in $MAT_A $MAT_B $MAT_C; do
    RESP=$(curl -s -X POST "$BASE/api/appliances" -H "$AUTH" -H "$CT" \
        -d "{\"materialCode\":\"$MAT\",\"supplierCode\":\"$SUP\",\"packType\":\"$PACK_TYPE\",\"packCapacity\":10}")
    MSG=$(get_json_field "$RESP" "message")
    echo "  器具 $MAT: $MSG"
done

# 验证初始库存为0
echo ""
echo "  初始库存:"
for MAT in $MAT_A $MAT_B $MAT_C; do
    STK=$(get_stock "$MAT")
    echo "    $MAT: $STK"
done

# ==========================================================================
# Phase 1: 基础入库测试
# ==========================================================================
echo ""
echo "============================================================"
echo " Phase 1: 基础入库测试"
echo "============================================================"

# --- Test 1.1: 整除入库 (50件, 箱容量10 → 5整箱) ---
echo ""
echo "--- Test 1.1: 整除入库 $MAT_A (planQty=50 ÷ 10 = 5箱) ---"
RESP=$(curl -s -X POST "$BASE/api/inbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"supplierCode\":\"$SUP\",\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":50}]}")
ORDER_ID_A1=$(get_data_field "$RESP" "id")
echo "  入库单ID: $ORDER_ID_A1"

# 获取详情，检查条码
DETAIL_A1=$(curl -s "$BASE/api/inbound/orders/$ORDER_ID_A1" -H "$AUTH")
BC_COUNT_A1=$(echo "$DETAIL_A1" | python3 -c "
import sys,json
d=json.load(sys.stdin)
bcs=d.get('data',{}).get('barcodes',[])
print(len(bcs))
" 2>/dev/null)
check_eq "1.1-条码数量" "5" "$BC_COUNT_A1"

# 确认入库
RESP=$(curl -s -X PUT "$BASE/api/inbound/orders/$ORDER_ID_A1/confirm" -H "$AUTH" -H "$CT" -d '{}')
MSG=$(get_json_field "$RESP" "message")
echo "  确认入库: $MSG"
STK_A=$(get_stock "$MAT_A")
check_eq "1.1-库存=50" "50" "$STK_A"

# 保存条码列表用于后续测试
BCS_A1=$(echo "$DETAIL_A1" | python3 -c "
import sys,json
d=json.load(sys.stdin)
for b in d.get('data',{}).get('barcodes',[]):
    print(b['barcode'])
" 2>/dev/null)
echo "  条码: $(echo "$BCS_A1" | tr '\n' ' ')"

# --- Test 1.2: 不整除入库 (23件, 箱容量10 → 2整箱+1零头3件) ---
echo ""
echo "--- Test 1.2: 不整除入库 $MAT_B (planQty=23, 2整箱+1零头) ---"
RESP=$(curl -s -X POST "$BASE/api/inbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"supplierCode\":\"$SUP\",\"details\":[{\"materialCode\":\"$MAT_B\",\"planQty\":23}]}")
ORDER_ID_B1=$(get_data_field "$RESP" "id")
DETAIL_B1=$(curl -s "$BASE/api/inbound/orders/$ORDER_ID_B1" -H "$AUTH")
BC_COUNT_B1=$(echo "$DETAIL_B1" | python3 -c "
import sys,json
d=json.load(sys.stdin)
bcs=d.get('data',{}).get('barcodes',[])
print(len(bcs))
" 2>/dev/null)
check_eq "1.2-条码数量" "3" "$BC_COUNT_B1"

# 检查末箱剩余件数
LAST_BC_QTY=$(echo "$DETAIL_B1" | python3 -c "
import sys,json
d=json.load(sys.stdin)
bcs=d.get('data',{}).get('barcodes',[])
print(bcs[-1]['remainingQty'] if bcs else '?')
" 2>/dev/null)
check_eq "1.2-末箱剩余件数" "3" "$LAST_BC_QTY"

RESP=$(curl -s -X PUT "$BASE/api/inbound/orders/$ORDER_ID_B1/confirm" -H "$AUTH" -H "$CT" -d '{}')
STK_B=$(get_stock "$MAT_B")
check_eq "1.2-库存=23" "23" "$STK_B"

# --- Test 1.3: 单件入库 ---
echo ""
echo "--- Test 1.3: 单件入库 $MAT_C (planQty=1) ---"
RESP=$(curl -s -X POST "$BASE/api/inbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"supplierCode\":\"$SUP\",\"details\":[{\"materialCode\":\"$MAT_C\",\"planQty\":1}]}")
ORDER_ID_C1=$(get_data_field "$RESP" "id")
curl -s -X PUT "$BASE/api/inbound/orders/$ORDER_ID_C1/confirm" -H "$AUTH" -H "$CT" -d '{}' > /dev/null
STK_C=$(get_stock "$MAT_C")
check_eq "1.3-库存=1" "1" "$STK_C"

# --- Test 1.4: 追加入库到已有库存 ---
echo ""
echo "--- Test 1.4: 追加入库 $MAT_C (+20件, 库存应=21) ---"
RESP=$(curl -s -X POST "$BASE/api/inbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"supplierCode\":\"$SUP\",\"details\":[{\"materialCode\":\"$MAT_C\",\"planQty\":20}]}")
ORDER_ID_C2=$(get_data_field "$RESP" "id")
curl -s -X PUT "$BASE/api/inbound/orders/$ORDER_ID_C2/confirm" -H "$AUTH" -H "$CT" -d '{}' > /dev/null
STK_C=$(get_stock "$MAT_C")
check_eq "1.4-库存=21" "21" "$STK_C"

# --- Test 1.5: 删除未完成入库单(不影响库存) ---
echo ""
echo "--- Test 1.5: 删除未完成入库单(库存不应变化) ---"
RESP=$(curl -s -X POST "$BASE/api/inbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"supplierCode\":\"$SUP\",\"details\":[{\"materialCode\":\"$MAT_C\",\"planQty\":100}]}")
ORDER_ID_C3=$(get_data_field "$RESP" "id")
STK_BEFORE=$(get_stock "$MAT_C")
curl -s -X DELETE "$BASE/api/inbound/orders/$ORDER_ID_C3" -H "$AUTH" > /dev/null
STK_AFTER=$(get_stock "$MAT_C")
check_eq "1.5-删除后库存不变" "$STK_BEFORE" "$STK_AFTER"

# ==========================================================================
# Phase 2: 基础出库测试
# ==========================================================================
echo ""
echo "============================================================"
echo " Phase 2: 基础出库测试"
echo "============================================================"
# 当前库存: MAT_A=50(5整箱), MAT_B=23(2整+1零), MAT_C=21(1+2整)

# --- Test 2.1: 整箱出库(不拆箱) ---
echo ""
echo "--- Test 2.1: 整箱出库 $MAT_A (20件=2整箱) ---"
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":20}]}")
OUT_ID_A1=$(get_data_field "$RESP" "id")
echo "  出库单ID: $OUT_ID_A1"

# 拣选后库存不变
STK_A_AFTER_PICK=$(get_stock "$MAT_A")
check_eq "2.1-拣选后库存不变" "50" "$STK_A_AFTER_PICK"

# 确认出库
OUT_DETAIL_A1=$(curl -s "$BASE/api/outbound/orders/$OUT_ID_A1" -H "$AUTH")
DETAIL_ID_A1=$(echo "$OUT_DETAIL_A1" | python3 -c "
import sys,json
d=json.load(sys.stdin)
dets=d.get('data',{}).get('details',[])
print(dets[0]['id'] if dets else '0')
" 2>/dev/null)
RESP=$(curl -s -X PUT "$BASE/api/outbound/orders/$OUT_ID_A1/confirm" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"detailId\":$DETAIL_ID_A1,\"actualQty\":20}]}")
MSG=$(get_json_field "$RESP" "message")
echo "  确认出库: $MSG"
STK_A=$(get_stock "$MAT_A")
check_eq "2.1-库存=30" "30" "$STK_A"

# --- Test 2.2: 部分箱优先出库(不需要拆箱) ---
echo ""
echo "--- Test 2.2: 部分箱出库 $MAT_B (5件, 应先取零头箱3件+拆整箱2件) ---"
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_B\",\"planQty\":5}]}")
OUT_ID_B1=$(get_data_field "$RESP" "id")

# 拣选后库存不变
STK_B_AFTER_PICK=$(get_stock "$MAT_B")
check_eq "2.2-拣选后库存不变" "23" "$STK_B_AFTER_PICK"

OUT_DETAIL_B1=$(curl -s "$BASE/api/outbound/orders/$OUT_ID_B1" -H "$AUTH")
DETAIL_ID_B1=$(echo "$OUT_DETAIL_B1" | python3 -c "
import sys,json
d=json.load(sys.stdin)
dets=d.get('data',{}).get('details',[])
print(dets[0]['id'] if dets else '0')
" 2>/dev/null)

# 检查拣选条码状态
echo "  拣选条码状态:"
echo "$OUT_DETAIL_B1" | python3 -c "
import sys,json
d=json.load(sys.stdin)
bcs=d.get('data',{}).get('barcodes',[])
for b in bcs:
    print(f\"    {b['barcode'][:50]}... status={b.get('status','?')} remaining={b.get('remainingQty','?')}\")
" 2>/dev/null

RESP=$(curl -s -X PUT "$BASE/api/outbound/orders/$OUT_ID_B1/confirm" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"detailId\":$DETAIL_ID_B1,\"actualQty\":5}]}")
MSG=$(get_json_field "$RESP" "message")
echo "  确认出库: $MSG"
STK_B=$(get_stock "$MAT_B")
check_eq "2.2-库存=18" "18" "$STK_B"

# --- Test 2.3: 删除未完成出库单(库存退回) ---
echo ""
echo "--- Test 2.3: 删除未完成出库单(拣选应退回) ---"
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_C\",\"planQty\":10}]}")
OUT_ID_C1=$(get_data_field "$RESP" "id")
STK_BEFORE_DEL=$(get_stock "$MAT_C")
echo "  删除前库存: $STK_BEFORE_DEL"

curl -s -X DELETE "$BASE/api/outbound/orders/$OUT_ID_C1" -H "$AUTH" > /dev/null
STK_AFTER_DEL=$(get_stock "$MAT_C")
check_eq "2.3-删除后退回库存" "$STK_BEFORE_DEL" "$STK_AFTER_DEL"

# --- Test 2.4: 部分确认出库 ---
echo ""
echo "--- Test 2.4: 部分确认出库(计划10件,确认6件) ---"
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_C\",\"planQty\":10}]}")
OUT_ID_C2=$(get_data_field "$RESP" "id")
OUT_DETAIL_C2=$(curl -s "$BASE/api/outbound/orders/$OUT_ID_C2" -H "$AUTH")
DETAIL_ID_C2=$(echo "$OUT_DETAIL_C2" | python3 -c "
import sys,json
d=json.load(sys.stdin)
dets=d.get('data',{}).get('details',[])
print(dets[0]['id'] if dets else '0')
" 2>/dev/null)

STK_BEFORE_PARTIAL=$(get_stock "$MAT_C")
RESP=$(curl -s -X PUT "$BASE/api/outbound/orders/$OUT_ID_C2/confirm" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"detailId\":$DETAIL_ID_C2,\"actualQty\":6}]}")
MSG=$(get_json_field "$RESP" "message")
echo "  确认出库6件: $MSG"
STK_AFTER_PARTIAL=$(get_stock "$MAT_C")
EXPECTED=$((STK_BEFORE_PARTIAL - 6))
check_eq "2.4-库存=$EXPECTED" "$EXPECTED" "$STK_AFTER_PARTIAL"

# ==========================================================================
# Phase 3: 封存解封测试 (核心)
# ==========================================================================
echo ""
echo "============================================================"
echo " Phase 3: 封存解封测试"
echo "============================================================"

# 当前 MAT_A=30(3整箱)
# 获取 MAT_A 在库条码
MAT_A_BC_LIST=$(curl -s "$BASE/api/inbound/trace?materialCode=$MAT_A&page=1&size=50" -H "$AUTH" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
for item in d.get('data',{}).get('items',[]):
    if item.get('status')=='在库' and item.get('remainingQty',0) > 0:
        print(item['barcode'])
" 2>/dev/null)
FIRST_BC_A=$(echo "$MAT_A_BC_LIST" | head -1)
echo "  选取封存条码: ${FIRST_BC_A:0:60}..."
FROZEN_QTY=$(get_barcode_remaining "$FIRST_BC_A")
echo "  该条码剩余件数: $FROZEN_QTY"

# --- Test 3.1: 封存在库条码 ---
echo ""
echo "--- Test 3.1: 封存在库条码 ---"
STK_BEFORE_FREEZE=$(get_stock "$MAT_A")
RESP=$(curl -s -X POST "$BASE/api/freeze/seal" -H "$AUTH" -H "$CT" \
    -d "{\"barcodes\":[\"$FIRST_BC_A\"],\"freezeType\":\"QUALITY\",\"reason\":\"测试封存\"}")
MSG=$(get_json_field "$RESP" "message")
echo "  封存: $MSG"
STK_AFTER_FREEZE=$(get_stock "$MAT_A")
check_eq "3.1-封存后库存不变" "$STK_BEFORE_FREEZE" "$STK_AFTER_FREEZE"

BC_STATUS=$(get_barcode_status "$FIRST_BC_A")
check_eq "3.1-条码→FROZEN" "FROZEN" "$BC_STATUS"

# --- Test 3.2: 封存后出库排除FROZEN条码 ---
echo ""
echo "--- Test 3.2: 封存后出库排除FROZEN ---"
UNFROZEN_STK=$((STK_AFTER_FREEZE - FROZEN_QTY))
echo "  MAT_A总库存=$STK_AFTER_FREEZE, 冻结=$FROZEN_QTY, 可用=$UNFROZEN_STK"

# 尝试出库全部库存 → 应只能取非冻结条码
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":$STK_AFTER_FREEZE}]}")
OUT_ID_A2=$(get_data_field "$RESP" "id")
OUT_DETAIL_A2=$(curl -s "$BASE/api/outbound/orders/$OUT_ID_A2" -H "$AUTH")

# 检查拣选量
PICKED_QTY=$(echo "$OUT_DETAIL_A2" | python3 -c "
import sys,json
d=json.load(sys.stdin)
bcs=d.get('data',{}).get('barcodes',[])
total=sum(b.get('remainingQty',0) for b in bcs)
print(total)
" 2>/dev/null)
echo "  实际拣选件数: $PICKED_QTY (期望=$UNFROZEN_STK)"
check_eq "3.2-拣选排除冻结" "$UNFROZEN_STK" "$PICKED_QTY"

# 确认出库
DETAIL_ID_A2=$(echo "$OUT_DETAIL_A2" | python3 -c "
import sys,json
d=json.load(sys.stdin)
dets=d.get('data',{}).get('details',[])
print(dets[0]['id'] if dets else '0')
" 2>/dev/null)
curl -s -X PUT "$BASE/api/outbound/orders/$OUT_ID_A2/confirm" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"detailId\":$DETAIL_ID_A2,\"actualQty\":$PICKED_QTY}]}" > /dev/null
STK_A_AFTER_OUT=$(get_stock "$MAT_A")
check_eq "3.2-出库后库存=冻结件数" "$FROZEN_QTY" "$STK_A_AFTER_OUT"

# --- Test 3.3: 解封 ---
echo ""
echo "--- Test 3.3: 解封 ---"
RESP=$(curl -s -X POST "$BASE/api/freeze/unseal?barcode=$FIRST_BC_A" -H "$AUTH" -H "$CT")
MSG=$(get_json_field "$RESP" "message")
echo "  解封: $MSG"
BC_STATUS=$(get_barcode_status "$FIRST_BC_A")
check_eq "3.3-状态→在库" "在库" "$BC_STATUS"
STK_A=$(get_stock "$MAT_A")
check_eq "3.3-库存不变" "$FROZEN_QTY" "$STK_A"

# --- Test 3.4: 解封后可正常出库 ---
echo ""
echo "--- Test 3.4: 解封后出库 ---"
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":$FROZEN_QTY}]}")
OUT_ID_A3=$(get_data_field "$RESP" "id")
OUT_DETAIL_A3=$(curl -s "$BASE/api/outbound/orders/$OUT_ID_A3" -H "$AUTH")
DETAIL_ID_A3=$(echo "$OUT_DETAIL_A3" | python3 -c "
import sys,json
d=json.load(sys.stdin)
dets=d.get('data',{}).get('details',[])
print(dets[0]['id'] if dets else '0')
" 2>/dev/null)
curl -s -X PUT "$BASE/api/outbound/orders/$OUT_ID_A3/confirm" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"detailId\":$DETAIL_ID_A3,\"actualQty\":$FROZEN_QTY}]}" > /dev/null
STK_A=$(get_stock "$MAT_A")
check_eq "3.4-库存归零" "0" "$STK_A"

# --- Test 3.5: 拒绝封存已出库条码 ---
echo ""
echo "--- Test 3.5: 拒绝封存已出库 ---"
# 找一个已出库的条码
OUT_BC=$(curl -s "$BASE/api/freeze/list?page=1&size=50" -H "$AUTH" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
# 从封存列表获取
records=d.get('data',{}).get('records',[])
print('has_records:', len(records))
" 2>/dev/null)

# 用 MAT_A 的已出库条码
OUT_BC=$(curl -s "$BASE/api/inbound/trace?materialCode=$MAT_A&page=1&size=50" -H "$AUTH" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
for item in d.get('data',{}).get('items',[]):
    if item.get('status')=='已出库':
        print(item['barcode'])
        break
" 2>/dev/null)
if [ -n "$OUT_BC" ] && [ "$OUT_BC" != "NOT_FOUND" ]; then
    RESP=$(curl -s -X POST "$BASE/api/freeze/seal" -H "$AUTH" -H "$CT" \
        -d "{\"barcodes\":[\"$OUT_BC\"],\"freezeType\":\"QUALITY\",\"reason\":\"测试\"}")
    CODE=$(get_json_field "$RESP" "code")
    check_eq "3.5-拒绝封存已出库(400)" "400" "$CODE"
else
    echo "  ⚠️ 未找到已出库条码, 跳过"
    WARNINGS="$WARNINGS\n  [3.5] 无已出库条码可测试"
fi

# --- Test 3.6: 拒绝解封非封存条码 ---
echo ""
echo "--- Test 3.6: 拒绝解封在库条码 ---"
# 用一个在库条码(比如MAT_B的)
IN_STOCK_BC=$(curl -s "$BASE/api/inbound/trace?materialCode=$MAT_B&page=1&size=50" -H "$AUTH" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
for item in d.get('data',{}).get('items',[]):
    if item.get('status')=='在库':
        print(item['barcode'])
        break
" 2>/dev/null)
if [ -n "$IN_STOCK_BC" ]; then
    RESP=$(curl -s -X POST "$BASE/api/freeze/unseal?barcode=$IN_STOCK_BC" -H "$AUTH" -H "$CT")
    CODE=$(get_json_field "$RESP" "code")
    check_eq "3.6-拒绝解封非封存(400)" "400" "$CODE"
else
    echo "  ⚠️ 未找到在库条码, 跳过"
fi

# ==========================================================================
# Phase 4: 复杂场景组合
# ==========================================================================
echo ""
echo "============================================================"
echo " Phase 4: 复杂场景组合测试"
echo "============================================================"

# 当前: MAT_A=0, MAT_B=18, MAT_C=15(21-6)

# --- Test 4.1: 完整周期: 入库→出库→封存→解封→出库 ---
echo ""
echo "--- Test 4.1: 完整周期 $MAT_A ---"
# 入库30件
RESP=$(curl -s -X POST "$BASE/api/inbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"supplierCode\":\"$SUP\",\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":30}]}")
ORD_A4=$(get_data_field "$RESP" "id")
curl -s -X PUT "$BASE/api/inbound/orders/$ORD_A4/confirm" -H "$AUTH" -H "$CT" -d '{}' > /dev/null
STK_A=$(get_stock "$MAT_A")
check_eq "4.1-入库30" "30" "$STK_A"

# 出库10件
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":10}]}")
OUT_A4=$(get_data_field "$RESP" "id")
OUT_DETAIL_A4=$(curl -s "$BASE/api/outbound/orders/$OUT_A4" -H "$AUTH")
DET_A4=$(echo "$OUT_DETAIL_A4" | python3 -c "import sys,json; d=json.load(sys.stdin); dets=d.get('data',{}).get('details',[]); print(dets[0]['id'] if dets else '0')" 2>/dev/null)
curl -s -X PUT "$BASE/api/outbound/orders/$OUT_A4/confirm" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"detailId\":$DET_A4,\"actualQty\":10}]}" > /dev/null
STK_A=$(get_stock "$MAT_A")
check_eq "4.1-出库10→20" "20" "$STK_A"

# 封存一个条码
BC_TO_FZ=$(curl -s "$BASE/api/inbound/trace?materialCode=$MAT_A&page=1&size=50" -H "$AUTH" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
for item in d.get('data',{}).get('items',[]):
    if item.get('status')=='在库' and item.get('remainingQty',0) > 0:
        print(item['barcode'])
        break
" 2>/dev/null)
FZ_QTY=$(get_barcode_remaining "$BC_TO_FZ")
curl -s -X POST "$BASE/api/freeze/seal" -H "$AUTH" -H "$CT" \
    -d "{\"barcodes\":[\"$BC_TO_FZ\"],\"freezeType\":\"DAMAGE\",\"reason\":\"测试\"}" > /dev/null
STK_A=$(get_stock "$MAT_A")
check_eq "4.1-封存后库存不变" "20" "$STK_A"

# 确认可用库存 = 20 - 冻结件数
AVAIL_A=$((20 - FZ_QTY))
echo "  冻结件数=$FZ_QTY, 可用=$AVAIL_A"

# 出库可用库存
if [ "$AVAIL_A" -gt 0 ]; then
    RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
        -d "{\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":$AVAIL_A}]}")
    OUT_A5=$(get_data_field "$RESP" "id")
    OUT_DETAIL_A5=$(curl -s "$BASE/api/outbound/orders/$OUT_A5" -H "$AUTH")
    DET_A5=$(echo "$OUT_DETAIL_A5" | python3 -c "import sys,json; d=json.load(sys.stdin); dets=d.get('data',{}).get('details',[]); print(dets[0]['id'] if dets else '0')" 2>/dev/null)
    curl -s -X PUT "$BASE/api/outbound/orders/$OUT_A5/confirm" -H "$AUTH" -H "$CT" \
        -d "{\"details\":[{\"detailId\":$DET_A5,\"actualQty\":$AVAIL_A}]}" > /dev/null
    STK_A=$(get_stock "$MAT_A")
    check_eq "4.1-出库可用后=$FZ_QTY" "$FZ_QTY" "$STK_A"
fi

# 解封
curl -s -X POST "$BASE/api/freeze/unseal?barcode=$BC_TO_FZ" -H "$AUTH" -H "$CT" > /dev/null
BC_STATUS=$(get_barcode_status "$BC_TO_FZ")
check_eq "4.1-解封→在库" "在库" "$BC_STATUS"

# 最后出库
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":$FZ_QTY}]}")
OUT_A6=$(get_data_field "$RESP" "id")
OUT_DETAIL_A6=$(curl -s "$BASE/api/outbound/orders/$OUT_A6" -H "$AUTH")
DET_A6=$(echo "$OUT_DETAIL_A6" | python3 -c "import sys,json; d=json.load(sys.stdin); dets=d.get('data',{}).get('details',[]); print(dets[0]['id'] if dets else '0')" 2>/dev/null)
curl -s -X PUT "$BASE/api/outbound/orders/$OUT_A6/confirm" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"detailId\":$DET_A6,\"actualQty\":$FZ_QTY}]}" > /dev/null
STK_A=$(get_stock "$MAT_A")
check_eq "4.1-最终归零" "0" "$STK_A"

# ==========================================================================
# Phase 5: 库存条码一致性校验
# ==========================================================================
echo ""
echo "============================================================"
echo " Phase 5: 全局库存一致性校验"
echo "============================================================"

# 检查所有库存 >= 0
echo ""
echo "--- 5.1: 所有物料库存 >= 0 ---"
ALL_STOCK=$(curl -s "$BASE/api/stock/report" -H "$AUTH")
NEG_COUNT=$(echo "$ALL_STOCK" | python3 -c "
import sys,json
d=json.load(sys.stdin)
neg=[item for item in d.get('data',[]) if item.get('stockQty',0) < 0]
print(len(neg))
" 2>/dev/null)
if [ "$NEG_COUNT" = "0" ]; then
    echo "  ✅ 所有物料库存 >= 0"
    PASS=$((PASS + 1))
else
    echo "  ❌ 存在 $NEG_COUNT 个负数库存!"
    echo "$ALL_STOCK" | python3 -c "
import sys,json
d=json.load(sys.stdin)
for item in d.get('data',[]):
    if item.get('stockQty',0) < 0:
        print(f\"    {item['materialCode']}: stockQty={item['stockQty']}\")
" 2>/dev/null
    FAIL=$((FAIL + 1))
    ERRORS="$ERRORS\n  [5.1] $NEG_COUNT 个负数库存"
fi

# 对测试物料检查 barcode剩余量 总和 vs 库存
echo ""
echo "--- 5.2: 条码剩余合计 vs 库存报表 ---"
for MAT in $MAT_A $MAT_B $MAT_C; do
    STK=$(get_stock "$MAT")
    BC_SUM=$(curl -s "$BASE/api/inbound/trace?materialCode=$MAT&page=1&size=100" -H "$AUTH" \
        | python3 -c "
import sys,json
d=json.load(sys.stdin)
total=sum(item.get('remainingQty',0) for item in d.get('data',{}).get('items',[]) if item.get('status') in ('在库','FROZEN','待出库'))
print(total)
" 2>/dev/null)
    echo "  $MAT: stockQty=$STK, 条码剩余合计=$BC_SUM"
    if [ "$STK" = "$BC_SUM" ]; then
        echo "    ✅ 一致"
        PASS=$((PASS + 1))
    else
        echo "    ❌ 不一致! 差额=$((STK - BC_SUM))"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  [$MAT] stock=$STK barcodes=$BC_SUM diff=$((STK - BC_SUM))"
    fi
done

# ==========================================================================
# Phase 6: ScanOutbound 库存扣减BUG检查
# ==========================================================================
echo ""
echo "============================================================"
echo " Phase 6: ScanOutbound 库存扣减检查"
echo "============================================================"

echo ""
echo "--- 6.1: 准备和测试 ---"
# 入库 MAT_A 10件
RESP=$(curl -s -X POST "$BASE/api/inbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"supplierCode\":\"$SUP\",\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":10}]}")
ORD_S1=$(get_data_field "$RESP" "id")
curl -s -X PUT "$BASE/api/inbound/orders/$ORD_S1/confirm" -H "$AUTH" -H "$CT" -d '{}' > /dev/null
STK_BEFORE_SCAN=$(get_stock "$MAT_A")
echo "  入库后库存: $STK_BEFORE_SCAN"

# 创建出库单(拣选)
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_A\",\"planQty\":10}]}")
OUT_S1=$(get_data_field "$RESP" "id")

# 获取待出库条码
SCAN_BC=$(curl -s "$BASE/api/outbound/orders/$OUT_S1" -H "$AUTH" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
bcs=d.get('data',{}).get('barcodes',[])
for b in bcs:
    if b.get('status')=='待出库':
        print(b['barcode'])
        break
" 2>/dev/null)
echo "  扫码出库条码: ${SCAN_BC:0:60}..."

# ScanOutbound 出库
RESP=$(curl -s -X POST "$BASE/api/outbound/scan/wms" -H "$AUTH" -H "$CT" \
    -d "{\"barcode\":\"$SCAN_BC\"}")
MSG=$(get_json_field "$RESP" "message")
echo "  扫码出库: $MSG"
STK_AFTER_SCAN=$(get_stock "$MAT_A")

echo ""
echo "  ⚠️ 关键检查: scanOutbound 是否扣减了库存?"
echo "  扫码前库存: $STK_BEFORE_SCAN"
echo "  扫码后库存: $STK_AFTER_SCAN"
if [ "$STK_BEFORE_SCAN" = "$STK_AFTER_SCAN" ]; then
    echo "  ❌ BUG确认: scanOutbound 没有扣减库存! (库存多出=$STK_BEFORE_SCAN)"
    FAIL=$((FAIL + 1))
    ERRORS="$ERRORS\n  [BUG] scanOutbound不扣库存: before=$STK_BEFORE_SCAN after=$STK_AFTER_SCAN"
else
    echo "  ✅ scanOutbound 已扣减库存"
    PASS=$((PASS + 1))
fi

# ==========================================================================
# Phase 7: 出库单修改/删除 库存一致性
# ==========================================================================
echo ""
echo "============================================================"
echo " Phase 7: 出库单修改库存一致性"
echo "============================================================"

# --- Test 7.1: 出库确认后不可删除 ---
echo ""
echo "--- Test 7.1: 已确认出库单不可删除 ---"
# 用 MAT_B 创建并确认一个出库单
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_B\",\"planQty\":5}]}")
OUT_B2=$(get_data_field "$RESP" "id")
OUT_DETAIL_B2=$(curl -s "$BASE/api/outbound/orders/$OUT_B2" -H "$AUTH")
DET_B2=$(echo "$OUT_DETAIL_B2" | python3 -c "import sys,json; d=json.load(sys.stdin); dets=d.get('data',{}).get('details',[]); print(dets[0]['id'] if dets else '0')" 2>/dev/null)
STK_B_BEFORE=$(get_stock "$MAT_B")
curl -s -X PUT "$BASE/api/outbound/orders/$OUT_B2/confirm" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"detailId\":$DET_B2,\"actualQty\":5}]}" > /dev/null
STK_B_AFTER=$(get_stock "$MAT_B")
check_eq "7.1-确认后库存扣减" "$((STK_B_BEFORE - 5))" "$STK_B_AFTER"

# 尝试删除
RESP=$(curl -s -X DELETE "$BASE/api/outbound/orders/$OUT_B2" -H "$AUTH")
CODE=$(get_json_field "$RESP" "code")
check_eq "7.1-拒绝删除已完成出库(400)" "400" "$CODE"

# --- Test 7.2: 库存不足拒绝出库 ---
echo ""
echo "--- Test 7.2: 库存不足拒绝 ---"
RESP=$(curl -s -X POST "$BASE/api/outbound/orders" -H "$AUTH" -H "$CT" \
    -d "{\"details\":[{\"materialCode\":\"$MAT_C\",\"planQty\":9999}]}")
CODE=$(get_json_field "$RESP" "code")
check_eq "7.2-库存不足拒绝(400)" "400" "$CODE"

# ==========================================================================
# 最终报告
# ==========================================================================
echo ""
echo "============================================================"
echo " 最终测试报告"
echo "============================================================"
echo "  ✅ 通过: $PASS"
echo "  ❌ 失败: $FAIL"
if [ -n "$WARNINGS" ]; then
    echo ""
    echo " ⚠️ 警告:"
    echo -e "$WARNINGS"
fi
if [ "$FAIL" -gt 0 ]; then
    echo ""
    echo " ❌ 失败详情:"
    echo -e "$ERRORS"
fi
echo ""
echo " 测试物料: $MAT_A, $MAT_B, $MAT_C (可保留或手动清理)"
echo "============================================================"
