<script setup lang="ts">
// 备课 · 待备课（BE-L-01~04）：左课次列表 + 右备课面板（勾知识点 / 挑卷 / 备注）
//
// 交互设计原则（2026-10-09 优化）：
// ① 只列「还没上完」的课 —— 已上/已补/作废的课不再混在待办里（除非手动展开历史）；
// ② 左侧按「待备 / 已备」分组，待备在上，一节一节往下推；
// ③ 知识点按大类分组折叠 + 关键词搜索，避免上百个标签糊成一片；
// ④ 保存后自动跳到下一节待备课，形成「流水线」，不用回头点列表；
// ⑤ 支持从「今日待办」带 studentId 跳进来，自动定位到该生的课。
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchTodoPrep, fetchLessonPrep, saveLessonPrep, fetchSuggestQuestions } from '@/api/prep'
import { fetchKps } from '@/api/kp'
import { fetchPapers } from '@/api/paper'
import type { KnowledgePoint, Paper, Question, TodoPrep } from '@/types'
import { errMsg } from '@/utils/error'

const route = useRoute()

const STATUS_LABEL: Record<string, string> = {
  UNTAKEN: '未上',
  NORMAL: '已上',
  ABSENT: '顺延',
  MADEUP: '已补',
  CANCELLED: '作废'
}

/** 这些状态的课已经上完了，备课列表默认不再催 */
const FINISHED_STATUS = ['NORMAL', 'MADEUP', 'CANCELLED']

const loading = ref(false)
const list = ref<TodoPrep[]>([])
const kps = ref<KnowledgePoint[]>([])
const papers = ref<Paper[]>([])

const rangeMode = ref<'today' | 'week' | 'custom'>('week')
const customRange = ref<[string, string] | null>(null)
/** 是否把已上完的课也显示出来（默认隐藏，避免噪音） */
const showFinished = ref(false)
/** 知识点关键词搜索 */
const kpKeyword = ref('')
/** 折叠的大类（默认全部展开；用集合记录「被折叠的」比记录「展开的」更省事） */
const collapsedCategories = ref<Set<string>>(new Set())

const selected = ref<TodoPrep | null>(null)
const form = reactive({ kpIds: [] as number[], paperIds: [] as number[], remark: '' })
const saving = ref(false)
/** 表单相对已保存内容是否有改动（用于离开提醒与保存按钮状态） */
const dirty = ref(false)

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

/** 按「是否已上完」拆分后的可见列表 */
const visibleList = computed(() =>
  showFinished.value ? list.value : list.value.filter((x) => !FINISHED_STATUS.includes(x.lessonStatus))
)

/** 待备 / 已备两组（已备=至少配了 1 个知识点） */
const pendingList = computed(() => visibleList.value.filter((x) => !x.prepared))
const preparedList = computed(() => visibleList.value.filter((x) => x.prepared))
const hiddenFinishedCount = computed(
  () => list.value.length - list.value.filter((x) => !FINISHED_STATUS.includes(x.lessonStatus)).length
)

/** 按大类分组的可选知识点（先按年级过滤，再按关键词过滤） */
const kpGroups = computed<{ category: string; items: KnowledgePoint[] }[]>(() => {
  const g = selected.value?.grade
  const kw = kpKeyword.value.trim().toLowerCase()
  const filtered = kps.value.filter((k) => {
    if (k.enabled !== 1) return false
    if (g && k.grade && k.grade !== g) return false
    if (kw && !k.name.toLowerCase().includes(kw)) return false
    return true
  })
  const map = new Map<string, KnowledgePoint[]>()
  for (const k of filtered) {
    const cat = k.category || '其他'
    if (!map.has(cat)) map.set(cat, [])
    map.get(cat)!.push(k)
  }
  return [...map.entries()].map(([category, items]) => ({ category, items }))
})

/** 已选知识点对象（回显在面板顶部，便于确认选了啥） */
const selectedKps = computed(() => kps.value.filter((k) => form.kpIds.includes(k.id)))

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

/** 推荐卷：与本节已选知识点同名/同年级的卷排前面（一键勾选的候选） */
const recommendedPapers = computed(() => {
  if (!form.kpIds.length) return []
  const names = selectedKps.value.map((k) => k.name)
  return paperOptions.value
    .filter((p) => !form.paperIds.includes(p.id))
    .map((p) => {
      let score = 0
      if (names.some((n) => p.title.includes(n))) score -= 2
      if (selected.value?.grade && p.grade === selected.value.grade) score -= 1
      if (!p.studentId) score -= 1 // 通用卷更可复用
      return { paper: p, score }
    })
    .filter((x) => x.score < 0)
    .sort((a, b) => a.score - b.score)
    .slice(0, 4)
    .map((x) => x.paper)
})

function markDirty(): void {
  dirty.value = true
}

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
  dirty.value = false
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
  markDirty()
}

function togglePaper(id: number): void {
  if (form.paperIds.includes(id)) form.paperIds = form.paperIds.filter((x) => x !== id)
  else form.paperIds.push(id)
  markDirty()
}

function toggleCategory(category: string): void {
  const s = new Set(collapsedCategories.value)
  if (s.has(category)) s.delete(category)
  else s.add(category)
  collapsedCategories.value = s
}

/** 保存并自动跳到下一节待备课 —— 让备课变成一条流水线 */
async function onSave(thenNext = false): Promise<void> {
  if (!selected.value) return
  saving.value = true
  const savedId = selected.value.lessonId
  try {
    await saveLessonPrep({
      lessonId: savedId,
      kpIds: form.kpIds,
      paperIds: form.paperIds,
      remark: form.remark
    })
    dirty.value = false
    await loadList()
    if (thenNext) {
      // 当前这节课已备好，找列表里下一节还没备的
      const next = pendingList.value.find((x) => x.lessonId !== savedId)
      if (next) {
        await selectRow(next)
        ElMessage.success(`已保存，继续备 ${next.studentName || ''} ${next.lessonDate}`)
        return
      }
      ElMessage.success('已保存，本节是本区间最后一节待备课')
      return
    }
    ElMessage.success('已保存备课')
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

/** 从今日待办带 studentId 跳进来时，自动选中该生第一节待备课 */
async function autoLocateFromQuery(): Promise<void> {
  const sid = route.query.studentId
  if (!sid) return
  // 待办页可能指向「明天」的课，若今天是周末则明天已跨到下周，本周区间会漏掉。
  // 因此带学生跳转时把区间放宽成「今天起 14 天」，确保一定命中。
  const now = new Date()
  const end = new Date(now)
  end.setDate(now.getDate() + 14)
  rangeMode.value = 'custom'
  customRange.value = [fmt(now), fmt(end)]
  await loadList()
  const target = pendingList.value.find((x) => String(x.studentId) === String(sid))
  if (target) await selectRow(target)
  else ElMessage.info('该生未来两周没有待备课的课次')
}

watch(() => route.query.studentId, autoLocateFromQuery)

onMounted(async () => {
  await Promise.all([loadRefs(), loadList()])
  await autoLocateFromQuery()
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
              style="width: 230px"
              @change="loadList"
            />
          </div>
        </template>

        <div class="stat-row">
          <span class="stat">待备 <b class="warn">{{ pendingList.length }}</b> 节</span>
          <span class="stat">已备 <b class="ok">{{ preparedList.length }}</b> 节</span>
          <el-button
            v-if="hiddenFinishedCount > 0 || showFinished"
            link
            type="primary"
            class="stat-toggle"
            @click="showFinished = !showFinished"
          >
            {{ showFinished ? '隐藏已上完' : `显示已上完 ${hiddenFinishedCount} 节` }}
          </el-button>
        </div>

        <div v-loading="loading" class="cards">
          <el-empty v-if="!visibleList.length" description="该区间没有待备课的课次" :image-size="60" />

          <!-- 待备课（主战场） -->
          <template v-if="pendingList.length">
            <div class="group-label">待备 · {{ pendingList.length }}</div>
            <div
              v-for="row in pendingList"
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
                <el-tag size="small" type="warning">未备课</el-tag>
                <span v-if="row.grade" class="card-meta">{{ row.grade }}</span>
                <span v-if="row.lessonStatus !== 'UNTAKEN'" class="card-status">
                  {{ STATUS_LABEL[row.lessonStatus] || row.lessonStatus }}
                </span>
              </div>
            </div>
          </template>

          <!-- 已备课（可复核） -->
          <template v-if="preparedList.length">
            <div class="group-label done">已备 · {{ preparedList.length }}</div>
            <div
              v-for="row in preparedList"
              :key="row.lessonId"
              class="card done"
              :class="{ active: selected?.lessonId === row.lessonId }"
              @click="selectRow(row)"
            >
              <div class="card-top">
                <span class="card-time">{{ row.lessonDate }} {{ timeLabel(row) }}</span>
                <span class="card-stu">{{ row.studentName || '未指派' }}</span>
              </div>
              <div class="card-tags">
                <el-tag v-for="n in row.kpNames" :key="n" size="small" effect="plain">{{ n }}</el-tag>
                <span class="card-paper">{{ row.paperTitles.length }} 份卷</span>
                <span v-if="row.lessonStatus !== 'UNTAKEN'" class="card-status">
                  {{ STATUS_LABEL[row.lessonStatus] || row.lessonStatus }}
                </span>
              </div>
            </div>
          </template>
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
          <el-alert
            v-if="FINISHED_STATUS.includes(selected.lessonStatus)"
            type="info"
            :closable="false"
            show-icon
            class="finished-alert"
            :title="`这节课已「${STATUS_LABEL[selected.lessonStatus]}」，以下仅为历史备课记录，可复核但通常不用再改`"
          />

          <p class="label">
            本节讲的知识点（可多选）
            <template v-if="selectedKps.length">
              <span class="label-picked">已选 {{ selectedKps.length }}</span>
            </template>
          </p>

          <div v-if="selectedKps.length" class="picked-row">
            <el-tag
              v-for="k in selectedKps"
              :key="k.id"
              size="small"
              closable
              @close="toggleKp(k.id)"
            >
              {{ k.name }}
            </el-tag>
          </div>

          <el-input
            v-model="kpKeyword"
            size="small"
            placeholder="搜索知识点名称"
            clearable
            class="kp-search"
          />

          <div class="kp-groups">
            <div v-for="g in kpGroups" :key="g.category" class="kp-group">
              <div class="kp-group-head" @click="toggleCategory(g.category)">
                <span class="kp-caret">{{ collapsedCategories.has(g.category) ? '▸' : '▾' }}</span>
                <span class="kp-cat">{{ g.category }}</span>
                <span class="kp-cat-num">{{ g.items.length }}</span>
              </div>
              <div v-show="!collapsedCategories.has(g.category)" class="kp-tags">
                <span
                  v-for="k in g.items"
                  :key="k.id"
                  class="kp-tag"
                  :class="{ on: form.kpIds.includes(k.id), empty: k.questionCount === 0 }"
                  :title="k.questionCount === 0 ? '该知识点下还没有题' : `题库有 ${k.questionCount} 题`"
                  @click="toggleKp(k.id)"
                >
                  {{ k.name }}
                  <span class="kp-num">{{ k.questionCount }}</span>
                </span>
              </div>
            </div>
            <div v-if="!kpGroups.length" class="muted kp-empty">
              {{ kpKeyword ? '没有匹配的知识点' : '该年级暂无知识点，先去「知识点」页新增' }}
            </div>
          </div>

          <p class="label">使用的试卷（可多选）</p>
          <div v-if="recommendedPapers.length" class="reco-row">
            <span class="reco-hint">推荐卷：</span>
            <el-button
              v-for="p in recommendedPapers"
              :key="p.id"
              size="small"
              round
              @click="togglePaper(p.id)"
            >
              + {{ p.title }}
            </el-button>
          </div>
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
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            placeholder="这节课的重点提示（选填）"
            @input="markDirty"
          />

          <div class="panel-actions">
            <el-button link type="primary" @click="onSuggest">看可用题目</el-button>
            <div class="actions-right">
              <span v-if="dirty" class="dirty-hint">有未保存改动</span>
              <el-button :loading="saving" @click="onSave(false)">保存</el-button>
              <el-button type="primary" :loading="saving" @click="onSave(true)">保存并备下一节</el-button>
            </div>
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
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
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
  align-items: center;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--text-muted);
}
.stat b.warn {
  color: var(--warning);
}
.stat b.ok {
  color: var(--success);
}
.stat-toggle {
  margin-left: auto;
  font-size: 12px;
}
.cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.group-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--warning);
  padding: 2px 2px 0;
}
.group-label.done {
  color: var(--text-faint);
  margin-top: 6px;
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
.card.done {
  opacity: 0.72;
}
.card.done.active {
  opacity: 1;
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
.card-status,
.card-meta {
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
.finished-alert {
  margin-bottom: 12px;
}
.label {
  font-size: 12px;
  color: var(--text-muted);
  margin: 14px 0 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.label:first-of-type {
  margin-top: 0;
}
.label-picked {
  color: var(--brand-600);
}
.picked-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.kp-search {
  margin-bottom: 8px;
}
.kp-groups {
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 6px 8px;
}
.kp-group + .kp-group {
  margin-top: 6px;
  border-top: 1px dashed var(--border-subtle);
  padding-top: 6px;
}
.kp-group-head {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  color: var(--text-muted);
  padding: 2px 0;
}
.kp-caret {
  width: 10px;
  font-size: 10px;
}
.kp-cat {
  font-weight: 600;
}
.kp-cat-num {
  color: var(--text-faint);
}
.kp-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
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
  opacity: 0.6;
}
.kp-num {
  font-size: 11px;
  color: var(--text-faint);
}
.kp-empty {
  padding: 8px 0;
}
.reco-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.reco-hint {
  font-size: 12px;
  color: var(--text-faint);
}
.paper-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 200px;
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
.actions-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dirty-hint {
  font-size: 12px;
  color: var(--warning);
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
