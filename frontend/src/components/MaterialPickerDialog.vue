<!--
  物料选择弹窗 — 封装物料目录浏览器 + 明细编辑器。
  支持入库（按供应商过滤）和出库（全部物料）两种模式。

  @author Focus
  @date 2026-06-28
-->
<template>
  <Teleport to="body">
    <el-dialog
      v-model="visible"
      :title="title"
      width="88%"
      destroy-on-close
      :close-on-click-modal="false"
      @opened="onOpened"
    >
      <!-- ========== 物料目录面板 ========== -->
      <div class="picker-section-label">物料目录</div>
      <div class="catalog-panel">
        <div class="catalog-toolbar">
          <el-input v-model="catalogSearch" placeholder="搜索物料号或名称" size="small"
            clearable style="width: 200px" />
          <el-button size="small" @click="selectAllCatalog">全选</el-button>
          <el-button size="small" @click="invertCatalog">反选</el-button>
          <span class="catalog-hint">已选 {{ catalogChecked.length }} / {{ catalogMaterials.length }} 种</span>
        </div>
        <div class="catalog-scroll">
          <el-table :data="filteredCatalog" size="small" max-height="280"
            @selection-change="onCatalogSelect" ref="catalogTableRef" row-key="materialCode">
            <el-table-column type="selection" width="36" :selectable="catalogSelectable" />
            <el-table-column prop="materialCode" label="物料号" width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
            <el-table-column v-if="type === 'inbound'" label="供应商" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="supplier-code-text">{{ row._supplierCode || '' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="器具类型" width="100">
              <template #default="{ row }">
                <span :class="{ 'text-warn': !row._appliance }">{{ row._packType || '未配置' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="单箱容量" width="80" align="right">
              <template #default="{ row }">
                <span :class="{ 'text-warn': !row._appliance }">{{ row._packCapacity || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="catalog-actions">
          <el-button type="primary" size="small" @click="batchAddToDetails"
            :disabled="catalogChecked.length === 0">
            <el-icon :size="14"><Plus /></el-icon>
            批量添加选中物料 ({{ catalogChecked.length }})
          </el-button>
        </div>
      </div>

      <!-- ========== 物料明细编辑区 ========== -->
      <div class="picker-section-label" style="margin-top: 20px">物料明细</div>
      <div class="detail-editor">
        <!-- 批量操作工具栏 -->
        <div class="batch-toolbar">
          <span class="batch-label">统一箱数</span>
          <el-input-number v-model="uniformBoxCount" :min="1" :max="999999" size="small"
            controls-position="right" style="width: 120px" />
          <el-button size="small" @click="applyUniformBoxCount">应用</el-button>
          <el-divider direction="vertical" />
          <el-button size="small" @click="clearAllDetails" :disabled="details.length <= 1">
            <el-icon :size="14"><Delete /></el-icon>清空列表
          </el-button>
          <span class="batch-summary">
            共 <b>{{ details.filter(d => d.materialCode).length }}</b> 种物料 /
            <b>{{ totalBoxes }}</b> 箱 /
            <b>{{ totalQty }}</b> 件
          </span>
        </div>
        <!-- 表头 -->
        <div class="detail-head">
          <span class="col-seq">#</span>
          <span>物料号</span>
          <span>器具类型</span>
          <span>单箱容量</span>
          <span>{{ type === 'inbound' ? '入库箱数' : '出库箱数' }}</span>
          <span>总件数</span>
          <span>操作</span>
        </div>
        <!-- 明细行滚动区 -->
        <div class="detail-scroll">
          <div v-for="(item, idx) in details" :key="idx" class="detail-row">
            <span class="col-seq seq-num">{{ idx + 1 }}</span>
            <el-select v-model="item.materialCode"
              placeholder="搜索物料号"
              size="small" filterable remote
              :remote-method="(q) => searchMaterials(q, idx)"
              :loading="materialSearchLoading[idx]" clearable style="width: 100%"
              @focus="searchMaterials('', idx)"
              @change="(val) => fetchPackCapacity(idx, val)">
              <el-option v-for="m in materialOptions[idx]" :key="m.materialCode"
                :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
            </el-select>
            <span class="readonly-cell" :title="item.packType">{{ item.packType || '—' }}</span>
            <span class="readonly-cell">{{ item.packCapacity || '—' }}</span>
            <el-input-number v-model="item.boxCount" :min="1" :max="999999" size="small"
              controls-position="right" @change="(v) => onBoxCountChange(idx, v)" />
            <el-input-number v-model="item.planQty" :min="1" :max="999999" size="small"
              controls-position="right" @change="(v) => onPlanQtyChange(idx, v)" />
            <el-button type="danger" link size="small" @click="removeDetailRow(idx)"
              :disabled="details.length <= 1">
              <el-icon :size="14"><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="detail-actions">
          <el-button type="primary" link size="small" @click="addDetailRow">
            <el-icon :size="14"><Plus /></el-icon>手动添加一行
          </el-button>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <span class="footer-tip">
            合计 {{ details.filter(d => d.materialCode).length }} 种物料，
            {{ totalBoxes }} 箱，{{ totalQty }} 件
          </span>
          <div>
            <el-button @click="visible = false">取消</el-button>
            <el-button type="primary" @click="handleConfirm">确定</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </Teleport>
</template>

<script setup>
/**
 * 物料选择弹窗 — 独立封装物料目录浏览和明细编辑。
 * 通过 v-model:details 双向绑定明细数组，组件内部直接修改，
 * 父组件在"确定"后读取最新数据。
 *
 * @author Focus
 * @date 2026-06-28
 */
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { getMaterials } from '@/api/materials'
import { getAppliances } from '@/api/appliances'

// ==================== Props & Emits ====================
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  details: { type: Array, required: true },
  supplierCodes: { type: Array, default: () => [] },
  title: { type: String, default: '选择物料' },
  /** 'inbound' 按供应商过滤目录，'outbound' 显示全部 */
  type: { type: String, default: 'inbound' }
})

const emit = defineEmits(['update:modelValue', 'update:details'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// ==================== 物料目录状态 ====================
const catalogMaterials = ref([])
const catalogChecked = ref([])
const catalogSearch = ref('')
const catalogTableRef = ref(null)
const uniformBoxCount = ref(1)

// ==================== 物料搜索下拉状态 ====================
const materialOptions = ref({})
const materialSearchLoading = ref({})

// ==================== 双向联动防循环标志 ====================
const linkFlags = reactive({})

// ==================== 汇总计算 ====================
const totalBoxes = computed(() =>
  props.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) : 0), 0)
)
const totalQty = computed(() =>
  props.details.reduce((sum, d) => sum + (d.materialCode
    ? (d.planQty > 0 ? d.planQty : (d.boxCount || 0) * (d.packCapacity || 0))
    : 0), 0)
)

const filteredCatalog = computed(() => {
  if (!catalogSearch.value) return catalogMaterials.value
  const kw = catalogSearch.value.toLowerCase()
  return catalogMaterials.value.filter(m =>
    m.materialCode?.toLowerCase().includes(kw) || m.materialName?.toLowerCase().includes(kw))
})

// ==================== 弹窗打开时加载物料目录 ====================
function onOpened() {
  loadCatalog()
}

async function loadCatalog() {
  const codes = props.supplierCodes || []
  try {
    if (codes.length > 0) {
      // 按供应商过滤加载
      await loadMultiSupplierCatalog(codes)
    } else {
      // 出库模式：加载全部物料
      await loadAllCatalog()
    }
  } catch { catalogMaterials.value = [] }
}

/** 按供应商列表加载物料目录 */
async function loadMultiSupplierCatalog(suppliers) {
  if (suppliers.length === 0) { catalogMaterials.value = []; return }
  try {
    const [matRes, appRes] = await Promise.all([
      getMaterials({ page: 1, size: 500 }),
      getAppliances({ page: 1, size: 500 })
    ])
    const supplierSet = new Set(suppliers)
    const materials = (matRes.records || []).filter(m => supplierSet.has(m.supplierCode))
    const appliances = appRes.records || []
    catalogMaterials.value = materials.map(m => {
      const app = appliances.find(a => a.materialCode === m.materialCode && a.supplierCode === m.supplierCode)
      return {
        materialCode: m.materialCode,
        materialName: m.materialName,
        _supplierCode: m.supplierCode,
        _appliance: !!app,
        _packType: app?.packType || '',
        _packCapacity: app?.packCapacity || 0
      }
    })
  } catch { catalogMaterials.value = [] }
}

/** 加载全部物料目录（出库模式） */
async function loadAllCatalog() {
  try {
    const [matRes, appRes] = await Promise.all([
      getMaterials({ page: 1, size: 200 }),
      getAppliances({ page: 1, size: 500 })
    ])
    const materials = matRes.records || []
    const appliances = appRes.records || []
    catalogMaterials.value = materials.map(m => {
      const app = appliances.find(a => a.materialCode === m.materialCode)
      return {
        materialCode: m.materialCode,
        materialName: m.materialName,
        _appliance: !!app,
        _packType: app?.packType || '',
        _packCapacity: app?.packCapacity || 0
      }
    })
  } catch { catalogMaterials.value = [] }
}

// ==================== 物料搜索（下拉框远程搜索） ====================
async function searchMaterials(query, idx) {
  const codes = props.supplierCodes || []
  // 入库模式：未选供应商时不发起搜索
  if (props.type === 'inbound' && codes.length === 0) {
    materialOptions.value[idx] = []
    return
  }
  materialSearchLoading.value[idx] = true
  try {
    const data = await getMaterials({ page: 1, size: 20, keyword: query || undefined })
    if (props.type === 'inbound') {
      const supplierSet = new Set(codes)
      materialOptions.value[idx] = (data.records || []).filter(m => supplierSet.has(m.supplierCode))
    } else {
      materialOptions.value[idx] = data.records || []
    }
  } catch {
    materialOptions.value[idx] = []
  } finally {
    materialSearchLoading.value[idx] = false
  }
}

/**
 * 物料选中后自动获取器具包装容量。
 * 入库模式优先级：物料+供应商精确匹配 → 物料号匹配
 * 出库模式：按物料号匹配
 */
async function fetchPackCapacity(idx, materialCode) {
  const item = props.details[idx]
  if (!materialCode) {
    item.packType = ''
    item.packCapacity = 0
    item.planQty = 0
    return
  }
  try {
    const data = await getAppliances({ page: 1, size: 20, keyword: materialCode })
    let match = null
    if (props.type === 'inbound') {
      const codes = props.supplierCodes || []
      // 优先匹配当前供应商的器具配置
      for (const sc of codes) {
        match = (data.records || []).find(a => a.materialCode === materialCode && a.supplierCode === sc)
        if (match) break
      }
    }
    // 回退：按物料号匹配
    if (!match) {
      match = (data.records || []).find(a => a.materialCode === materialCode)
    }
    if (match && match.packCapacity > 0) {
      item.packType = match.packType || ''
      item.packCapacity = match.packCapacity
      item.planQty = (item.boxCount || 1) * match.packCapacity
    } else {
      item.packType = ''
      item.packCapacity = 0
      item.planQty = 0
    }
  } catch { /* 获取失败时保留默认值 */ }
}

// ==================== 目录选择操作 ====================
function onCatalogSelect(val) { catalogChecked.value = val }
function catalogSelectable(row) {
  if (!row._appliance) return false
  const exists = props.details.some(d => d.materialCode === row.materialCode)
  return !exists
}
/** 全选：使用 toggleRowSelection 逐个选中所有可选行 */
function selectAllCatalog() {
  const table = catalogTableRef.value
  if (!table) return
  filteredCatalog.value.forEach(row => {
    if (catalogSelectable(row)) table.toggleRowSelection(row, true)
  })
}
function invertCatalog() {
  const table = catalogTableRef.value
  if (!table) return
  const checkedSet = new Set(catalogChecked.value.map(r => r.materialCode))
  filteredCatalog.value.forEach(row => {
    if (catalogSelectable(row)) table.toggleRowSelection(row, !checkedSet.has(row.materialCode))
  })
}

/** 批量添加目录中选中的物料到明细列表 */
function batchAddToDetails() {
  const selectedCount = catalogChecked.value.length
  let addedCount = 0
  for (const row of catalogChecked.value) {
    if (!row._appliance) continue
    const exists = props.details.some(d => d.materialCode === row.materialCode)
    if (exists) continue
    const emptyIdx = props.details.findIndex(d => !d.materialCode)
    if (emptyIdx >= 0) {
      props.details[emptyIdx] = {
        materialCode: row.materialCode, supplierCode: row._supplierCode || '',
        packType: row._packType, packCapacity: row._packCapacity,
        boxCount: 1, planQty: row._packCapacity
      }
    } else {
      props.details.push({
        materialCode: row.materialCode, supplierCode: row._supplierCode || '',
        packType: row._packType, packCapacity: row._packCapacity,
        boxCount: 1, planQty: row._packCapacity
      })
    }
    addedCount++
  }
  // 先保存计数再清空选择（clearSelection 会触发 selection-change 重置 catalogChecked）
  catalogTableRef.value?.clearSelection()
  const unselectedCount = selectedCount - addedCount
  if (unselectedCount > 0) {
    ElMessage.success(`已添加 ${addedCount} 种物料（${unselectedCount} 种已存在或未配置器具）`)
  } else {
    ElMessage.success(`已添加 ${addedCount} 种物料`)
  }
}

// ==================== 明细行编辑操作 ====================
function addDetailRow() {
  props.details.push({ materialCode: '', supplierCode: '', packType: '', packCapacity: 0, boxCount: 1, planQty: 0 })
}
function removeDetailRow(idx) {
  if (props.details.length > 1) props.details.splice(idx, 1)
}

function applyUniformBoxCount() {
  const count = uniformBoxCount.value
  let applied = 0
  props.details.forEach(d => {
    if (d.materialCode) { d.boxCount = count; d.planQty = count * (d.packCapacity || 0); applied++ }
  })
  ElMessage.success(`已将 ${applied} 行箱数统一设为 ${count}`)
}

function clearAllDetails() {
  props.details.splice(0, props.details.length, { materialCode: '', supplierCode: '', packType: '', packCapacity: 0, boxCount: 1, planQty: 0 })
}

// ==================== 箱数↔件数双向联动 ====================
function onBoxCountChange(idx, newBoxCount) {
  if (linkFlags[idx]) return
  const item = props.details[idx]
  if (!item || !item.packCapacity || item.packCapacity <= 0) return
  linkFlags[idx] = true
  item.planQty = (newBoxCount || 0) * item.packCapacity
  setTimeout(() => { linkFlags[idx] = false }, 0)
}

function onPlanQtyChange(idx, newPlanQty) {
  if (linkFlags[idx]) return
  const item = props.details[idx]
  if (!item || !item.packCapacity || item.packCapacity <= 0) return
  linkFlags[idx] = true
  item.boxCount = Math.max(1, Math.ceil((newPlanQty || 0) / item.packCapacity))
  setTimeout(() => { linkFlags[idx] = false }, 0)
}

// ==================== 确定 — 清理空行后关闭 ====================
function handleConfirm() {
  // 校验：检查有物料号的行是否都配置了容量
  const invalid = props.details.find(item =>
    item.materialCode?.trim() && (!item.packCapacity || item.packCapacity < 1)
  )
  if (invalid) {
    ElMessage.warning(`物料 ${invalid.materialCode} 未配置器具容量，请先在器具管理页面配置`)
    return
  }
  visible.value = false
}
</script>

<style scoped>
/* ==================== 区段标题 ==================== */
.picker-section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  padding-left: 2px;
}

/* ==================== 物料目录面板 ==================== */
.catalog-panel {
  border: 1px solid var(--border-light);
  border-radius: 4px;
  overflow: hidden;
}
.catalog-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid var(--border-light);
}
.catalog-hint {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: auto;
}
.catalog-scroll {
  overflow-y: auto;
}
.catalog-actions {
  padding: 8px 12px;
  border-top: 1px solid var(--border-light);
  background: #fafafa;
}

/* ==================== 明细编辑器 ==================== */
.detail-editor { width: 100%; min-width: 0; }
.detail-head {
  display: grid;
  grid-template-columns: 28px minmax(120px, 1fr) 80px 80px 100px 100px 56px;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}
.detail-row {
  display: grid;
  grid-template-columns: 28px minmax(120px, 1fr) 80px 80px 100px 100px 56px;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-bottom: 10px;
}
.detail-row :deep(.el-input-number) { width: 100%; }
.readonly-cell {
  font-size: 13px;
  color: var(--text-primary);
  padding: 4px 8px;
  background: #f5f7fa;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.total-cell {
  font-weight: 600;
  color: var(--wms-primary);
  background: #ecf5ff;
  border-color: #d9ecff;
}
.detail-row :deep(.el-input__wrapper) { min-width: 0; }
.detail-row :deep(.el-button) {
  display: inline-flex;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  padding-left: 2px;
  padding-right: 2px;
}
.detail-actions { padding-top: 2px; }
.detail-actions :deep(.el-button) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* ==================== 批量工具栏 ==================== */
.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f0f5ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.batch-label { font-size: 12px; color: var(--text-secondary); white-space: nowrap; }
.batch-summary { font-size: 12px; color: var(--text-secondary); margin-left: auto; }
.batch-summary b { color: var(--wms-primary); }

/* ==================== 明细列表区 ==================== */
.detail-scroll {
  border: 1px solid var(--border-light);
  border-radius: 4px;
  padding: 6px;
}

/* ==================== 序号列 ==================== */
.col-seq {
  width: 28px;
  flex-shrink: 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-secondary);
}
.seq-num {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  background: #f5f7fa;
  border-radius: 4px;
  font-weight: 500;
  color: var(--text-secondary);
  font-size: 12px;
}

/* ==================== 对话框底部 ==================== */
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.footer-tip { font-size: 12px; color: var(--text-secondary); }

/* ==================== 辅助 ==================== */
.text-warn { color: var(--wms-warning); font-weight: 500; }
.supplier-code-text { font-size: 11px; color: var(--text-secondary); }

/* ==================== 响应式 ==================== */
@media (max-width: 760px) {
  .detail-head { display: none; }
  .detail-row {
    grid-template-columns: 1fr;
    padding: 10px;
    border: 1px solid var(--border-light);
    border-radius: 4px;
  }
  .detail-row :deep(.el-button) { justify-content: flex-start; }
}
</style>
