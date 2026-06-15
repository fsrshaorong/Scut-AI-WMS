<!--
  物料与基础数据管理页 — 三个子 Tab：物料档案、器具配置、供应商库。
  @author Focus
  @date 2026-06-15
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <el-tabs v-model="activeTab">
        <!-- === 物料档案 === -->
        <el-tab-pane label="物料档案" name="materials">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openMaterialDialog(null)">
              <el-icon :size="14" style="margin-right: 4px"><Plus /></el-icon>新增物料
            </el-button>
            <el-input v-model="materialKeyword" placeholder="搜索物料编码或名称"
              clearable size="small" style="width: 240px" @input="loadMaterials" />
          </div>
          <el-table :data="materialList" stripe size="small" v-loading="materialLoading">
            <el-table-column prop="materialCode" label="物料号" width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column label="默认供应商" min-width="200">
              <template #default="{ row }">
                {{ getSupplierName(row.supplierCode) }}
              </template>
            </el-table-column>
            <el-table-column label="包装规格" width="200">
              <template #default="{ row }">
                <span v-if="getApplianceInfo(row.materialCode, row.supplierCode)">
                  {{ getApplianceInfo(row.materialCode, row.supplierCode).packType }}
                  ({{ getApplianceInfo(row.materialCode, row.supplierCode).packCapacity }}件/箱)
                </span>
                <span v-else class="muted-text">未配置</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openMaterialDialog(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteMaterial(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination
              v-if="materialTotal > 10"
              :current-page="materialPage" :page-size="10" :total="materialTotal"
              layout="total, prev, pager, next" small
              @current-change="loadMaterials" />
          </div>
        </el-tab-pane>

        <!-- === 器具配置 === -->
        <el-tab-pane label="器具配置" name="appliances">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openApplianceDialog(null)">
              <el-icon :size="14" style="margin-right: 4px"><Plus /></el-icon>新增器具
            </el-button>
            <el-input v-model="applianceKeyword" placeholder="搜索物料号或包装型号"
              clearable size="small" style="width: 240px" @input="loadAppliances" />
          </div>
          <el-table :data="applianceList" stripe size="small" v-loading="applianceLoading">
            <el-table-column prop="materialCode" label="物料号" width="140" />
            <el-table-column label="供应商" min-width="180">
              <template #default="{ row }">
                {{ getSupplierName(row.supplierCode) }}
              </template>
            </el-table-column>
            <el-table-column prop="packType" label="包装器具型号" min-width="160" />
            <el-table-column prop="packCapacity" label="满载容量(件)" width="120" align="right" />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openApplianceDialog(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteAppliance(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination
              v-if="applianceTotal > 10"
              :current-page="appliancePage" :page-size="10" :total="applianceTotal"
              layout="total, prev, pager, next" small
              @current-change="loadAppliances" />
          </div>
        </el-tab-pane>

        <!-- === 供应商库 === -->
        <el-tab-pane label="供应商库" name="suppliers">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openSupplierDialog(null)">
              <el-icon :size="14" style="margin-right: 4px"><Plus /></el-icon>新增供应商
            </el-button>
            <el-input v-model="supplierKeyword" placeholder="搜索供应商编码或名称"
              clearable size="small" style="width: 240px" @input="loadSuppliers" />
          </div>
          <el-table :data="supplierList" stripe size="small" v-loading="supplierLoading">
            <el-table-column prop="supplierCode" label="供应商代码" width="160" />
            <el-table-column prop="supplierName" label="供应商名称" min-width="220" />
            <el-table-column prop="contactName" label="联系人" width="120" />
            <el-table-column prop="contactPhone" label="联系电话" width="140" />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openSupplierDialog(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteSupplier(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination
              v-if="supplierTotal > 10"
              :current-page="supplierPage" :page-size="10" :total="supplierTotal"
              layout="total, prev, pager, next" small
              @current-change="loadSuppliers" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新增/编辑物料对话框 -->
    <Teleport to="body">
      <el-dialog v-model="materialDialogVisible" :title="materialDialogTitle" width="480px" destroy-on-close>
        <el-form ref="materialFormRef" :model="materialForm" :rules="materialRules" label-width="100px">
          <el-form-item label="物料号" prop="materialCode">
            <el-input v-model="materialForm.materialCode" :disabled="!!materialEditingRow" placeholder="如 M_PART_001" />
          </el-form-item>
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="materialForm.materialName" placeholder="如 左前大灯总成" />
          </el-form-item>
          <el-form-item label="默认供应商" prop="supplierCode">
            <el-select v-model="materialForm.supplierCode" placeholder="请选择供应商" style="width: 100%">
              <el-option v-for="s in allSuppliers" :key="s.supplierCode"
                :label="`${s.supplierName} (${s.supplierCode})`" :value="s.supplierCode" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="materialDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveMaterial">保存</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 新增/编辑器具对话框 -->
    <Teleport to="body">
      <el-dialog v-model="applianceDialogVisible" :title="applianceDialogTitle" width="520px" destroy-on-close>
        <el-form ref="applianceFormRef" :model="applianceForm" :rules="applianceRules" label-width="120px">
          <el-form-item label="物料号" prop="materialCode">
            <el-select v-model="applianceForm.materialCode" placeholder="请选择物料" filterable style="width: 100%"
              :disabled="!!applianceEditingRow">
              <el-option v-for="m in allMaterials" :key="m.materialCode"
                :label="`${m.materialName} (${m.materialCode})`" :value="m.materialCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="供应商" prop="supplierCode">
            <el-select v-model="applianceForm.supplierCode" placeholder="请选择供应商" style="width: 100%"
              :disabled="!!applianceEditingRow">
              <el-option v-for="s in allSuppliers" :key="s.supplierCode"
                :label="`${s.supplierName} (${s.supplierCode})`" :value="s.supplierCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="包装器具型号" prop="packType">
            <el-input v-model="applianceForm.packType" placeholder="如 标准铁箱、塑料周转箱" />
          </el-form-item>
          <el-form-item label="满载容量(件)" prop="packCapacity">
            <el-input-number v-model="applianceForm.packCapacity" :min="1" :max="999999"
              controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="applianceDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveAppliance">保存</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 新增/编辑供应商对话框 -->
    <Teleport to="body">
      <el-dialog v-model="supplierDialogVisible" :title="supplierDialogTitle" width="520px" destroy-on-close>
        <el-form ref="supplierFormRef" :model="supplierForm" :rules="supplierRules" label-width="100px">
          <el-form-item label="供应商代码" prop="supplierCode">
            <el-input v-model="supplierForm.supplierCode" :disabled="!!supplierEditingRow" placeholder="如 SUP_VWG_09" />
          </el-form-item>
          <el-form-item label="供应商名称" prop="supplierName">
            <el-input v-model="supplierForm.supplierName" placeholder="如 一汽大众佛山配件厂" />
          </el-form-item>
          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="supplierForm.contactName" placeholder="联系人姓名" />
          </el-form-item>
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="supplierForm.contactPhone" placeholder="联系电话" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="supplierDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveSupplier">保存</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 删除确认对话框 -->
    <Teleport to="body">
      <el-dialog v-model="deleteVisible" title="删除确认" width="400px"
        :close-on-click-modal="false" destroy-on-close>
        <p style="font-size: 15px; text-align: center; padding: 10px 0;">
          <el-icon :size="22" color="#f56c6c" style="vertical-align: middle; margin-right: 6px;">
            <WarningFilled />
          </el-icon>
          {{ deleteMessage }}
        </p>
        <template #footer>
          <el-button @click="deleteVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmDelete">确定</el-button>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMaterials, createMaterial, updateMaterial, deleteMaterial } from '@/api/materials'
import { getAppliances, createAppliance, updateAppliance, deleteAppliance } from '@/api/appliances'
import { getSuppliers, createSupplier, updateSupplier, deleteSupplier } from '@/api/suppliers'

const activeTab = ref('materials')

// ========== 公共数据缓存 ==========
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

function getApplianceInfo(materialCode, supplierCode) {
  return allAppliances.value.find(a => a.materialCode === materialCode && a.supplierCode === supplierCode)
}

// ========== 物料管理 ==========
const materialList = ref([])
const materialLoading = ref(false)
const materialPage = ref(1)
const materialTotal = ref(0)
const materialKeyword = ref('')

const materialDialogVisible = ref(false)
const materialEditingRow = ref(null)
const materialFormRef = ref(null)
const materialForm = reactive({ materialCode: '', materialName: '', supplierCode: '' })
const materialRules = {
  materialCode: [{ required: true, message: '请输入物料号', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  supplierCode: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}
const materialDialogTitle = computed(() => materialEditingRow.value ? '编辑物料' : '新增物料')

async function loadMaterials(page = 1) {
  materialPage.value = page
  materialLoading.value = true
  try {
    const data = await getMaterials({ page, size: 10, keyword: materialKeyword.value })
    materialList.value = data.records || []
    materialTotal.value = data.total || 0
  } catch { /* */ } finally {
    materialLoading.value = false
  }
}

function openMaterialDialog(row) {
  materialEditingRow.value = row
  if (row) {
    Object.assign(materialForm, { materialCode: row.materialCode, materialName: row.materialName, supplierCode: row.supplierCode })
  } else {
    materialFormRef.value?.resetFields()
  }
  materialDialogVisible.value = true
}

async function handleSaveMaterial() {
  const valid = await materialFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    if (materialEditingRow.value) {
      await updateMaterial(materialEditingRow.value.id, { ...materialForm })
      ElMessage.success('物料更新成功')
    } else {
      await createMaterial({ ...materialForm })
      ElMessage.success('物料创建成功')
    }
    materialDialogVisible.value = false
    loadMaterials(materialPage.value)
    loadCacheData()
  } catch { /* */ }
}

function handleDeleteMaterial(row) {
  deleteMessage.value = `确定删除物料 ${row.materialCode}？`
  deleteAction.value = async () => {
    await deleteMaterial(row.id)
    ElMessage.success('已删除')
    loadMaterials(materialPage.value)
    loadCacheData()
  }
  deleteVisible.value = true
}

// ========== 器具管理 ==========
const applianceList = ref([])
const applianceLoading = ref(false)
const appliancePage = ref(1)
const applianceTotal = ref(0)
const applianceKeyword = ref('')

const applianceDialogVisible = ref(false)
const applianceEditingRow = ref(null)
const applianceFormRef = ref(null)
const applianceForm = reactive({ materialCode: '', supplierCode: '', packType: '', packCapacity: 20 })
const applianceRules = {
  materialCode: [{ required: true, message: '请选择物料', trigger: 'change' }],
  supplierCode: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  packType: [{ required: true, message: '请输入包装器具型号', trigger: 'blur' }],
  packCapacity: [{ required: true, message: '请输入满载容量', trigger: 'blur' }]
}
const applianceDialogTitle = computed(() => applianceEditingRow.value ? '编辑器具' : '新增器具')

async function loadAppliances(page = 1) {
  appliancePage.value = page
  applianceLoading.value = true
  try {
    const data = await getAppliances({ page, size: 10, keyword: applianceKeyword.value })
    applianceList.value = data.records || []
    applianceTotal.value = data.total || 0
  } catch { /* */ } finally {
    applianceLoading.value = false
  }
}

function openApplianceDialog(row) {
  applianceEditingRow.value = row
  if (row) {
    Object.assign(applianceForm, {
      materialCode: row.materialCode,
      supplierCode: row.supplierCode,
      packType: row.packType,
      packCapacity: row.packCapacity
    })
  } else {
    applianceFormRef.value?.resetFields()
  }
  applianceDialogVisible.value = true
}

async function handleSaveAppliance() {
  const valid = await applianceFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    if (applianceEditingRow.value) {
      await updateAppliance(applianceEditingRow.value.id, { ...applianceForm })
      ElMessage.success('器具配置更新成功')
    } else {
      await createAppliance({ ...applianceForm })
      ElMessage.success('器具配置创建成功')
    }
    applianceDialogVisible.value = false
    loadAppliances(appliancePage.value)
    loadCacheData()
  } catch { /* */ }
}

function handleDeleteAppliance(row) {
  deleteMessage.value = `确定删除物料 ${row.materialCode} 的器具配置？`
  deleteAction.value = async () => {
    await deleteAppliance(row.id)
    ElMessage.success('已删除')
    loadAppliances(appliancePage.value)
    loadCacheData()
  }
  deleteVisible.value = true
}

// ========== 供应商管理 ==========
const supplierList = ref([])
const supplierLoading = ref(false)
const supplierPage = ref(1)
const supplierTotal = ref(0)
const supplierKeyword = ref('')

const supplierDialogVisible = ref(false)
const supplierEditingRow = ref(null)
const supplierFormRef = ref(null)
const supplierForm = reactive({ supplierCode: '', supplierName: '', contactName: '', contactPhone: '' })
const supplierRules = {
  supplierCode: [{ required: true, message: '请输入供应商代码', trigger: 'blur' }],
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}
const supplierDialogTitle = computed(() => supplierEditingRow.value ? '编辑供应商' : '新增供应商')

async function loadSuppliers(page = 1) {
  supplierPage.value = page
  supplierLoading.value = true
  try {
    const data = await getSuppliers({ page, size: 10, keyword: supplierKeyword.value })
    supplierList.value = data.records || []
    supplierTotal.value = data.total || 0
  } catch { /* */ } finally {
    supplierLoading.value = false
  }
}

function openSupplierDialog(row) {
  supplierEditingRow.value = row
  if (row) {
    Object.assign(supplierForm, {
      supplierCode: row.supplierCode,
      supplierName: row.supplierName,
      contactName: row.contactName || '',
      contactPhone: row.contactPhone || ''
    })
  } else {
    supplierFormRef.value?.resetFields()
  }
  supplierDialogVisible.value = true
}

async function handleSaveSupplier() {
  const valid = await supplierFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    if (supplierEditingRow.value) {
      await updateSupplier(supplierEditingRow.value.id, { ...supplierForm })
      ElMessage.success('供应商更新成功')
    } else {
      await createSupplier({ ...supplierForm })
      ElMessage.success('供应商创建成功')
    }
    supplierDialogVisible.value = false
    loadSuppliers(supplierPage.value)
    loadCacheData()
  } catch { /* */ }
}

function handleDeleteSupplier(row) {
  deleteMessage.value = `确定删除供应商 ${row.supplierCode}？`
  deleteAction.value = async () => {
    await deleteSupplier(row.id)
    ElMessage.success('已删除')
    loadSuppliers(supplierPage.value)
    loadCacheData()
  }
  deleteVisible.value = true
}

// ========== 通用删除确认 ==========
const deleteVisible = ref(false)
const deleteMessage = ref('')
const deleteAction = ref(null)

async function confirmDelete() {
  if (deleteAction.value) {
    try {
      await deleteAction.value()
    } catch { /* */ }
    deleteVisible.value = false
    deleteAction.value = null
  }
}

// ========== 初始化 ==========
onMounted(() => {
  loadCacheData()
  loadMaterials()
  loadAppliances()
  loadSuppliers()
})
</script>

<style scoped>
.muted-text {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
