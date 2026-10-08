<script setup lang="ts">
// 备课 · 知识点管理（BE-K-01~04）
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchKps, createKp, updateKp, deleteKp } from '@/api/kp'
import { fetchDicts } from '@/api/dict'
import type { Dict, KnowledgePoint } from '@/types'
import { errMsg } from '@/utils/error'

const CATEGORIES = ['字词', '句子', '阅读', '古诗文', '写作', '基础']

const loading = ref(false)
const list = ref<KnowledgePoint[]>([])
const grades = ref<Dict[]>([])
const filterGrade = ref('')
const filterCategory = ref('')

const drawerVisible = ref(false)
const mode = ref<'add' | 'edit'>('add')
const submitting = ref(false)
const formRef = ref()

const form = reactive({
  id: undefined as number | undefined,
  name: '',
  grade: '',
  category: '',
  sortOrder: 0,
  enabled: 1 as 0 | 1
})

const rules = {
  name: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }]
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    list.value = await fetchKps({
      grade: filterGrade.value || undefined,
      category: filterCategory.value || undefined
    })
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

async function loadGrades(): Promise<void> {
  try {
    grades.value = await fetchDicts('grade')
  } catch {
    grades.value = []
  }
}

function openAdd(): void {
  mode.value = 'add'
  Object.assign(form, {
    id: undefined,
    name: '',
    grade: filterGrade.value,
    category: filterCategory.value,
    sortOrder: list.value.length + 1,
    enabled: 1
  })
  drawerVisible.value = true
}

function openEdit(row: KnowledgePoint): void {
  mode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    name: row.name,
    grade: row.grade || '',
    category: row.category || '',
    sortOrder: row.sortOrder,
    enabled: row.enabled
  })
  drawerVisible.value = true
}

async function onSubmit(): Promise<void> {
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        name: form.name.trim(),
        grade: form.grade || null,
        category: form.category || null,
        sortOrder: form.sortOrder,
        enabled: form.enabled
      }
      if (mode.value === 'add') {
        await createKp(payload)
        ElMessage.success('已新增知识点')
      } else {
        await updateKp({ ...payload, id: form.id })
        ElMessage.success('已保存')
      }
      drawerVisible.value = false
      loadList()
    } catch (e) {
      ElMessage.error(errMsg(e))
    } finally {
      submitting.value = false
    }
  })
}

/** 行内启用开关（1/0） */
async function onToggle(row: KnowledgePoint, val: number | string | boolean): Promise<void> {
  const next = Number(val) === 1 ? 1 : 0
  try {
    await updateKp({ id: row.id, enabled: next as 0 | 1 })
    row.enabled = next as 0 | 1
  } catch (e) {
    ElMessage.error(errMsg(e))
    loadList()
  }
}

async function onDelete(row: KnowledgePoint): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除知识点「${row.name}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteKp(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

onMounted(() => {
  loadGrades()
  loadList()
})
</script>

<template>
  <el-card>
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="filterGrade" placeholder="全部年级" clearable style="width: 130px" @change="loadList">
          <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
        </el-select>
        <el-select
          v-model="filterCategory"
          placeholder="全部大类"
          clearable
          style="width: 130px"
          @change="loadList"
        >
          <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
        </el-select>
      </div>
      <el-button type="primary" @click="openAdd">新增知识点</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      empty-text="暂无知识点（可执行 backend/db/schema.sql 预置一版）"
    >
      <el-table-column prop="name" label="知识点" min-width="200" />
      <el-table-column prop="grade" label="年级" width="100" align="center">
        <template #default="{ row }">{{ row.grade || '通用' }}</template>
      </el-table-column>
      <el-table-column prop="category" label="大类" width="100" align="center">
        <template #default="{ row }">{{ row.category || '-' }}</template>
      </el-table-column>
      <el-table-column label="题量" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.questionCount > 0" type="success" effect="plain">{{ row.questionCount }}</el-tag>
          <span v-else class="muted">0</span>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
      <el-table-column label="启用" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :active-value="1"
            :inactive-value="0"
            @change="(val: number | string | boolean) => onToggle(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawerVisible" :title="mode === 'add' ? '新增知识点' : '编辑知识点'" size="420px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：修辞手法" />
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="form.grade" placeholder="不选 = 通用" clearable style="width: 100%">
            <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="大类">
          <el-select v-model="form.category" placeholder="可不选" clearable style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-drawer>
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.filters {
  display: flex;
  gap: 8px;
}
.muted {
  color: var(--text-faint);
}
</style>
