<script setup lang="ts">
// 备课 · 待备课（BE-L-01~04）：左课次列表 + 右备课面板（勾知识点 / 挑卷 / 备注）
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchTodoPrep, fetchLessonPrep, saveLessonPrep, fetchSuggestQuestions } from '@/api/prep'
import { fetchKps } from '@/api/kp'
import { fetchPapers } from '@/api/paper'
import type { KnowledgePoint, Paper, Question, TodoPrep } from '@/types'
import { errMsg } from '@/utils/error'

const STATUS_LABEL: Record<string, string> = {
  UNTAKEN: '未上',
  NORMAL: '已上',
  ABSENT: '顺延',
  MADEUP: '已补',
  CANCELLED: '作废'
}

const loading = ref(false)
const list = ref<TodoPrep[]>([])
const kps = ref<KnowledgePoint[]>([])
const papers = ref<Paper[]>([])

const rangeMode = ref<'today' | 'week' | 'custom'>('week')
const customRange = ref<[string, string] | null>(null)

const selected = ref<TodoPrep | null>(null)
const form = reactive({ kpIds: [] as number[], paperIds: [] as number[], remark: '' })
const saving = ref(false)

const suggestVisible = ref(false)
const suggestLoading = ref(false)
const suggestList = ref<Question[]>([])

function fmt(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 当前查询区间 */
const range = computed<[string, string]>(() => {
  const now = new Date()
  if (rangeMode.value === 'today') {
    const t = fmt(now)
    return [t, t]
  }
  if (rangeMode.value === 'week') {
    const dow = now.getDay() === 0 ? 7 : now.getDay()
    const start = new Date(now)
    start.setDate(now.getDate() - (dow - 1))
    const end = new Date(start)
    end.setDate(start.getDate() + 6)
    return [fmt(start), fmt(end)]
  }
  return customRange.value || [fmt(now), fmt(now)]
})

const pendingCount = computed(() => list.value.filter((x) => !x.prepared).length)
const preparedCount = computed(() => list.value.filter((x) => x.prepared).length)

/** 按学生年级过滤知识点（未填年级则全部） */
const kpOptions = computed(() => {
  const g = selected.value?.grade
  return kps.value.filter((k) => k.enabled === 1 && (!g || !k.grade || k.grade === g))
})

/** 试卷：年级匹配优先，其次通用卷 */
const paperOptions = computed(() => {
  const g = selected.value?.grade
  const arr = [...papers.value]
  arr.sort((a, b) => {
    const score = (p: Paper): number => {
      if (g && p.grade === g) return 0
      if (!p.grade) return 1
      return 2
    }
    return score(a) - score(b)
  })
  return arr
})

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const [from, to] = range.value
    list.value = await fetchTodoPrep({ from, to })
    if (selected.value) {
      const still = list.value.find((x) => x.lessonId === selected.value?.lessonId)
      if (still) await selectRow(still)
      else selected.value = null
    }
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

async function loadRefs(): Promise<void> {
  try {
    kps.value = await fetchKps({ enabled: 1 })
  } catch {
    kps.value = []
  }
  try {
    papers.value = await fetchPapers({})
  } catch {
    papers.value = []
  }
}

async function selectRow(row: TodoPrep): Promise<void> {
  selected.value = row
  suggestVisible.value = false
  suggestList.value = []
  try {
    const detail = await fetchLessonPrep(row.lessonId)
    form.kpIds = [...detail.kpIds]
    form.paperIds = [...detail.paperIds]
    form.remark = detail.remark || ''
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

function toggleKp(id: number): void {
  if (form.kpIds.includes(id)) form.kpIds = form.kpIds.filter((x) => x !== id)
  else form.kpIds.push(id)
}

function togglePaper(id: number): void {
  if (form.paperIds.includes(id)) form.paperIds = form.paperIds.filter((x) => x !== id)
  else form.paperIds.push(id)
}

async function onSave(): Promise<void> {
  if (!selected.value) return
  saving.value = true
  try {
    await saveLessonPrep({
      lessonId: selected.value.lessonId,
      kpIds: form.kpIds,
      paperIds: form.paperIds,
      remark: form.remark
    })
    ElMessage.success('已保存备课')
    await loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    saving.value = false
  }
}

/** 按已选知识点，看题库里有哪些可用题（跨卷取题） */
async function onSuggest(): Promise<void> {
  if (!selected.value) return
  if (!form.kpIds.length) {
    ElMessage.warning('先勾选至少一个知识点')
    return
  }
  suggestVisible.value = true
  suggestLoading.value = true
  try {
    suggestList.value = await fetchSuggestQuestions(selected.value.lessonId)
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    suggestLoading.value = false
  }
}

function timeLabel(row: TodoPrep): string {
  if (row.slotStart && row.slotEnd) return `${row.slotStart}-${row.slotEnd}`
  return row.slotId ? `时段 ${row.slotId}` : '时段未定'
}

onMounted(() => {
  loadRefs()
  loadList()
})
</script>

<template>
  <div class="prep-layout">
    <div class="left">
      <el-card shadow="never">
        <template #header>
          <div class="left-head">
            <el-radio-group v-model="rangeMode" size="small" @change="loadList">
              <el-radio-button value="today">今日</el-radio-button>
              <el-radio-button value="week">本周</el-radio-button>
              <el-radio-button value="custom">自定义</el-radio-button>
            </el-radio-group>
            <el-date-picker
              v-if="rangeMode === 'custom'"
              v-model="customRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              size="small"
              style="width: 240px"
              @change="loadList"
            />
          </div>
        </template>
        <div class="stat-row">
          <span class="stat">待备 <b class="warn">{{ pendingCount }}</b> 节</span>
          <span class="stat">已备 <b class="ok">{{ preparedCount }}</b> 节</span>
        </div>
        <div v-loading="loading" class="cards">
          <el-empty v-if="!list.length" description="该区间没有课次" :image-size="60" />
          <div
            v-for="row in list"
            :key="row.lessonId"
            class="card"
            :class="{ active: selected?.lessonId === row.lessonId }"
            @click="selectRow(row)"
          >
            <div class="card-top">
              <span class="card-time">{{ row.lessonDate }} {{ timeLabel(row) }}</span>
              <span class="card-stu">{{ row.studentName || '未指派' }}</span>
            </div>
            <div class="card-tags">
              <template v-if="row.prepared">
                <el-tag v-for="n in row.kpNames" :key="n" size="small" effect="plain">{{ n }}</el-tag>
                <span class="card-paper">{{ row.paperTitles.length }} 份卷</span>
              </template>
              <el-tag v-else size="small" type="warning">未备课</el-tag>
              <span v-if="row.lessonStatus !== 'UNTAKEN'" class="card-status">
                {{ STATUS_LABEL[row.lessonStatus] || row.lessonStatus }}
              </span>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <div class="right">
      <el-card shadow="never" class="panel">
        <template #header>
          <div class="panel-head">
            <span v-if="selected">
              备课 · {{ selected.lessonDate }} {{ timeLabel(selected) }} · {{ selected.studentName }}
              <span v-if="selected.grade" class="muted"> · {{ selected.grade }}</span>
            </span>
            <span v-else class="muted">从左侧选择一节课开始备课</span>
          </div>
        </template>

        <el-empty v-if="!selected" description="选择左侧课次" :image-size="60" />
        <template v-else>
          <p class="label">本节讲的知识点（可多选）</p>
          <div class="kp-tags">
            <span
              v-for="k in kpOptions"
              :key="k.id"
              class="kp-tag"
              :class="{ on: form.kpIds.includes(k.id), empty: k.questionCount === 0 }"
              @click="toggleKp(k.id)"
            >
              {{ k.name }}
              <span class="kp-num">{{ k.questionCount }}</span>
            </span>
            <span v-if="!kpOptions.length" class="muted">该年级暂无知识点，先去「知识点」页新增</span>
          </div>

          <p class="label">使用的试卷（可多选）</p>
          <div class="paper-list">
            <el-empty v-if="!paperOptions.length" description="还没有试卷，去「试卷」页一键组卷" :image-size="50" />
            <label
              v-for="p in paperOptions"
              :key="p.id"
              class="paper-item"
              :class="{ on: form.paperIds.includes(p.id) }"
              @click="togglePaper(p.id)"
            >
              <el-checkbox :model-value="form.paperIds.includes(p.id)" />
              <span class="paper-title">{{ p.title }}</span>
              <el-tag v-if="p.studentName" size="small" type="warning" effect="plain">专属</el-tag>
              <span class="paper-count">{{ p.questionCount }} 题</span>
            </label>
          </div>

          <p class="label">备注</p>
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="这节课的重点提示（选填）" />

          <div class="panel-actions">
            <el-button link type="primary" @click="onSuggest">看可用题目</el-button>
            <el-button type="primary" :loading="saving" @click="onSave">保存备课</el-button>
          </div>

          <div v-if="suggestVisible" class="suggest">
            <div class="suggest-head">已选知识点下的可用题目（共 {{ suggestList.length }} 题）</div>
            <div v-loading="suggestLoading" class="suggest-body">
              <el-empty v-if="!suggestList.length" description="这些知识点下还没有题" :image-size="50" />
              <div v-for="q in suggestList" :key="q.id" class="suggest-item">
                <el-tag size="small" effect="plain">{{ q.kpName }}</el-tag>
                <span class="suggest-stem">{{ q.stem.split('\n')[0] }}</span>
              </div>
            </div>
          </div>
        </template>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.prep-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 16px;
  height: 100%;
}
.left,
.right {
  min-height: 0;
}
.left :deep(.el-card),
.right :deep(.el-card) {
  height: 100%;
}
.left :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.right :deep(.el-card__body) {
  overflow: auto;
}
.left-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.stat-row {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-muted);
}
.stat b.warn {
  color: var(--warning);
}
.stat b.ok {
  color: var(--success);
}
.cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.card {
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.18s;
}
.card:hover {
  border-color: var(--brand-200);
}
.card.active {
  border-color: var(--brand-500);
  background: var(--brand-50);
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-time {
  font-weight: 500;
  font-size: 13px;
}
.card-stu {
  font-size: 12px;
  color: var(--text-muted);
}
.card-tags {
  display: flex;
  gap: 4px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 6px;
}
.card-paper,
.card-status {
  font-size: 12px;
  color: var(--text-muted);
}
.panel {
  min-height: 0;
}
.panel-head {
  font-size: 14px;
  font-weight: 500;
}
.label {
  font-size: 12px;
  color: var(--text-muted);
  margin: 14px 0 6px;
}
.label:first-of-type {
  margin-top: 0;
}
.kp-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.kp-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  border: 1px solid var(--border-base);
  cursor: pointer;
  transition: all 0.15s;
}
.kp-tag:hover {
  border-color: var(--brand-500);
}
.kp-tag.on {
  background: var(--brand-50);
  border-color: var(--brand-500);
  color: var(--brand-500);
}
.kp-tag.empty {
  opacity: 0.65;
}
.kp-num {
  font-size: 11px;
  color: var(--text-faint);
}
.paper-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
}
.paper-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid var(--border-subtle);
  cursor: pointer;
}
.paper-item.on {
  background: var(--surface-sunken);
  border-color: var(--brand-200);
}
.paper-title {
  font-size: 13px;
}
.paper-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-muted);
}
.panel-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}
.suggest {
  margin-top: 14px;
  border-top: 1px dashed var(--border-base);
  padding-top: 10px;
}
.suggest-head {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}
.suggest-body {
  max-height: 220px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.suggest-item {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 12px;
}
.suggest-stem {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.muted {
  color: var(--text-muted);
  font-size: 12px;
}
</style>
