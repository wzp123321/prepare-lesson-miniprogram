<script setup lang="ts">
// S03 课程管理 —— 按学生分组展示语文课配置（价格/备注/启用），建课/改备注/停用
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchStudents } from '@/api/student'
import { fetchStudentCourse, createCourse, updateCourse, disableCourse } from '@/api/course'
import type { Student, Course } from '@/types'
import { isConflict, errMsg } from '@/api/mockData'

interface CourseRow {
  student: Student
  course: Course | null
}

const loading = ref(false)
const rows = ref<CourseRow[]>([])

const remarkDialog = ref(false)
const remarkTarget = ref<CourseRow | null>(null)
const remarkText = ref('')
const savingRemark = ref(false)

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const res = await fetchStudents({ page: 1, size: 1000 })
    const built: CourseRow[] = await Promise.all(
      res.list.map(async (student) => {
        const course = await fetchStudentCourse(student.id)
        return { student, course }
      })
    )
    rows.value = built
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

async function onBuild(row: CourseRow): Promise<void> {
  try {
    await createCourse(row.student.id, { remark: '' })
    ElMessage.success('建课成功（价格已自动取自学生单价）')
    loadList()
  } catch (e) {
    if (isConflict(e)) {
      ElMessage.warning(errMsg(e) || '该生已建语文课，不可重复建课')
      return
    }
    ElMessage.error(errMsg(e))
  }
}

function openRemark(row: CourseRow): void {
  remarkTarget.value = row
  remarkText.value = row.course?.remark || ''
  remarkDialog.value = true
}

async function saveRemark(): Promise<void> {
  if (!remarkTarget.value?.course) return
  savingRemark.value = true
  try {
    await updateCourse(remarkTarget.value.course.id, { remark: remarkText.value })
    ElMessage.success('备注已保存')
    remarkDialog.value = false
    loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    savingRemark.value = false
  }
}

async function onDisable(row: CourseRow): Promise<void> {
  if (!row.course) return
  try {
    await ElMessageBox.confirm('确认停用该生语文课？停用后保留历史记录。', '停用确认', {
      type: 'warning',
      confirmButtonText: '确认停用',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await disableCourse(row.course.id)
    ElMessage.success('已停用')
    loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

async function onToggleEnabled(row: CourseRow, val: boolean | string | number): Promise<void> {
  if (!row.course) return
  try {
    if (val) {
      await updateCourse(row.course.id, { remark: row.course.remark })
      ElMessage.success('已启用')
    } else {
      await onDisable(row)
      return
    }
    loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
    loadList()
  }
}

onMounted(loadList)
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>课程管理</span>
        <span class="hint">每生一门语文课，价格自动取自学生单价</span>
      </div>
    </template>

    <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无学生">
      <el-table-column prop="student.name" label="学生" min-width="100" />
      <el-table-column prop="student.grade" label="年级" min-width="90" />
      <el-table-column label="科目" min-width="90" align="center">
        <template #default>语文</template>
      </el-table-column>
      <el-table-column label="价格" min-width="100" align="right">
        <template #default="{ row }">¥{{ row.course ? row.course.price : row.student.price }}</template>
      </el-table-column>
      <el-table-column label="备注" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.course ? row.course.remark || '—' : '—' }}</template>
      </el-table-column>
      <el-table-column label="启用" min-width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-if="row.course"
            :model-value="row.course.enabled"
            @change="(val: boolean | string | number) => onToggleEnabled(row, val)"
          />
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="!row.course">
            <el-button link type="primary" @click="onBuild(row)">建课</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" @click="openRemark(row)">编辑备注</el-button>
            <el-button link type="danger" @click="onDisable(row)">停用</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="remarkDialog" title="编辑课程备注" width="460px">
      <el-input v-model="remarkText" type="textarea" :rows="3" placeholder="课程备注" />
      <template #footer>
        <el-button @click="remarkDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingRemark" @click="saveRemark">保存</el-button>
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
.hint {
  font-size: 12px;
  color: #909399;
}
</style>
