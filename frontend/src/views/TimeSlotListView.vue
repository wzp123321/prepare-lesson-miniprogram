<script setup lang="ts">
// S02 时间段管理 —— 列表 + 排序 + 启用开关 + 表单(el-time-picker) + 重叠预校验 + 删除引用确认
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchTimeSlots,
  createTimeSlot,
  updateTimeSlot,
  deleteTimeSlot,
  sortTimeSlots
} from '@/api/timeSlot'
import type { TimeSlot } from '@/types'
import { isConflict, errMsg } from '@/api/mockData'

interface SlotForm {
  id: number | undefined
  startTime: string
  endTime: string
  sortOrder: number
  enabled: boolean
}

const loading = ref(false)
const list = ref<TimeSlot[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const submitting = ref(false)
const formRef = ref()

const form = reactive<SlotForm>({
  id: undefined,
  startTime: '09:00:00',
  endTime: '10:30:00',
  sortOrder: 1,
  enabled: true
})

const rules = {
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

function toMinutes(t: string): number {
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}

function overlaps(aStart: string, aEnd: string, bStart: string, bEnd: string): boolean {
  return toMinutes(aStart) < toMinutes(bEnd) && toMinutes(bStart) < toMinutes(aEnd)
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    list.value = await fetchTimeSlots()
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
    startTime: '09:00:00',
    endTime: '10:30:00',
    sortOrder: list.value.length + 1,
    enabled: true
  })
  dialogVisible.value = true
}

function openEdit(row: TimeSlot): void {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    startTime: row.startTime,
    endTime: row.endTime,
    sortOrder: row.sortOrder,
    enabled: row.enabled
  })
  dialogVisible.value = true
}

// 前端重叠预校验：与已有时段（排除自身）区间交叉则拦截
function checkOverlap(): boolean {
  const conflict = list.value.find(
    (s) => s.id !== form.id && overlaps(form.startTime, form.endTime, s.startTime, s.endTime)
  )
  if (conflict) {
    ElMessage.warning(
      `时间段重叠：与 ${conflict.startTime}~${conflict.endTime} 冲突，请调整起止时间`
    )
    return true
  }
  return false
}

async function onSubmit(): Promise<void> {
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    if (checkOverlap()) return
    submitting.value = true
    try {
      if (dialogMode.value === 'add') {
        await createTimeSlot({ ...form })
        ElMessage.success('新增成功')
      } else {
        await updateTimeSlot(form.id as number, { ...form })
        ElMessage.success('修改成功')
      }
      dialogVisible.value = false
      loadList()
    } catch (e) {
      if (isConflict(e)) {
        ElMessage.warning(errMsg(e) || '时间段重叠，请调整起止时间')
        return
      }
      ElMessage.error(errMsg(e))
    } finally {
      submitting.value = false
    }
  })
}

async function onToggleEnabled(row: TimeSlot, val: boolean | string | number): Promise<void> {
  try {
    await updateTimeSlot(row.id, { enabled: Boolean(val) })
    ElMessage.success('启用状态已更新')
  } catch (e) {
    ElMessage.error(errMsg(e))
    loadList()
  }
}

// 排序调整：与相邻项交换 sortOrder 并提交 BE-API-10
async function move(index: number, dir: -1 | 1): Promise<void> {
  const target = list.value[index + dir]
  if (!target) return
  const a = list.value[index]
  const b = target
  const aOrder = a.sortOrder
  const bOrder = b.sortOrder
  a.sortOrder = bOrder
  b.sortOrder = aOrder
  try {
    await sortTimeSlots([
      { id: a.id, sortOrder: a.sortOrder },
      { id: b.id, sortOrder: b.sortOrder }
    ])
    ElMessage.success('排序已保存')
    loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
    loadList()
  }
}

async function onDelete(row: TimeSlot): Promise<void> {
  try {
    await deleteTimeSlot(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    if (isConflict(e)) {
      const refCount = (e as { response?: { data?: { referenceCount?: number } } })?.response?.data
        ?.referenceCount
      const msg = refCount
        ? `该时间段已被 ${refCount} 条排课引用，删除后将一并解除引用。确认删除？`
        : '该时间段已被排课引用，确认删除？'
      try {
        await ElMessageBox.confirm(msg, '删除确认', {
          type: 'warning',
          confirmButtonText: '确认删除',
          cancelButtonText: '取消'
        })
        await deleteTimeSlot(row.id)
        ElMessage.success('已删除')
        loadList()
      } catch (err) {
        // 用户取消或二次删除失败
        if (!isConflict(err)) ElMessage.error(errMsg(err))
      }
      return
    }
    ElMessage.error(errMsg(e))
  }
}

onMounted(loadList)
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>时间段管理</span>
        <el-button type="primary" @click="openAdd">新增时间段</el-button>
      </div>
    </template>

    <el-table v-loading="loading" :data="list" border stripe empty-text="暂无时间段">
      <el-table-column label="排序" min-width="160" align="center">
        <template #default="{ $index }">
          <el-button
            link
            type="primary"
            :disabled="$index === 0"
            @click="move($index, -1)"
          >↑ 上移</el-button>
          <el-button
            link
            type="primary"
            :disabled="$index === list.length - 1"
            @click="move($index, 1)"
          >↓ 下移</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" min-width="120" align="center" />
      <el-table-column prop="endTime" label="结束时间" min-width="120" align="center" />
      <el-table-column prop="sortOrder" label="排序值" min-width="90" align="center" />
      <el-table-column label="启用" min-width="100" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            @change="(val: boolean | string | number) => onToggleEnabled(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? '新增时间段' : '编辑时间段'"
      width="480px"
      @closed="formRef?.clearValidate?.()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="开始时间" prop="startTime">
          <el-time-picker
            v-model="form.startTime"
            value-format="HH:mm:ss"
            format="HH:mm"
            placeholder="选择开始时间"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-time-picker
            v-model="form.endTime"
            value-format="HH:mm:ss"
            format="HH:mm"
            placeholder="选择结束时间"
          />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sortOrder" :min="1" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
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
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
