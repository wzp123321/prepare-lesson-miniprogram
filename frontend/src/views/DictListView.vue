<script setup lang="ts">
// S07 字典管理 —— 类型切换（年级 / 顺延原因），el-table + 增删改
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchDicts, createDict, updateDict, deleteDict } from '@/api/dict'
import type { Dict } from '@/types'
import { errMsg } from '@/utils/error'

interface DictForm {
  id: number | undefined
  dictType: string
  dictValue: string
  sortOrder: number
  /** 启用 1 / 停用 0（与后端 dict.enabled TINYINT 对齐） */
  enabled: 0 | 1
}

const activeType = ref<'grade' | 'absent_reason'>('grade')
const typeLabel: Record<string, string> = { grade: '年级', absent_reason: '顺延原因' }

const loading = ref(false)
const list = ref<Dict[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const submitting = ref(false)
const formRef = ref()

const form = reactive<DictForm>({
  id: undefined,
  dictType: 'grade',
  dictValue: '',
  sortOrder: 1,
  enabled: 1
})

const rules = {
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }]
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    list.value = await fetchDicts(activeType.value)
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

function openAdd(): void {
  dialogMode.value = 'add'
  Object.assign(form, {
    id: undefined,
    dictType: activeType.value,
    dictValue: '',
    sortOrder: list.value.length + 1,
    enabled: 1
  })
  dialogVisible.value = true
}

function openEdit(row: Dict): void {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    dictType: row.dictType,
    dictValue: row.dictValue,
    sortOrder: row.sortOrder,
    enabled: row.enabled
  })
  dialogVisible.value = true
}

async function onSubmit(): Promise<void> {
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    submitting.value = true
    try {
      if (dialogMode.value === 'add') {
        await createDict({ ...form })
        ElMessage.success('新增成功')
      } else {
        await updateDict(form.id as number, { ...form })
        ElMessage.success('修改成功')
      }
      dialogVisible.value = false
      loadList()
    } catch (e) {
      ElMessage.error(errMsg(e))
    } finally {
      submitting.value = false
    }
  })
}

async function onDelete(row: Dict): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除字典项「${row.dictValue}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteDict(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

watch(activeType, () => loadList())

onMounted(loadList)
</script>

<template>
  <el-card>
    <!-- 工具条：类型页签在左、新增按钮在右 -->
    <div class="toolbar">
      <el-tabs v-model="activeType" class="type-tabs">
        <el-tab-pane label="年级" name="grade" />
        <el-tab-pane label="顺延原因" name="absent_reason" />
      </el-tabs>
      <el-button type="primary" class="add-btn" @click="openAdd">
        <el-icon><Plus /></el-icon>
        <span>新增{{ typeLabel[activeType] }}</span>
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" empty-text="暂无字典项">
      <el-table-column prop="dictValue" label="字典值" min-width="160" />
      <el-table-column prop="sortOrder" label="排序值" min-width="100" align="center" />
      <el-table-column label="启用" min-width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="(dialogMode === 'add' ? '新增' : '编辑') + typeLabel[activeType]"
      width="460px"
      @closed="formRef?.clearValidate?.()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="字典值" prop="dictValue">
          <el-input v-model="form.dictValue" placeholder="必填" />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sortOrder" :min="1" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.type-tabs {
  flex: 1;
  min-width: 0;
}
.type-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.add-btn {
  margin-bottom: 6px;
}
</style>
