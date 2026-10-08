<script setup lang="ts">
// S03 课程管理 —— 按学生分组展示语文课配置（价格/备注/启用），建课/改备注/停用
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchStudents } from '@/api/student'
import { fetchStudentCourse, createCourse, updateCourse, disableCourse, enableCourse } from '@/api/course'
import type { Student, Course } from '@/types'
import { isConflict, errMsg } from '@/utils/error'

const router = useRouter()

interface CourseRow {
  student: Student
  course: Course | null
}

const loading = ref(false)
const rows = ref<CourseRow[]>([])

/** 已建课人数（未建课的行只能「建课」） */
const builtCount = computed(() => rows.value.filter((r) => r.course).length)

/** 跳「排课记录」看逐节课（本页只维护课程配置） */
function goRecords(): void {
  router.push('/dashboard/records')
}

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
      await enableCourse(row.course.id)
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
    <div class="toolbar">
      <div class="hint">
        每生一门语文课，价格自动取自学生单价；本页是课程配置，逐节课请看
        <el-button link type="primary" class="link" @click="goRecords">排课记录</el-button>
      </div>
      <span class="count">共 {{ rows.length }} 名学生 · 已建课 {{ builtCount }} 人</span>
    </div>

    <el-table v-loading="loading" :data="rows" empty-text="暂无学生">
      <el-table-column label="学生" min-width="110">
        <template #default="{ row }">
          <span class="sname">{{ row.student.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="年级" min-width="90">
        <template #default="{ row }">
          <span v-if="row.student.grade">{{ row.student.grade }}</span>
          <span v-else class="muted">未填</span>
        </template>
      </el-table-column>
      <el-table-column label="科目" min-width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.course">语文</span>
          <el-tag v-else size="small" type="info" effect="plain">未建课</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="价格" min-width="100" align="right">
        <template #default="{ row }">
          <span class="price">¥{{ row.course ? row.course.price : row.student.price }}</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.course ? row.course.remark || '—' : '—' }}</template>
      </el-table-column>
      <el-table-column label="启用" min-width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-if="row.course"
            :model-value="row.course.enabled"
            :active-value="1"
            :inactive-value="0"
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
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.hint {
  font-size: 13px;
  color: var(--text-muted);
}
.hint .link {
  vertical-align: baseline;
  padding: 0 2px;
}
.count {
  font-size: 13px;
  color: var(--text-muted);
}
.sname {
  font-weight: 500;
}
.muted {
  color: var(--text-muted);
}
.price {
  font-weight: 600;
  color: var(--text-strong);
  font-variant-numeric: tabular-nums;
}
</style>
