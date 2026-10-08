<script setup lang="ts">
// S09 今日视图（落地页）—— Wave 4 实现
// 今日课程列表（el-table：学生/时段/状态）+ 一键标记（el-dialog：正常/顺延[必填请假方+原因]）；
// 下方「今日待补课」列表，每条「已安排进本月课程」按钮（调 close 接口 BE-API-25）。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { LessonCell, LessonStatus, AbsentBy } from '@/types'
import { fetchToday, markAbsent, changeLessonStatus } from '@/api/lesson'
import { closeMakeUp } from '@/api/makeUp'
import { STATUS_META, ABSENT_BY_LABEL } from '@/constants/status'
import { errMsg } from '@/utils/error'

const todayStr = (() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})()

type ViewState = 'loading' | 'empty' | 'error' | 'populated'
const viewState = ref<ViewState>('loading')
const errorMessage = ref('')
const todayLessons = ref<LessonCell[]>([])
const pendingToday = ref<LessonCell[]>([])

const cardState = computed<ViewState>(() => {
  if (viewState.value === 'loading' || viewState.value === 'error') return viewState.value
  if (todayLessons.value.length === 0) return 'empty'
  return 'populated'
})

// ===================== 标记弹窗 =====================
const dialogVisible = ref(false)
const dialogForm = reactive({
  lessonId: 0 as number,
  mark: 'NORMAL' as Exclude<LessonStatus, 'UNTAKEN' | 'MADEUP' | 'CANCELLED'>,
  absentBy: 'student' as AbsentBy,
  reason: ''
})

function openMark(row: LessonCell): void {
  dialogForm.lessonId = row.id
  dialogForm.mark = row.status === 'ABSENT' ? 'ABSENT' : 'NORMAL'
  dialogForm.absentBy = row.absentBy ?? 'student'
  dialogForm.reason = row.absentReason ?? ''
  dialogVisible.value = true
}

async function submitMark(): Promise<void> {
  const row = todayLessons.value.find((l) => l.id === dialogForm.lessonId)
  if (!row) return
  try {
    if (dialogForm.mark === 'ABSENT') {
      if (!dialogForm.reason.trim()) {
        ElMessage.warning('顺延时必须填写原因')
        return
      }
      await markAbsent(row.id, {
        absentBy: dialogForm.absentBy,
        absentReason: dialogForm.reason.trim()
      })
      row.status = 'ABSENT'
      row.absentBy = dialogForm.absentBy
      row.absentReason = dialogForm.reason.trim()
    } else {
      await changeLessonStatus(row.id, { status: 'NORMAL' })
      row.status = 'NORMAL'
      row.absentBy = null
      row.absentReason = null
    }
    dialogVisible.value = false
    ElMessage.success('标记已保存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

// 今日待补课「已安排进本月课程」关闭（BE-API-25）
async function closePending(item: LessonCell): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认将 ${item.studentName} 的待补课（原 ${item.lessonDate}）标记为「已安排进本月课程」并关闭？`,
      '关闭待补',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await closeMakeUp(item.id)
    pendingToday.value = pendingToday.value.filter((p) => p.id !== item.id)
    ElMessage.success('待补课已关闭')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function loadAll(): Promise<void> {
  viewState.value = 'loading'
  errorMessage.value = ''
  try {
    const res = await fetchToday()
    todayLessons.value = res.lessons
    pendingToday.value = res.pendingToday
    viewState.value = 'populated'
  } catch (err) {
    errorMessage.value = errMsg(err)
    viewState.value = 'error'
  }
}

onMounted(loadAll)
</script>

<template>
  <div>
    <div class="page-head">今日 · {{ todayStr }}</div>

    <!-- loading -->
    <el-card v-if="cardState === 'loading'" shadow="never">
      <el-skeleton :rows="6" animated />
    </el-card>

    <!-- error -->
    <el-result
      v-else-if="cardState === 'error'"
      icon="error"
      title="加载失败"
      :sub-title="errorMessage"
    >
      <template #extra>
        <el-button type="primary" @click="loadAll">重试</el-button>
      </template>
    </el-result>

    <template v-else>
      <!-- 今日该补的待补课 -->
      <el-card shadow="never" style="margin-bottom: 12px" header="今日该补 · 待补课">
        <el-empty v-if="pendingToday.length === 0" description="今日无待补课，状态良好" :image-size="50" />
        <el-table v-else :data="pendingToday" size="small">
          <el-table-column prop="studentName" label="学生" width="120" />
          <el-table-column prop="lessonDate" label="原顺延日期" width="140" />
          <el-table-column label="请假方" width="110">
            <template #default="{ row }: { row: LessonCell }">
              <span v-if="row.absentBy">{{ ABSENT_BY_LABEL[row.absentBy as AbsentBy] }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="absentReason" label="原因" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }: { row: LessonCell }">
              <el-button size="small" type="success" @click="closePending(row)">已安排进本月课程</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 今天课程列表 -->
      <el-card shadow="never" header="今天课程">
        <el-empty v-if="cardState === 'empty'" description="今天暂无排课" />
        <el-table v-else :data="todayLessons" size="small">
          <el-table-column label="时段" width="140">
            <template #default="{ row }: { row: LessonCell }">
              <el-tag size="small" effect="plain">{{ row.slotLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="studentName" label="学生" min-width="120" />
          <el-table-column label="状态" width="130">
            <template #default="{ row }: { row: LessonCell }">
              <el-tag :type="STATUS_META[row.status].tagType" size="small">
                {{ STATUS_META[row.status].label }}
              </el-tag>
              <div v-if="row.status === 'ABSENT'" class="absent-note">
                {{ row.absentBy ? ABSENT_BY_LABEL[row.absentBy as AbsentBy] : '' }}：{{ row.absentReason }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="一键标记" width="220">
            <template #default="{ row }: { row: LessonCell }">
              <el-button size="small" type="success" @click="openMark(row)">标记</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 标记弹窗 -->
    <el-dialog v-model="dialogVisible" title="标记课程状态" width="420px">
      <el-form :model="dialogForm" label-width="80px">
        <el-form-item label="状态">
          <el-radio-group v-model="dialogForm.mark">
            <el-radio value="NORMAL">正常上课</el-radio>
            <el-radio value="ABSENT">顺延</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="dialogForm.mark === 'ABSENT'">
          <el-form-item label="请假方">
            <el-select v-model="dialogForm.absentBy" style="width: 100%">
              <el-option label="学生请假" value="student" />
              <el-option label="老师请假" value="teacher" />
            </el-select>
          </el-form-item>
          <el-form-item label="原因" required>
            <el-input
              v-model="dialogForm.reason"
              type="textarea"
              :rows="2"
              placeholder="必填：顺延原因"
            />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMark">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-muted);
}
.absent-note {
  margin-top: 2px;
  font-size: 11px;
  color: var(--danger);
}
</style>
