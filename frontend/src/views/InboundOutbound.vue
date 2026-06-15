<!--
  入库与出库管理页。
  @author Focus
  @date 2026-06-15
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <el-tabs v-model="activeTab">
        <!-- === 入库管理 === -->
        <el-tab-pane label="入库管理" name="inbound">
          <div class="inbound-summary">
            <div>
              <div class="summary-title">入库单流转</div>
              <div class="summary-desc">创建入库单后确认到货，系统会生成条码并更新库存。</div>
            </div>
            <div class="summary-stats">
              <span>待入库 {{ pendingInboundCount }}</span>
              <span>已完成 {{ completedInboundCount }}</span>
            </div>
          </div>

          <div class="toolbar inbound-toolbar">
            <el-button type="primary" size="small" @click="openInboundDialog">
              <el-icon :size="14"><Plus /></el-icon>
              <span>新建入库单</span>
            </el-button>
            <el-button size="small" :loading="inboundLoading" @click="loadInboundOrders">刷新</el-button>
          </div>
          <el-table :data="inboundList" stripe size="small" v-loading="inboundLoading"
            empty-text="暂无入库单数据">
            <el-table-column prop="orderNo" label="入库单号" min-width="180" show-overflow-tooltip />
            <el-table-column label="供应商" min-width="200">
              <template #default="{ row }">
                {{ getSupplierName(row.supplierCode) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <span class="badge" :class="row.status === '已完成' ? 'badge-success' : 'badge-default'">
                  {{ row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button v-if="row.status !== '已完成'" type="success" link size="small"
                  @click="handleConfirmInbound(row)">
                  确认入库
                </el-button>
                <span v-else class="muted-text">无需操作</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- === 出库管理 === -->
        <el-tab-pane label="出库管理" name="outbound">
          <div class="inbound-summary">
            <div>
              <div class="summary-title">出库单流转</div>
              <div class="summary-desc">创建出库单后确认出库，系统会扣减库存并更新条码状态。</div>
            </div>
            <div class="summary-stats">
              <span>待出库 {{ pendingOutboundCount }}</span>
              <span>已完成 {{ completedOutboundCount }}</span>
            </div>
          </div>

          <div class="toolbar inbound-toolbar">
            <el-button type="primary" size="small" @click="openOutboundDialog">
              <el-icon :size="14"><Plus /></el-icon>
              <span>新建出库单</span>
            </el-button>
            <el-button size="small" :loading="outboundLoading" @click="loadOutboundOrders">刷新</el-button>
          </div>
          <el-table :data="outboundList" stripe size="small" v-loading="outboundLoading"
            empty-text="暂无出库单数据">
            <el-table-column prop="orderNo" label="出库单号" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <span class="badge" :class="row.status === '已完成' ? 'badge-success' : 'badge-default'">
                  {{ row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button v-if="row.status !== '已完成'" type="warning" link size="small"
                  @click="handleConfirmOutbound(row)">
                  确认出库
                </el-button>
                <span v-else class="muted-text">无需操作</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新建入库单对话框 -->
    <Teleport to="body">
      <el-dialog v-model="inboundDialogVisible" title="新建入库单"
        width="min(720px, calc(100vw - 32px))" destroy-on-close class="inbound-dialog">
        <el-alert v-if="isAiDraft" title="已根据 AI 建议预填物料和计划数量，请选择供应商后保存。"
          type="info" show-icon :closable="false" class="draft-alert" />
        <el-form ref="inboundFormRef" :model="inboundForm" :rules="inboundRules" label-width="88px">
          <el-form-item label="供应商" prop="supplierCode">
            <el-select v-model="inboundForm.supplierCode" placeholder="请选择供应商" style="width: 100%"
              @change="onInboundSupplierChange">
              <el-option v-for="s in allSuppliers" :key="s.supplierCode"
                :label="`${s.supplierName} (${s.supplierCode})`" :value="s.supplierCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="物料明细" prop="details">
            <div class="detail-editor">
              <div class="detail-head">
                <span>物料号</span>
                <span>单箱容量</span>
                <span>计划入库数</span>
                <span>操作</span>
              </div>
              <div v-for="(item, idx) in inboundForm.details" :key="idx" class="detail-row">
                <el-select v-model="item.materialCode" placeholder="选择物料" filterable size="small"
                  @change="onInboundMaterialChange(idx)">
                  <el-option v-for="m in allMaterials" :key="m.materialCode"
                    :label="`${m.materialName} (${m.materialCode})`" :value="m.materialCode" />
                </el-select>
                <el-input-number v-model="item.packCapacity" :min="1" :max="999999" size="small"
                  controls-position="right" />
                <el-input-number v-model="item.planQty" :min="1" :max="999999" size="small"
                  controls-position="right" />
                <el-button type="danger" link size="small" @click="removeInboundDetail(idx)"
                  :disabled="inboundForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon>
                  <span>删除</span>
                </el-button>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addInboundDetail">
                  <el-icon :size="14"><Plus /></el-icon>
                  <span>添加物料行</span>
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">保存后可在列表中执行确认入库。</span>
            <div>
              <el-button @click="inboundDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="handleCreateInbound">保存</el-button>
            </div>
          </div>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 新建出库单对话框 -->
    <Teleport to="body">
      <el-dialog v-model="outboundDialogVisible" title="新建出库单"
        width="min(720px, calc(100vw - 32px))" destroy-on-close class="inbound-dialog">
        <el-form ref="outboundFormRef" :model="outboundForm" label-width="88px">
          <el-form-item label="物料明细">
            <div class="detail-editor">
              <div class="detail-head">
                <span>物料号</span>
                <span>单箱容量</span>
                <span>计划出库数</span>
                <span>操作</span>
              </div>
              <div v-for="(item, idx) in outboundForm.details" :key="idx" class="detail-row">
                <el-select v-model="item.materialCode" placeholder="选择物料" filterable size="small"
                  @change="onOutboundMaterialChange(idx)">
                  <el-option v-for="m in allMaterials" :key="m.materialCode"
                    :label="`${m.materialName} (${m.materialCode})`" :value="m.materialCode" />
                </el-select>
                <el-input-number v-model="item.packCapacity" :min="1" :max="999999" size="small"
                  controls-position="right" />
                <el-input-number v-model="item.planQty" :min="1" :max="999999" size="small"
                  controls-position="right" />
                <el-button type="danger" link size="small" @click="removeOutboundDetail(idx)"
                  :disabled="outboundForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon>
                  <span>删除</span>
                </el-button>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addOutboundDetail">
                  <el-icon :size="14"><Plus /></el-icon>
                  <span>添加物料行</span>
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">保存后可在列表中执行确认出库，确认后将扣减库存。</span>
            <div>
              <el-button @click="outboundDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="handleCreateOutbound">保存</el-button>
            </div>
          </div>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getInboundOrders, createInbound, confirmInbound } from '@/api/inbound'
import { getOutboundOrders, createOutbound, confirmOutbound } from '@/api/outbound'
import { getSuppliers } from '@/api/suppliers'
import { getMaterials } from '@/api/materials'
import { getAppliances, lookupAppliance } from '@/api/appliances'

const route = useRoute()
const router = useRouter()

const activeTab = ref('inbound')

// ========== 公共缓存 ==========
const allSuppliers = ref([])
const allMaterials = ref([])
const allAppliances = ref([])

async function loadCacheData() {
  try {
    const [supRes, matRes, appRes] = await Promise.all([
      getSuppliers({ page: 1, size: 200 }),
      getMaterials({ page: 1, size: 200 }),
      getAppliances({ page: 1, size: 200 })
    ])
    allSuppliers.value = supRes.records || []
    allMaterials.value = matRes.records || []
    allAppliances.value = appRes.records || []
  } catch { /* */ }
}

function getSupplierName(code) {
  const s = allSuppliers.value.find(x => x.supplierCode === code)
  return s ? `${s.supplierName} (${code})` : code
}

function findApplianceCapacity(materialCode, supplierCode) {
  const a = allAppliances.value.find(x => x.materialCode === materialCode && x.supplierCode === supplierCode)
  return a ? a.packCapacity : 20
}

// ========== 入库管理 ==========
const inboundList = ref([])
const inboundLoading = ref(false)
const inboundDialogVisible = ref(false)
const inboundFormRef = ref(null)
const isAiDraft = ref(false)

const inboundForm = reactive({
  supplierCode: '',
  details: [{ materialCode: '', packCapacity: 20, planQty: 200 }]
})
const inboundRules = {
  supplierCode: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}

const pendingInboundCount = computed(() => inboundList.value.filter(row => row.status !== '已完成').length)
const completedInboundCount = computed(() => inboundList.value.filter(row => row.status === '已完成').length)

onMounted(() => {
  loadInboundOrders()
  loadOutboundOrders()
  loadCacheData()
  applyAiInboundDraft()
})

watch(
  () => route.query,
  () => applyAiInboundDraft()
)

async function loadInboundOrders() {
  inboundLoading.value = true
  try {
    const data = await getInboundOrders({ page: 1, size: 50 })
    inboundList.value = data.records || []
  } catch { /* */ } finally {
    inboundLoading.value = false
  }
}

function addInboundDetail() {
  inboundForm.details.push({ materialCode: '', packCapacity: 20, planQty: 100 })
}

function removeInboundDetail(idx) {
  if (inboundForm.details.length > 1) inboundForm.details.splice(idx, 1)
}

function openInboundDialog() {
  isAiDraft.value = false
  inboundForm.supplierCode = ''
  inboundForm.details = [{ materialCode: '', packCapacity: 20, planQty: 200 }]
  inboundDialogVisible.value = true
}

function onInboundSupplierChange() {
  // 当供应商变化时，重新自动填充已有物料行的包装容量
  inboundForm.details.forEach(item => {
    if (item.materialCode) {
      item.packCapacity = findApplianceCapacity(item.materialCode, inboundForm.supplierCode)
    }
  })
}

function onInboundMaterialChange(idx) {
  const item = inboundForm.details[idx]
  if (item.materialCode && inboundForm.supplierCode) {
    item.packCapacity = findApplianceCapacity(item.materialCode, inboundForm.supplierCode)
  }
}

function applyAiInboundDraft() {
  const materialCode = String(route.query.materialCode || '').trim()
  const suggestedQty = Number(route.query.suggestedQty || 0)
  if (!materialCode || suggestedQty <= 0) return

  isAiDraft.value = true
  activeTab.value = 'inbound'
  inboundForm.supplierCode = ''
  inboundForm.details = [{
    materialCode,
    packCapacity: findApplianceCapacity(materialCode, ''),
    planQty: suggestedQty
  }]
  inboundDialogVisible.value = true
  ElMessage.info('已根据 AI 建议预填入库明细，请选择供应商后保存')

  router.replace({ path: route.path, query: {} })
}

async function handleCreateInbound() {
  const valid = await inboundFormRef.value.validate().catch(() => false)
  if (!valid) return
  const invalidDetail = inboundForm.details.find(item =>
    !item.materialCode?.trim() || !item.packCapacity || !item.planQty
  )
  if (invalidDetail) {
    ElMessage.warning('请完整填写每一行物料明细')
    return
  }
  try {
    await createInbound({
      supplierCode: inboundForm.supplierCode,
      details: inboundForm.details
    })
    ElMessage.success('入库单创建成功')
    inboundDialogVisible.value = false
    loadInboundOrders()
  } catch { /* */ }
}

async function handleConfirmInbound(row) {
  try {
    await confirmInbound(row.id)
    ElMessage.success('入库确认成功')
    loadInboundOrders()
  } catch { /* */ }
}

// ========== 出库管理 ==========
const outboundList = ref([])
const outboundLoading = ref(false)
const outboundDialogVisible = ref(false)
const outboundFormRef = ref(null)

const outboundForm = reactive({
  details: [{ materialCode: '', packCapacity: 20, planQty: 100 }]
})

const pendingOutboundCount = computed(() => outboundList.value.filter(row => row.status !== '已完成').length)
const completedOutboundCount = computed(() => outboundList.value.filter(row => row.status === '已完成').length)

async function loadOutboundOrders() {
  outboundLoading.value = true
  try {
    const data = await getOutboundOrders({ page: 1, size: 50 })
    outboundList.value = data.records || []
  } catch { /* */ } finally {
    outboundLoading.value = false
  }
}

function addOutboundDetail() {
  outboundForm.details.push({ materialCode: '', packCapacity: 20, planQty: 100 })
}

function removeOutboundDetail(idx) {
  if (outboundForm.details.length > 1) outboundForm.details.splice(idx, 1)
}

function openOutboundDialog() {
  outboundForm.details = [{ materialCode: '', packCapacity: 20, planQty: 100 }]
  outboundDialogVisible.value = true
}

function onOutboundMaterialChange(idx) {
  const item = outboundForm.details[idx]
  if (item.materialCode) {
    // 尝试找到第一个匹配的器具配置
    const a = allAppliances.value.find(x => x.materialCode === item.materialCode)
    if (a) {
      item.packCapacity = a.packCapacity
    }
  }
}

async function handleCreateOutbound() {
  const invalidDetail = outboundForm.details.find(item =>
    !item.materialCode?.trim() || !item.packCapacity || !item.planQty
  )
  if (invalidDetail) {
    ElMessage.warning('请完整填写每一行物料明细')
    return
  }
  try {
    await createOutbound({
      details: outboundForm.details
    })
    ElMessage.success('出库单创建成功')
    outboundDialogVisible.value = false
    loadOutboundOrders()
  } catch { /* */ }
}

async function handleConfirmOutbound(row) {
  try {
    await confirmOutbound(row.id)
    ElMessage.success('出库确认成功')
    loadOutboundOrders()
  } catch { /* */ }
}
</script>

<style scoped>
.detail-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 128px 140px 74px;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-bottom: 10px;
}
.inbound-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  margin-bottom: 14px;
  background: #f7f9fc;
  border: 1px solid var(--border-light);
  border-radius: 4px;
}
.summary-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.summary-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}
.summary-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  color: var(--text-regular);
  font-size: 12px;
}
.summary-stats span {
  padding: 4px 8px;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 3px;
  white-space: nowrap;
}
.inbound-toolbar :deep(.el-button) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.draft-alert {
  margin-bottom: 16px;
}
.detail-editor {
  width: 100%;
  min-width: 0;
}
.detail-head {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 128px 140px 74px;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}
.detail-row :deep(.el-input-number) {
  width: 100%;
}
.detail-row :deep(.el-input__wrapper) {
  min-width: 0;
}
.detail-row :deep(.el-button) {
  display: inline-flex;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  padding-left: 2px;
  padding-right: 2px;
}
.detail-actions {
  padding-top: 2px;
}
.detail-actions :deep(.el-button) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.footer-tip,
.muted-text {
  font-size: 12px;
  color: var(--text-secondary);
}
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 12px;
  white-space: nowrap;
}
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-default { background: #f4f4f5; color: #909399; }

@media (max-width: 760px) {
  .inbound-summary,
  .dialog-footer {
    align-items: flex-start;
    flex-direction: column;
  }
  .summary-stats {
    flex-wrap: wrap;
  }
  .detail-head {
    display: none;
  }
  .detail-row {
    grid-template-columns: 1fr;
    padding: 10px;
    border: 1px solid var(--border-light);
    border-radius: 4px;
  }
  .detail-row :deep(.el-button) {
    justify-content: flex-start;
  }
}
</style>
