<script setup lang="ts">
// S06 待补课首页 —— Wave 4 实现 + 补课闭环补全
// 调 BE-API-23 拉全局待补（status=ABSENT AND closed=0）；无则 el-empty「当前无待补课」；
// 每条可二选一终结补课：
//  - 「标记已补」(BE-API-24 arrangeMakeUp，写补课日期 + 置 MADEUP + 关单) —— 完成态，计入已补 Z / MADEUP 收入 / 今日该补
//  - 「已安排进本月课程」(BE-API-25 手动关闭，仅置 closed=1，status 保持 ABSENT)
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { LessonCell, AbsentBy } from '@/types'
import { fetchPendingMakeUp, closeMakeUp, arrangeMakeUp } from '@/api/makeUp'
import { ABSENT_BY_LABEL } from '@/constants/status'
import { errMsg } from '@/api/mockData'

const loading = ref(false)
const errorMsg = ref('')
const pending = ref<LessonCell[]>([])

const hasData = computed(() => pending.value.length > 0)

// 「标记已补」弹窗（BE-API-24）
const makeUpDialog = ref(false)
const makeUpForm = reactive({ id: 0, studentName: '', lessonDate: '', makeUpDate: '' })

async function load(): Promise<void> {
  loading.value = true
  errorMsg.value = ''
  try {
    pending.value = await fetchPendingMakeUp()
  } catch (err) {
    errorMsg.value = errMsg(err)
  } finally {
    loading.value = false
  }
}

async function onClose(item: LessonCell): Promise<void> {
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
    pending.value = pending.value.filter((p) => p.id !== item.id)
    ElMessage.success('待补课已关闭')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

function openMakeUp(item: LessonCell): void {
  makeUpForm.id = item.id
  makeUpForm.studentName = item.studentName
  makeUpForm.lessonDate = item.lessonDate
  makeUpForm.makeUpDate = ''
  makeUpDialog.value = true
}

async function submitMakeUp(): Promise<void> {
  if (!makeUpForm.makeUpDate) {
    ElMessage.warning('请选择补课日期')
    return
  }
  try {
    await arrangeMakeUp(makeUpForm.id, { makeUpDate: makeUpForm.makeUpDate })
    pending.value = pending.value.filter((p) => p.id !== makeUpForm.id)
    makeUpDialog.value = false
    ElMessage.success('已标记为「已补」')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never" header="待补课">
    <!-- loading -->
    <el-skeleton v-if="loading" :rows="6" animated />
    <!-- error -->
    <el-result v-else-if="errorMsg" icon="error" title="加载失败" :sub-title="errorMsg">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>
    <!-- 无数据 -->
    <el-empty v-else-if="!hasData" description="当前无待补课" :image-size="80" />
    <!-- 列表 -->
    <el-table v-else :data="pending" border size="small">
      <el-table-column prop="studentName" label="学生" width="120" />
      <el-table-column prop="lessonDate" label="原顺延日期" width="140" />
      <el-table-column label="时段" width="130">
        <template #default="{ row }: { row: LessonCell }">
          <el-tag size="small" effect="plain">{{ row.slotLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="请假方" width="110">
        <template #default="{ row }: { row: LessonCell }">
          <span v-if="row.absentBy">{{ ABSENT_BY_LABEL[row.absentBy as AbsentBy] }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="absentReason" label="原因" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }: { row: LessonCell }">
          <el-button size="small" type="success" @click="openMakeUp(row)">标记已补</el-button>
          <el-button size="small" @click="onClose(row)">已安排进本月课程</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <!-- 标记已补弹窗（BE-API-24） -->
  <el-dialog v-model="makeUpDialog" title="标记已补" width="420px">
    <el-descriptions :column="1" size="small" border>
      <el-descriptions-item label="学生">{{ makeUpForm.studentName }}</el-descriptions-item>
      <el-descriptions-item label="原顺延日期">{{ makeUpForm.lessonDate }}</el-descriptions-item>
    </el-descriptions>
    <el-form label-width="80px" style="margin-top: 12px">
      <el-form-item label="补课日期" required>
        <el-date-picker
          v-model="makeUpForm.makeUpDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择补课日期"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="makeUpDialog = false">取消</el-button>
      <el-button type="primary" @click="submitMakeUp">确认已补</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.el-card {
  border-radius: 8px;
}
</style>
