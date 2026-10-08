<script setup lang="ts">
// S01 学生管理 —— 列表 + 搜索 + 分页 + 表单(新增/编辑) + 删除保护 + 状态切换
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchStudents,
  createStudent,
  updateStudent,
  deleteStudent,
  type StudentQuery
} from '@/api/student'
import { fetchDicts } from '@/api/dict'
import type { Student, StudentStatus, Dict } from '@/types'
import { isConflict, errMsg } from '@/utils/error'
import { autoColor } from '@/utils/color'

interface StudentForm {
  id: number | undefined
  name: string
  grade: string
  phone: string
  parentWechat: string
  address: string
  price: number
  color: string
  remark: string
  status: StudentStatus
}

const loading = ref(false)
const list = ref<Student[]>([])
const total = ref(0)
const gradeOptions = ref<Dict[]>([])

const query = reactive<StudentQuery>({ keyword: '', grade: '', status: undefined, page: 1, size: 10 })

const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const submitting = ref(false)
const formRef = ref()

const form = reactive<StudentForm>({
  id: undefined,
  name: '',
  grade: '',
  phone: '',
  parentWechat: '',
  address: '',
  price: 0,
  color: '#409EFF',
  remark: '',
  status: 1
})

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  address: [{ required: true, message: '请输入家庭地址', trigger: 'blur' }]
}

const statusOptions = [
  { label: '在读', value: 1 },
  { label: '暂停', value: 0 }
]

async function loadGrades(): Promise<void> {
  try {
    gradeOptions.value = await fetchDicts('grade')
  } catch (e) {
    gradeOptions.value = []
    ElMessage.error(errMsg(e))
  }
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const res = await fetchStudents({ ...query })
    list.value = res.list
    total.value = res.total
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.page = 1
  loadList()
}

function onReset(): void {
  query.keyword = ''
  query.grade = ''
  query.status = undefined
  query.page = 1
  loadList()
}

function openAdd(): void {
  dialogMode.value = 'add'
  Object.assign(form, {
    id: undefined,
    name: '',
    grade: gradeOptions.value[0]?.dictValue || '',
    phone: '',
    parentWechat: '',
    address: '',
    price: 0,
    color: autoColor(list.value.map((s) => s.color)),
    remark: '',
    status: 1
  })
  dialogVisible.value = true
}

function openEdit(row: Student): void {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    name: row.name,
    grade: row.grade,
    phone: row.phone,
    parentWechat: row.parentWechat,
    address: row.address,
    price: row.price,
    color: row.color,
    remark: row.remark,
    status: row.status
  })
  dialogVisible.value = true
}

async function onSubmit(): Promise<void> {
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    submitting.value = true
    try {
      if (dialogMode.value === 'add') {
        await createStudent({ ...form })
        ElMessage.success('新增成功')
      } else {
        await updateStudent(form.id as number, { ...form })
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

async function onDelete(row: Student): Promise<void> {
  try {
    await deleteStudent(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    if (isConflict(e)) {
      ElMessageBox.alert(
        '该学生存在未结课程或未来排课，请先改为「暂停」归档后再删除。',
        '无法删除',
        { type: 'warning', confirmButtonText: '我知道了' }
      )
      return
    }
    ElMessage.error(errMsg(e))
  }
}

async function onToggleStatus(row: Student, val: number): Promise<void> {
  try {
    await updateStudent(row.id, { status: val as StudentStatus })
    ElMessage.success('状态已更新')
  } catch (e) {
    ElMessage.error(errMsg(e))
    loadList()
  }
}

function onPageChange(page: number): void {
  query.page = page
  loadList()
}

function onSizeChange(size: number): void {
  query.size = size
  query.page = 1
  loadList()
}

onMounted(() => {
  loadGrades()
  loadList()
})
</script>

<template>
  <el-card>
    <!-- 工具条：左搜索、右主操作（不再单占一行标题） -->
    <div class="toolbar">
      <el-form :inline="true" class="search-bar">
        <el-form-item label="姓名/电话">
          <el-input
            v-model="query.keyword"
            placeholder="姓名或电话"
            clearable
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="query.grade" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="g in gradeOptions"
              :key="g.id"
              :label="g.dictValue"
              :value="g.dictValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">搜索</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-button type="primary" class="add-btn" @click="openAdd">
        <el-icon><Plus /></el-icon>
        <span>新增学生</span>
      </el-button>
    </div>

    <!-- 列表：去掉竖线与斑马纹，靠行高与 hover 区分 -->
    <el-table v-loading="loading" :data="list" empty-text="暂无学生">
      <el-table-column prop="name" label="姓名" min-width="100">
        <template #default="{ row }">
          <span class="cell-name">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="grade" label="年级" min-width="90" />
      <el-table-column prop="phone" label="电话" min-width="130" />
      <el-table-column prop="parentWechat" label="家长微信" min-width="120" />
      <el-table-column prop="address" label="家庭地址" min-width="200" show-overflow-tooltip />
      <el-table-column prop="price" label="课程价格" min-width="100" align="right">
        <template #default="{ row }">
          <span class="price">¥{{ row.price }}</span>
        </template>
      </el-table-column>
      <el-table-column label="专属颜色" min-width="90" align="center">
        <template #default="{ row }">
          <span class="color-dot" :style="{ background: row.color }" />
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="110" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            active-text="在读"
            inactive-text="暂停"
            inline-prompt
            :width="56"
            @change="(val: boolean | string | number) => onToggleStatus(row, val ? 1 : 0)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" min-width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      :page-size="query.size"
      :current-page="query.page"
      :page-sizes="[10, 20, 50]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />

    <!-- 新增/编辑 对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? '新增学生' : '编辑学生'"
      width="560px"
      @closed="formRef?.clearValidate?.()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="必填" />
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="form.grade" placeholder="请选择" style="width: 100%">
            <el-option v-for="g in gradeOptions" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="家长微信">
          <el-input v-model="form.parentWechat" />
        </el-form-item>
        <el-form-item label="家庭地址" prop="address">
          <el-input v-model="form.address" placeholder="必填" />
        </el-form-item>
        <el-form-item label="课程价格">
          <el-input-number v-model="form.price" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="专属颜色">
          <el-color-picker v-model="form.color" />
          <el-button link type="primary" style="margin-left: 8px" @click="form.color = autoColor(list.map((s) => s.color))">
            自动分配
          </el-button>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
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
/* 工具条：搜索在左、主操作在右，压成一行（不再单占一行卡片标题） */
.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.search-bar {
  flex: 1;
  min-width: 0;
}
.search-bar :deep(.el-form-item) {
  margin-right: 12px;
  margin-bottom: 8px;
}
.add-btn {
  margin-bottom: 8px;
}

/* 列表 */
.cell-name {
  font-weight: 500;
  color: var(--text-strong);
}
.price {
  font-weight: 600;
  color: var(--text-strong);
}
.color-dot {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: var(--radius-xs);
  /* 双层描边，比实线边框更精致 */
  box-shadow: 0 0 0 2px #fff, 0 0 0 3px var(--border-base);
  vertical-align: middle;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
