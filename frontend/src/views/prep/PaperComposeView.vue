<script setup lang="ts">
// 制卷台（独立整页，无侧栏）：左试题库 / 中卷面 / 右组卷栏
// 交互参考：智学网组卷中心 —— 选题 → 拖拽编排（按住手柄拖到任意位置）→ 打印
// 落库：卷面每次变动立即整体覆盖保存（防"改了半小时忘保存"）
//
// 卷内编辑（本卷独有）：
//   在卷面上改题 → 写进 paper_question 的快照列，题库与其他卷不受影响；
//   没改过的题查卷时仍读题库原题（后端按 edited 标记决定读哪边）。
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import { fetchPaperDetail, setPaperQuestions, updatePaper, reusePaper, type PaperQuestionItem } from '@/api/paper'
import { fetchPapers } from '@/api/paper'
import { fetchQuestions } from '@/api/question'
import { fetchKps } from '@/api/kp'
import { fetchDicts } from '@/api/dict'
import type { Dict, KnowledgePoint, Paper, PaperDetail, Question } from '@/types'
import { errMsg } from '@/utils/error'
import { QTYPES, DIFFICULTY_LABEL } from '@/constants/question'
import { qtypeOf, qtypeWeight, isPairedType } from '@/utils/qtype'

const route = useRoute()
const router = useRouter()

const paperId = Number(route.params.id)
const loading = ref(true)
const paper = ref<PaperDetail | null>(null)

// ===================== 左侧：试题库 =====================
const bankLoading = ref(false)
const bank = ref<Question[]>([])
const bankTotal = ref(0)
const kps = ref<KnowledgePoint[]>([])
const grades = ref<Dict[]>([])
const bankFilter = reactive<{ grade?: string; kpId?: number; qtype?: string; keyword?: string }>({})
/** 超出首屏时一次多拉一些，满足"拖完接着挑"的连续感 */
const PAGE_SIZE = 200

async function loadBank(): Promise<void> {
  bankLoading.value = true
  try {
    const res = await fetchQuestions({ ...bankFilter, page: 1, size: PAGE_SIZE })
    bank.value = res.list
    bankTotal.value = res.total
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    bankLoading.value = false
  }
}

function resetBankFilter(): void {
  Object.assign(bankFilter, {
    grade: paper.value?.grade || undefined,
    kpId: undefined,
    qtype: undefined,
    keyword: ''
  })
  loadBank()
}

/**
 * 左侧题型下拉的候选项：题库里实际出现过的题型优先，再补上标准题型。
 * 只展示"有题可挑"的题型，避免选了却是空的。
 */
const bankQtypes = computed(() => {
  const seen = new Set(bank.value.map((q) => qtypeOf(q)))
  return Array.from(new Set([...QTYPES.filter((t) => seen.has(t)), ...seen]))
})

// ===================== 钩子：全部题目按题型取；阅读/古诗文两问并排，其余一题一行 =====================
/**
 * 拖拽组策略（两侧元素类型不同，必须分开定义）：
 *   题库（源）：pull='clone' → 拖出去只克隆，原列表不动；put=false → 不接受外来元素
 *   卷面（目标）：pull=true（卷面内自由重排）、put=true（接受题库拖入）
 * 组名相同才能互拖；pull/put 分侧配置，避免「卷面拖回题库」这类反向污染。
 */
const BANK_GROUP = { name: 'compose-questions', pull: 'clone', put: false }
const PAPER_GROUP = { name: 'compose-questions', pull: true, put: true }

/** 布局行：one = 单题独占（选择/填空/判断/写作/其他）；pair = 阅读/古诗文两问并排 */
interface ComposeRow {
  key: string
  items: Question[]
}
let rowSeq = 0
function rowKey(): string {
  return `row-${rowSeq++}`
}

/** 单题独占一行；阅读/古诗文按 2 题并排 */
function buildRows(list: Question[]): ComposeRow[] {
  const rows: ComposeRow[] = []
  let i = 0
  while (i < list.length) {
    const q = list[i]
    if (isPaired(q)) {
      const pair: Question[] = [q]
      const next = list[i + 1]
      if (next && isPaired(next)) {
        pair.push(next)
        i += 2
      } else {
        i += 1
      }
      rows.push({ key: rowKey(), items: pair })
    } else {
      rows.push({ key: rowKey(), items: [q] })
      i += 1
    }
  }
  return rows
}

function isPaired(q: Question): boolean {
  return isPairedType(qtypeOf(q))
}

const composeRows = ref<ComposeRow[]>([])

/** 拍平卷面所有小题（过滤掉任何非预期元素，避免拖拽中间态导致 undefined 抛错） */
const flatQuestions = computed<Question[]>(() =>
  composeRows.value.flatMap((r) => (Array.isArray(r?.items) ? r.items : [])).filter((q): q is Question => !!q)
)

// ===================== 拖拽落点 =====================
/**
 * 从题库拖入卷面时，源元素是 Question，而卷面列表的元素是 ComposeRow，
 * 两者结构不同 —— 必须用 :clone 把 Question 包成一行 ComposeRow，
 * 否则裸 Question 会被塞进 composeRows，导致 row.items 为 undefined（曾导致
 * 「Cannot read properties of undefined (reading 'id')」）。
 */
function wrapAsRow(q: Question): ComposeRow {
  return { key: rowKey(), items: [q] }
}

/** 卷面内拖拽结束：vuedraggable 已就地改好 composeRows，这里只负责拍平回 paper.questions 并落库 */
async function onComposeChange(): Promise<void> {
  await persist()
}

/** 「并排」按钮：把当前行与上一行合并成一行（仅阅读/古诗文用） */
function mergeUp(rowIndex: number): void {
  const cur = composeRows.value[rowIndex]
  const prev = composeRows.value[rowIndex - 1]
  if (!cur || !prev) return
  const merged = [...prev.items, ...cur.items]
  if (merged.length > 2) {
    ElMessage.warning('一行最多并排 2 道小题')
    return
  }
  composeRows.value.splice(rowIndex - 1, 2, { key: prev.key, items: merged })
  persist()
}

/** 「拆开」按钮：把并排行拆成两行 */
function splitRow(rowIndex: number): void {
  const row = composeRows.value[rowIndex]
  if (!row || row.items.length < 2) return
  const extra: ComposeRow[] = row.items.slice(1).map((q) => ({ key: rowKey(), items: [q] }))
  row.items = [row.items[0]]
  composeRows.value.splice(rowIndex + 1, 0, ...extra)
  persist()
}

/** 键盘移动：行内无鼠标也能微调位置 */
function moveRow(rowIndex: number, dir: -1 | 1): void {
  const target = rowIndex + dir
  const arr = composeRows.value
  if (target < 0 || target >= arr.length) return
  const tmp = arr[rowIndex]
  arr[rowIndex] = arr[target]
  arr[target] = tmp
  persist()
}

function removeQuestion(rowIndex: number, qi: number): void {
  const row = composeRows.value[rowIndex]
  if (!row) return
  row.items.splice(qi, 1)
  if (!row.items.length) composeRows.value.splice(rowIndex, 1)
  persist()
}

/** 清空卷面 */
const clearing = ref(false)
async function clearAll(): Promise<void> {
  if (!flatQuestions.value.length) return
  try {
    await ElMessageBox.confirm('确认清空卷面？题目仍保留在题库中。', '清空确认', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  clearing.value = true
  composeRows.value = []
  await persist()
  clearing.value = false
}

// ===================== 卷内编辑（本卷独有，写快照） =====================
const editVisible = ref(false)
const editSaving = ref(false)
const editTarget = ref<Question | null>(null)
/** 编辑表单：题面相关字段，保存后写进 paper_question 快照 */
const editForm = reactive({
  qtype: '选择',
  stem: '',
  options: ['', '', '', ''] as string[],
  answer: '',
  analysis: '',
  difficulty: 2
})

/** 选择题才渲染选项行 */
const editIsChoice = computed(() => ['选择', '判断'].includes(editForm.qtype))
const EDIT_OPT_LETTERS = ['A', 'B', 'C', 'D', 'E', 'F']
const JUDGE_OPTIONS = ['正确', '错误']

function parseOptions(options: string | null | undefined): string[] {
  if (!options) return []
  try {
    const arr = JSON.parse(options)
    return Array.isArray(arr) ? (arr as string[]) : []
  } catch {
    return []
  }
}

function openEdit(q: Question): void {
  editTarget.value = q
  const opts = parseOptions(q.options)
  editForm.qtype = qtypeOf(q)
  editForm.stem = q.stem || ''
  editForm.options =
    editForm.qtype === '判断'
      ? [...JUDGE_OPTIONS]
      : opts.length
        ? [...opts, '', '', '', ''].slice(0, Math.max(4, opts.length))
        : ['', '', '', '']
  editForm.answer = q.answer || ''
  editForm.analysis = q.analysis || ''
  editForm.difficulty = q.difficulty ?? 2
  editVisible.value = true
}

function onEditQtypeChange(): void {
  if (editForm.qtype === '判断') {
    editForm.options = [...JUDGE_OPTIONS]
  } else if (editForm.qtype === '选择') {
    if (editForm.options.length < 4) editForm.options = [...editForm.options, '', '', '', ''].slice(0, 4)
  }
}

/** 答案点选（可多选，拼成 AB） */
function toggleEditAnswer(letter: string): void {
  const cur = (editForm.answer || '').split(/[,，、\s]+/).filter(Boolean)
  const next = cur.includes(letter) ? cur.filter((x) => x !== letter) : [...cur, letter]
  next.sort((a, b) => EDIT_OPT_LETTERS.indexOf(a) - EDIT_OPT_LETTERS.indexOf(b))
  editForm.answer = next.join('')
}
function isEditAnswerOn(letter: string): boolean {
  return (editForm.answer || '').split(/[,，、\s]+/).includes(letter)
}

async function saveEdit(): Promise<void> {
  const q = editTarget.value
  if (!q) return
  if (!editForm.stem.trim()) {
    ElMessage.warning('题干不能为空')
    return
  }
  const opts = editIsChoice.value
    ? editForm.options.map((o) => (o || '').trim()).filter(Boolean)
    : []
  // 就地覆盖内存中的题对象 → 卷面立即呈现新内容，再整体落库（写快照）
  q.qtype = editForm.qtype
  q.stem = editForm.stem.trim()
  q.options = opts.length ? JSON.stringify(opts) : null
  q.answer = editForm.answer || null
  q.analysis = editForm.analysis || null
  q.difficulty = editForm.difficulty
  q.edited = true
  editVisible.value = false
  await persist()
  ElMessage.success('已保存到本卷（不影响题库原题）')
}

/** 还原为题库原题：重新拉一次详情，把该题的快照抹掉 */
async function revertEdit(q: Question): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '还原后，本卷这道题将重新跟随题库原题（题库若改过也会同步）。确认还原？',
      '还原确认',
      { type: 'warning', confirmButtonText: '还原', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  q.edited = false
  // 用快照前的题库数据回填：重新拉详情最稳（后端按 edited=0 读原题）
  await persist()
  const d = await fetchPaperDetail(paperId)
  paper.value = d
  composeRows.value = buildRows(d.questions)
}

// ===================== 落库（整体覆盖保存） =====================
const saving = ref(false)
const lastSaved = ref<string>('')
const dirty = ref(false)
let saveTimer: number | undefined
let saveSeq = 0

/** 防抖保存：连续拖拽只在停下来后写一次库 */
function persist(): void {
  dirty.value = true
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(doSave, 500)
}

/** 卷面 → 落库项：edited 的题携带快照字段 */
function toItems(): PaperQuestionItem[] {
  return flatQuestions.value.map((q) => {
    if (q.edited) {
      return {
        questionId: q.id,
        edited: true,
        qtype: qtypeOf(q),
        stem: q.stem,
        options: q.options,
        answer: q.answer,
        analysis: q.analysis,
        difficulty: q.difficulty
      }
    }
    return { questionId: q.id, edited: false }
  })
}

async function doSave(): Promise<void> {
  if (!paper.value) return
  const items = toItems()
  const seq = ++saveSeq
  saving.value = true
  try {
    await setPaperQuestions({ paperId: paperId, items })
    if (seq !== saveSeq) return // 又改了，让最后一次落库写状态
    paper.value.questionCount = items.length
    paper.value.questions = flatQuestions.value
    dirty.value = false
    lastSaved.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (e) {
    ElMessage.error('自动保存失败：' + errMsg(e))
  } finally {
    saving.value = false
  }
}

// ===================== 卷面信息 =====================
const editingTitle = ref(false)
const titleInput = ref('')
const titleRef = ref()

function startEditTitle(): void {
  if (!paper.value) return
  titleInput.value = paper.value.title
  editingTitle.value = true
  nextTick(() => titleRef.value?.focus())
}

async function saveTitle(): Promise<void> {
  editingTitle.value = false
  const t = titleInput.value.trim()
  if (!paper.value || !t || t === paper.value.title) return
  try {
    await updatePaper({ id: paper.value.id, title: t })
    paper.value.title = t
    ElMessage.success('卷名已更新')
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

async function toggleStatus(): Promise<void> {
  if (!paper.value) return
  const next = paper.value.status === 'READY' ? 'DRAFT' : 'READY'
  try {
    await updatePaper({ id: paper.value.id, status: next })
    paper.value.status = next
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

function printPaper(): void {
  if (!paper.value) return
  const url = router.resolve({ name: 'PaperPrint', params: { id: paper.value.id } }).href
  window.open(url, '_blank')
}

// ===================== 从已有试卷导入（快速复用） =====================
const importVisible = ref(false)
const importLoading = ref(false)
const importPapers = ref<Paper[]>([])
const importKeyword = ref('')
const importSelected = ref<number | null>(null)

async function openImport(): Promise<void> {
  importVisible.value = true
  importSelected.value = null
  await loadImportPapers()
}

async function loadImportPapers(): Promise<void> {
  importLoading.value = true
  try {
    const list = await fetchPapers({ keyword: importKeyword.value || undefined })
    // 排除自己，避免"复用自己"
    importPapers.value = list.filter((p) => p.id !== paperId)
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    importLoading.value = false
  }
}

/** 把选中试卷的题目追加到当前卷末尾（重复的跳过） */
async function doImport(): Promise<void> {
  if (!importSelected.value) {
    ElMessage.warning('请选择要复用的试卷')
    return
  }
  try {
    const d = await fetchPaperDetail(importSelected.value)
    const existing = new Set(flatQuestions.value.map((q) => q.id))
    const added: Question[] = []
    for (const q of d.questions) {
      if (existing.has(q.id)) continue
      existing.add(q.id)
      added.push(q)
    }
    if (!added.length) {
      ElMessage.info('该卷的题目已全部在本卷中')
      importVisible.value = false
      return
    }
    // 追加到末尾：已有行保持，新增题按行规整后再并入
    composeRows.value.push(...buildRows(added))
    importVisible.value = false
    await persist()
    ElMessage.success(`已从「${d.title}」并入 ${added.length} 题`)
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

// ===================== 整卷复用（另存为新卷） =====================
const reuseVisible = ref(false)
const reuseLoading = ref(false)
const reuseSource = ref<Paper | null>(null)
const reuseTitle = ref('')

function openReuse(row?: Paper): void {
  // 不传则用当前卷自身为蓝本
  reuseSource.value = row || (paper.value ? { ...paper.value } : null)
  reuseTitle.value = (reuseSource.value?.title || '') + '（副本）'
  reuseVisible.value = true
}

async function doReuse(): Promise<void> {
  if (!reuseSource.value) return
  reuseLoading.value = true
  try {
    const id = await reusePaper({
      sourceId: reuseSource.value.id,
      title: reuseTitle.value.trim() || undefined
    })
    reuseVisible.value = false
    ElMessage.success('已生成一份新卷，正在打开…')
    router.push({ name: 'PaperCompose', params: { id } })
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    reuseLoading.value = false
  }
}

// ===================== 题目预览 =====================
const previewVisible = ref(false)
const previewQ = ref<Question | null>(null)

function openPreview(q: Question): void {
  previewQ.value = q
  previewVisible.value = true
}

/** 题干摘要（列表里截断显示） */
function stemBrief(q: Question, n = 60): string {
  const s = (q.stem || '').replace(/\s+/g, ' ')
  return s.length > n ? s.slice(0, n) + '…' : s
}

function inPaper(qid: number): boolean {
  return flatQuestions.value.some((q) => q.id === qid)
}

/** 卷面题量（按行内小题累计） */
const counts = computed(() => ({
  total: flatQuestions.value.length,
  edited: flatQuestions.value.filter((q) => q.edited).length
}))

/** 卷面构成：按题型分组计数 */
const breakdown = computed(() => {
  const map = new Map<string, number>()
  for (const q of flatQuestions.value) {
    const t = qtypeOf(q) || '其他'
    map.set(t, (map.get(t) || 0) + 1)
  }
  // 与卷面排序一致：按项目统一题型表排序
  return Array.from(map.entries())
    .map(([type, count]) => ({ type, count }))
    .sort((a, b) => qtypeWeight(a.type) - qtypeWeight(b.type))
})

/**
 * 连续题号：把卷面上所有小题按顺序编号。
 * rowIndex 行的第 qi 个小题 = 该行之前所有小题数 + qi + 1。
 * 阅读/古诗文并排的两问各占一个独立编号（如 5、6）。
 */
function numberAt(rowIndex: number, qi = 0): number {
  let n = 0
  for (let i = 0; i < rowIndex; i++) {
    n += composeRows.value[i]?.items.length ?? 0
  }
  return n + qi + 1
}

// ===================== 卷面设置（年级） =====================
const gradeDraft = ref<string | undefined>(undefined)

async function saveGrade(): Promise<void> {
  if (!paper.value) return
  const next = gradeDraft.value || null
  if (next === paper.value.grade) return
  try {
    await updatePaper({ id: paper.value.id, grade: next })
    paper.value.grade = next
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

// ===================== 生命周期 =====================
let onKeydown: ((e: KeyboardEvent) => void) | null = null

async function loadRefs(): Promise<void> {
  try {
    kps.value = await fetchKps()
  } catch {
    kps.value = []
  }
  try {
    grades.value = await fetchDicts('grade')
  } catch {
    grades.value = []
  }
}

async function loadPaper(): Promise<void> {
  loading.value = true
  try {
    const d = await fetchPaperDetail(paperId)
    paper.value = d
    gradeDraft.value = d.grade ?? undefined
    composeRows.value = buildRows(d.questions)
    resetBankFilter()
  } catch (e) {
    ElMessage.error(errMsg(e))
    router.replace({ name: 'PrepPapers' })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadRefs()
  loadPaper()
  // Ctrl/Cmd+S 手动保存；Esc 退出卷名编辑
  onKeydown = (e: KeyboardEvent) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
      e.preventDefault()
      if (saveTimer) window.clearTimeout(saveTimer)
      doSave()
    }
    if (e.key === 'Escape' && editingTitle.value) {
      editingTitle.value = false
    }
  }
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  if (onKeydown) window.removeEventListener('keydown', onKeydown)
  if (saveTimer) {
    window.clearTimeout(saveTimer)
    if (dirty.value) doSave() // 离开前补一次保存，避免丢改动
  }
})
</script>

<template>
  <div class="compose-page" v-loading="loading">
    <!-- ===================== 顶部工具条 ===================== -->
    <header class="compose-head">
      <div class="head-left">
        <button class="back-btn" @click="router.push({ name: 'PrepPapers' })">
          <span class="back-arrow">←</span>
          <span>试卷列表</span>
        </button>
        <span class="head-divider" />
        <span class="head-badge">{{ paper?.paperType === 'LESSON' ? '课时题单' : '知识点卷' }}</span>
        <span v-if="paper?.studentName" class="head-badge is-student">{{ paper.studentName }} 专属</span>
      </div>

      <div class="head-center">
        <span class="save-state" :class="{ dirty, saving }">
          <span class="save-dot" />
          <template v-if="saving">保存中…</template>
          <template v-else-if="dirty">有改动待保存</template>
          <template v-else-if="lastSaved">已保存 {{ lastSaved }}</template>
          <template v-else>自动保存已开启</template>
        </span>
      </div>

      <div class="head-right">
        <el-button size="small" class="head-btn" @click="openImport">从已有试卷导入</el-button>
        <el-button size="small" class="head-btn" @click="openReuse()">整卷复用</el-button>
        <el-button
          size="small"
          class="head-btn"
          :type="paper?.status === 'READY' ? 'warning' : 'success'"
          plain
          @click="toggleStatus"
        >
          {{ paper?.status === 'READY' ? '退回草稿' : '标记可用' }}
        </el-button>
        <el-button size="small" type="primary" class="head-print" @click="printPaper">
          打印 / 导出 PDF
        </el-button>
      </div>
    </header>

    <div class="compose-body">
      <!-- ===================== 左：试题库 ===================== -->
      <aside class="pane pane-bank">
        <div class="pane-head">
          <div class="pane-head-main">
            <span class="pane-icon">库</span>
            <span class="pane-title">试题库</span>
          </div>
          <span class="pane-count">{{ bankTotal }} 题</span>
        </div>

        <div class="bank-filter">
          <el-select v-model="bankFilter.grade" placeholder="全部年级" clearable size="small" @change="loadBank">
            <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
          </el-select>
          <el-select
            v-model="bankFilter.kpId"
            placeholder="全部知识点"
            clearable
            filterable
            size="small"
            @change="loadBank"
          >
            <el-option v-for="k in kps" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
          <div class="filter-row">
            <el-select v-model="bankFilter.qtype" placeholder="全部题型" clearable size="small" @change="loadBank">
              <el-option v-for="t in bankQtypes" :key="t" :label="t" :value="t" />
            </el-select>
            <el-input
              v-model="bankFilter.keyword"
              placeholder="搜索题干"
              clearable
              size="small"
              @keyup.enter="loadBank"
            >
              <template #append>
                <el-button @click="loadBank">查</el-button>
              </template>
            </el-input>
          </div>
          <div class="bank-filter-foot">
            <button class="link-btn" @click="resetBankFilter">重置筛选</button>
            <span class="bank-hint">拖到中间卷面即可加入</span>
          </div>
        </div>

        <draggable
          v-model="bank"
          class="bank-list"
          :group="BANK_GROUP"
          item-key="id"
          :sort="false"
          ghost-class="drag-ghost"
          chosen-class="drag-chosen"
        >
          <template #item="{ element: q }">
            <div class="bank-card" :class="{ used: inPaper(q.id) }">
              <div class="bank-card-head">
                <span class="qtype-chip">{{ qtypeOf(q) || '其他' }}</span>
                <span class="kp-name">{{ q.kpName || '未标知识点' }}</span>
                <span v-if="inPaper(q.id)" class="used-chip">已在卷</span>
              </div>
              <div class="bank-stem">{{ stemBrief(q, 54) }}</div>
              <div class="bank-card-foot">
                <span class="diff" :class="`d${q.difficulty}`">{{ DIFFICULTY_LABEL[q.difficulty] || '中' }}</span>
                <button class="link-btn" @click.stop="openPreview(q)">预览</button>
              </div>
            </div>
          </template>
        </draggable>
        <el-empty v-if="!bankLoading && !bank.length" description="没有匹配的题目" :image-size="60" />
      </aside>

      <!-- ===================== 中：卷面 ===================== -->
      <main class="pane pane-paper">
        <div class="paper-toolbar">
          <div class="paper-title-edit">
            <template v-if="editingTitle">
              <el-input
                ref="titleRef"
                v-model="titleInput"
                size="small"
                class="title-input"
                @blur="saveTitle"
                @keyup.enter="saveTitle"
              />
            </template>
            <template v-else>
              <h2 class="paper-title" @click="startEditTitle" title="点击改名">
                {{ paper?.title }}
                <span class="title-pen">✎</span>
              </h2>
            </template>
          </div>
          <div class="paper-toolbar-right">
            <span class="paper-count">
              共 <b>{{ counts.total }}</b> 题
              <span v-if="counts.edited" class="edited-hint">· {{ counts.edited }} 题已改</span>
            </span>
            <el-button link size="small" :disabled="!counts.total" @click="clearAll">清空卷面</el-button>
          </div>
        </div>

        <div class="paper-canvas">
          <div class="paper-sheet">
            <div class="paper-meta-row">
              <span class="meta-item">姓名：<span class="meta-blank" /></span>
              <span class="meta-item">班级：<span class="meta-blank" /></span>
              <span class="meta-item">日期：<span class="meta-blank" /></span>
              <span class="meta-item score-item">得分：<span class="meta-score" /></span>
            </div>

            <draggable
              v-model="composeRows"
              class="paper-rows"
              :group="PAPER_GROUP"
              item-key="key"
              handle=".drag-handle"
              :clone="wrapAsRow"
              ghost-class="drag-ghost"
              chosen-class="drag-chosen"
              @change="onComposeChange"
            >
              <template #item="{ element: row, index: rowIndex }">
                <div class="paper-row" :class="{ paired: (row.items?.length ?? 0) > 1 }">
                  <div class="row-gutter">
                    <span class="drag-handle" title="按住拖到任意位置">⠿</span>
                    <span class="row-no">{{ numberAt(rowIndex) }}</span>
                  </div>

                  <div class="row-items">
                    <div v-for="(q, qi) in row.items || []" :key="q.id" class="paper-q-wrap">
                      <div class="paper-q">
                        <div class="paper-q-head">
                          <span class="paper-q-no">{{ numberAt(rowIndex, qi) }}.</span>
                          <span class="paper-q-stem">{{ q.stem }}</span>
                          <span v-if="q.edited" class="q-edited-flag" title="本卷已改，不跟随题库">已改</span>
                        </div>
                        <div v-if="parseOptions(q.options).length" class="paper-opts">
                          <div v-for="(o, oi) in parseOptions(q.options)" :key="oi" class="paper-opt">
                            <span class="opt-key">{{ String.fromCharCode(65 + oi) }}</span>
                            {{ o }}
                          </div>
                        </div>
                        <div class="paper-blank" />
                      </div>
                      <div class="paper-q-ops">
                        <button class="op-btn is-edit" @click="openEdit(q)">
                          {{ q.edited ? '继续改' : '编辑' }}
                        </button>
                        <button class="op-btn" @click="openPreview(q)">预览</button>
                        <button v-if="q.edited" class="op-btn is-revert" @click="revertEdit(q)">还原</button>
                        <button
                          v-if="qi === 0"
                          class="op-btn"
                          :disabled="row.items.length > 1 || rowIndex === 0"
                          @click="mergeUp(rowIndex)"
                        >
                          并排
                        </button>
                        <button
                          v-if="qi === 0"
                          class="op-btn"
                          :disabled="row.items.length < 2"
                          @click="splitRow(rowIndex)"
                        >
                          拆开
                        </button>
                        <button class="op-btn is-danger" @click="removeQuestion(rowIndex, qi)">移出</button>
                      </div>
                    </div>
                  </div>

                  <div class="row-side">
                    <button
                      class="side-btn"
                      :disabled="rowIndex === 0"
                      title="上移"
                      @click="moveRow(rowIndex, -1)"
                    >
                      ↑
                    </button>
                    <button
                      class="side-btn"
                      :disabled="rowIndex === composeRows.length - 1"
                      title="下移"
                      @click="moveRow(rowIndex, 1)"
                    >
                      ↓
                    </button>
                  </div>
                </div>
              </template>
            </draggable>

            <el-empty
              v-if="!composeRows.length"
              description="卷面还是空的，从左侧把题拖进来"
              :image-size="80"
            />
          </div>
        </div>
      </main>

      <!-- ===================== 右：组卷栏 ===================== -->
      <aside class="pane pane-side">
        <div class="pane-head">
          <div class="pane-head-main">
            <span class="pane-icon">卷</span>
            <span class="pane-title">组卷信息</span>
          </div>
        </div>

        <div class="side-block">
          <div class="side-label">卷面构成</div>
          <div class="side-stat">
            <span class="stat-num">{{ counts.total }}</span>
            <span class="stat-unit">题</span>
          </div>
          <div class="side-rows">
            <div v-for="s in breakdown" :key="s.type" class="side-row">
              <span class="side-row-name">
                <span class="dot" />
                {{ s.type }}
              </span>
              <span class="side-row-val">{{ s.count }}</span>
            </div>
            <div v-if="!breakdown.length" class="side-empty">还没有题目</div>
          </div>
        </div>

        <div class="side-block">
          <div class="side-label">卷面设置</div>
          <div class="side-field">
            <span>年级</span>
            <el-select v-model="gradeDraft" size="small" placeholder="不限" clearable @change="saveGrade">
              <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
            </el-select>
          </div>
          <div class="side-field">
            <span>归属</span>
            <span class="side-text">{{ paper?.studentName || '通用卷' }}</span>
          </div>
          <div class="side-field">
            <span>状态</span>
            <span class="side-status" :class="paper?.status === 'READY' ? 'ok' : 'draft'">
              {{ paper?.status === 'READY' ? '可用' : '草稿' }}
            </span>
          </div>
          <div v-if="counts.edited" class="side-field">
            <span>卷内已改</span>
            <span class="side-text">{{ counts.edited }} 题</span>
          </div>
        </div>

        <div class="side-block">
          <div class="side-label">快捷操作</div>
          <div class="side-ops">
            <el-button size="small" :loading="saving" @click="doSave">立即保存</el-button>
            <el-button size="small" @click="openImport">从已有试卷导入</el-button>
            <el-button size="small" @click="openReuse()">整卷复用为新卷</el-button>
            <el-button size="small" type="primary" plain @click="printPaper">打印 / 导出 PDF</el-button>
          </div>
          <p class="side-tip">
            卷面一改就自动保存。在卷面上改题只影响本卷，题库原题不动；「已改」的题可随时还原。
          </p>
        </div>
      </aside>
    </div>

    <!-- ===================== 卷内编辑弹窗 ===================== -->
    <el-dialog v-model="editVisible" title="编辑试题（仅本卷）" width="680px" top="6vh" class="edit-dialog">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="edit-alert"
      >
        <template #title>这里的修改只写进本卷，不会改动题库原题，也不会影响其他引用了这道题的试卷。</template>
      </el-alert>
      <div class="edit-form">
        <div class="edit-row">
          <label class="edit-label">题型</label>
          <el-select v-model="editForm.qtype" size="default" style="width: 160px" @change="onEditQtypeChange">
            <el-option v-for="t in QTYPES" :key="t" :label="t" :value="t" />
          </el-select>
          <label class="edit-label" style="margin-left: 20px">难度</label>
          <el-radio-group v-model="editForm.difficulty">
            <el-radio :value="1">易</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">难</el-radio>
          </el-radio-group>
        </div>

        <div class="edit-field">
          <label class="edit-label">题干</label>
          <el-input v-model="editForm.stem" type="textarea" :rows="4" placeholder="题干，支持换行" />
        </div>

        <div v-if="editIsChoice" class="edit-field">
          <label class="edit-label">选项</label>
          <div class="edit-opt-list">
            <div v-for="(_, i) in editForm.options" :key="i" class="edit-opt-row">
              <span class="edit-opt-key">{{ EDIT_OPT_LETTERS[i] }}</span>
              <el-input
                v-model="editForm.options[i]"
                :placeholder="editForm.qtype === '判断' ? JUDGE_OPTIONS[i] : `选项 ${EDIT_OPT_LETTERS[i]}`"
              />
              <el-button
                v-if="editForm.qtype === '选择' && editForm.options.length > 2"
                link
                type="danger"
                @click="editForm.options.splice(i, 1)"
              >
                删
              </el-button>
            </div>
            <button
              v-if="editForm.qtype === '选择' && editForm.options.length < 6"
              class="link-btn"
              @click="editForm.options.push('')"
            >
              + 添加选项
            </button>
          </div>
        </div>

        <div class="edit-field">
          <label class="edit-label">答案</label>
          <div class="edit-ans">
            <template v-if="editIsChoice">
              <span
                v-for="(_, i) in editForm.options"
                :key="i"
                class="ans-chip"
                :class="{ on: isEditAnswerOn(EDIT_OPT_LETTERS[i]) }"
                @click="toggleEditAnswer(EDIT_OPT_LETTERS[i])"
              >
                {{ EDIT_OPT_LETTERS[i] }}
              </span>
            </template>
            <el-input v-model="editForm.answer" size="small" placeholder="也可直接手写答案" style="width: 260px" />
          </div>
        </div>

        <div class="edit-field">
          <label class="edit-label">解析</label>
          <el-input v-model="editForm.analysis" type="textarea" :rows="3" placeholder="讲题要点（选填）" />
        </div>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">保存到本卷</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 题目详情预览 ===================== -->
    <el-dialog v-model="previewVisible" title="题目详情" width="640px" top="8vh">
      <div v-if="previewQ" class="q-detail">
        <div class="q-detail-meta">
          <span class="qtype-chip">{{ qtypeOf(previewQ) || '其他' }}</span>
          <span class="kp-chip">{{ previewQ.kpName || '未标知识点' }}</span>
          <span class="diff" :class="`d${previewQ.difficulty}`">{{ DIFFICULTY_LABEL[previewQ.difficulty] || '中' }}</span>
          <span v-if="previewQ.grade" class="kp-chip">{{ previewQ.grade }}</span>
          <span v-if="previewQ.edited" class="q-edited-flag">本卷已改</span>
        </div>
        <div class="q-detail-stem">{{ previewQ.stem }}</div>
        <div v-if="parseOptions(previewQ.options).length" class="q-detail-opts">
          <div v-for="(o, oi) in parseOptions(previewQ.options)" :key="oi">
            {{ String.fromCharCode(65 + oi) }}. {{ o }}
          </div>
        </div>
        <div class="q-detail-line"><b>答案：</b>{{ previewQ.answer || '（未填）' }}</div>
        <div v-if="previewQ.analysis" class="q-detail-line"><b>解析：</b>{{ previewQ.analysis }}</div>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="openEdit(previewQ!)">编辑本题</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 从已有试卷导入（并入当前卷） ===================== -->
    <el-dialog v-model="importVisible" title="从已有试卷导入题目" width="560px" top="8vh">
      <div class="import-bar">
        <el-input
          v-model="importKeyword"
          placeholder="搜索卷名"
          clearable
          size="small"
          style="width: 220px"
          @keyup.enter="loadImportPapers"
        />
        <el-button size="small" @click="loadImportPapers">搜索</el-button>
      </div>
      <div class="import-tip">选中的试卷，其题目会「追加」到当前卷面末尾（已在本卷的题自动跳过）。</div>
      <div v-loading="importLoading" class="import-list">
        <div
          v-for="p in importPapers"
          :key="p.id"
          class="import-item"
          :class="{ on: importSelected === p.id }"
          @click="importSelected = p.id"
        >
          <div class="import-item-main">
            <div class="import-title">{{ p.title }}</div>
            <div class="import-meta">
              <span>{{ p.grade || '不限年级' }}</span>
              <span>· {{ p.paperType === 'LESSON' ? '课时题单' : '知识点卷' }}</span>
              <span>· {{ p.questionCount }} 题</span>
              <span v-if="p.studentName">· {{ p.studentName }}</span>
            </div>
          </div>
          <span class="import-check" :class="{ on: importSelected === p.id }">✓</span>
        </div>
        <el-empty v-if="!importLoading && !importPapers.length" description="没有可导入的试卷" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!importSelected" @click="doImport">并入当前卷</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 整卷复用（另存为新卷） ===================== -->
    <el-dialog v-model="reuseVisible" title="整卷复用为新卷" width="480px">
      <p class="reuse-tip">
        以「<b>{{ reuseSource?.title }}</b>」为蓝本生成一份<b>新的可编辑卷</b>，
        题目与编排一并复制，原卷不受影响。
      </p>
      <div class="reuse-field">
        <label class="edit-label">新卷名</label>
        <el-input v-model="reuseTitle" placeholder="留空则自动命名" />
      </div>
      <template #footer>
        <el-button @click="reuseVisible = false">取消</el-button>
        <el-button type="primary" :loading="reuseLoading" @click="doReuse">生成新卷</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
// 说明：题型判定与题号生成放普通 <script> 里，只是为了让模板能直接用；
// 逻辑本身无状态，不依赖 this。
export default {}
</script>

<style scoped>
.compose-page {
  /* 独立整页：吃掉侧栏外的全部高度，三栏各自滚动 */
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--page-bg);
  color: var(--text-body);
}

/* ===================== 顶部工具条 ===================== */
.compose-head {
  flex: none;
  height: 56px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--grad-toolbar);
  border-bottom: 1px solid var(--border-base);
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.03);
  z-index: 2;
}
.head-left,
.head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.head-center {
  flex: 1;
  display: flex;
  justify-content: center;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-body);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.back-btn:hover {
  background: var(--surface-sunken);
  border-color: var(--border-base);
  color: var(--text-strong);
}
.back-arrow {
  font-size: 15px;
  line-height: 1;
}
.head-divider {
  width: 1px;
  height: 18px;
  background: var(--border-base);
}
.head-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--grad-brand-soft);
  color: var(--brand-700);
  font-weight: 500;
}
.head-badge.is-student {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}
.head-btn {
  font-weight: 500;
}
.head-print {
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.24);
}
.save-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  background: var(--surface-sunken);
  border: 1px solid var(--border-subtle);
}
.save-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success);
}
.save-state.dirty .save-dot {
  background: var(--warning);
}
.save-state.saving .save-dot {
  background: var(--brand-500);
  animation: pulse 1s infinite;
}
.save-state.dirty {
  color: #b45309;
  background: rgba(245, 158, 11, 0.08);
  border-color: rgba(245, 158, 11, 0.25);
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

/* ===================== 三栏骨架 ===================== */
.compose-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 312px minmax(0, 1fr) 272px;
  gap: 14px;
  padding: 14px;
}
.pane {
  background: var(--surface);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  box-shadow: var(--shadow-raise);
}
.pane-head {
  flex: none;
  padding: 13px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-subtle);
}
.pane-head-main {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pane-icon {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: var(--grad-brand-soft);
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 700;
}
.pane-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-strong);
  letter-spacing: 0.2px;
}
.pane-count {
  font-size: 12px;
  color: var(--text-muted);
  background: var(--surface-sunken);
  padding: 2px 9px;
  border-radius: var(--radius-pill);
}

/* ===================== 左：试题库 ===================== */
.bank-filter {
  flex: none;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--surface-sunken);
  border-bottom: 1px solid var(--border-subtle);
}
.filter-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.bank-filter :deep(.el-input-group__append) {
  padding: 0 10px;
}
.bank-filter-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 2px;
}
.bank-hint {
  font-size: 11px;
  color: var(--text-faint);
}
.link-btn {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: var(--brand-600);
  cursor: pointer;
}
.link-btn:hover {
  color: var(--brand-500);
  text-decoration: underline;
}
.bank-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.bank-card {
  border: 1px solid var(--border-base);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  cursor: grab;
  background: var(--surface);
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.bank-card:hover {
  border-color: var(--brand-300);
  box-shadow: var(--shadow-card);
  transform: translateY(-1px);
}
.bank-card.used {
  background: var(--surface-sunken);
  border-style: dashed;
}
.bank-card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.qtype-chip {
  flex: none;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--grad-brand-soft);
  color: var(--brand-700);
}
.kp-name {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.used-chip {
  margin-left: auto;
  flex: none;
  font-size: 11px;
  color: var(--success);
  background: rgba(16, 185, 129, 0.1);
  padding: 2px 8px;
  border-radius: var(--radius-pill);
}
.bank-stem {
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--text-body);
  word-break: break-word;
}
.bank-card-foot {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.diff {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: var(--surface-sunken);
  color: var(--text-muted);
}
.diff.d1 {
  color: var(--success);
  background: rgba(16, 185, 129, 0.1);
}
.diff.d3 {
  color: var(--danger);
  background: rgba(239, 68, 68, 0.1);
}

/* ===================== 中：卷面 ===================== */
.pane-paper {
  background: var(--canvas-bg);
  background-image: var(--canvas-grad);
}
.paper-toolbar {
  flex: none;
  height: 52px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--surface);
  border-bottom: 1px solid var(--border-subtle);
}
.paper-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-strong);
  cursor: text;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.paper-title:hover {
  color: var(--brand-600);
}
.title-pen {
  font-size: 12px;
  color: var(--text-faint);
  opacity: 0;
  transition: opacity 0.15s;
}
.paper-title:hover .title-pen {
  opacity: 1;
}
.title-input {
  width: 340px;
}
.paper-toolbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.paper-count {
  font-size: 12.5px;
  color: var(--text-muted);
}
.paper-count b {
  color: var(--brand-600);
  font-size: 14px;
}
.edited-hint {
  color: #b45309;
}
.paper-canvas {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 22px 26px 40px;
}
/* 纸张质感：白色卷面浮在灰底上 */
.paper-sheet {
  max-width: 860px;
  margin: 0 auto;
  background: var(--paper-bg);
  border-radius: var(--radius-md);
  padding: 26px 30px 34px;
  box-shadow: var(--shadow-float);
  border: 1px solid var(--hairline);
  font-family: 'Songti SC', 'SimSun', 'STSong', serif;
}
.paper-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 26px;
  font-size: 13px;
  padding-bottom: 12px;
  border-bottom: 1.5px solid var(--border-base);
  margin-bottom: 18px;
  color: var(--text-body);
}
.meta-item {
  display: inline-flex;
  align-items: center;
}
.meta-blank {
  display: inline-block;
  width: 90px;
  border-bottom: 1px solid var(--text-faint);
  height: 1.1em;
  margin-left: 2px;
}
.score-item {
  margin-left: auto;
}
.meta-score {
  display: inline-block;
  width: 46px;
  height: 20px;
  border: 1px solid var(--text-faint);
  border-radius: 3px;
  vertical-align: middle;
}
.paper-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.paper-row {
  display: flex;
  gap: 10px;
  align-items: stretch;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  transition: box-shadow 0.15s, border-color 0.15s;
}
.paper-row:hover {
  border-color: var(--brand-200);
  box-shadow: var(--shadow-card);
}
.paper-row.paired .row-items {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.row-gutter {
  flex: none;
  width: 26px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
}
.drag-handle {
  cursor: grab;
  color: var(--text-faint);
  font-size: 14px;
  line-height: 1;
  user-select: none;
  padding: 2px;
  border-radius: 4px;
  transition: background 0.15s, color 0.15s;
}
.drag-handle:hover {
  background: var(--surface-sunken);
  color: var(--brand-500);
}
.drag-handle:active {
  cursor: grabbing;
}
.row-no {
  font-size: 11px;
  color: var(--text-faint);
  font-family: 'Inter', sans-serif;
}
.row-items {
  flex: 1;
  min-width: 0;
}
.paper-q-wrap + .paper-q-wrap {
  margin-top: 10px;
}
.paper-q {
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--text-strong);
}
.paper-q-head {
  display: flex;
  gap: 5px;
  align-items: baseline;
}
.paper-q-no {
  flex: none;
  font-weight: 600;
  color: var(--brand-600);
  font-family: 'Inter', sans-serif;
}
.paper-q-stem {
  white-space: pre-wrap;
  word-break: break-word;
}
.q-edited-flag {
  flex: none;
  font-size: 10.5px;
  font-family: 'Inter', sans-serif;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  font-weight: 600;
  align-self: center;
}
.paper-opts {
  margin: 5px 0 0 20px;
  color: var(--text-body);
  font-size: 13px;
}
.paper-opt {
  line-height: 1.85;
}
.opt-key {
  display: inline-block;
  width: 16px;
  font-weight: 600;
  font-family: 'Inter', sans-serif;
  color: var(--text-muted);
}
.paper-blank {
  margin: 9px 0 0 20px;
  height: 20px;
  border-bottom: 1px dashed var(--border-base);
}
.paper-q-ops {
  margin-top: 7px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
  flex-wrap: wrap;
}
.paper-row:hover .paper-q-ops {
  opacity: 1;
}
.op-btn {
  border: none;
  background: none;
  padding: 2px 7px;
  border-radius: 5px;
  font-size: 12px;
  font-family: 'Inter', sans-serif;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s;
}
.op-btn:hover:not(:disabled) {
  background: var(--surface-sunken);
  color: var(--brand-600);
}
.op-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.op-btn.is-edit {
  color: var(--brand-600);
  font-weight: 600;
  background: var(--brand-50);
}
.op-btn.is-edit:hover {
  background: var(--brand-100);
}
.op-btn.is-revert {
  color: #b45309;
}
.op-btn.is-danger:hover {
  color: var(--danger);
  background: rgba(239, 68, 68, 0.08);
}
.row-side {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.side-btn {
  width: 22px;
  height: 20px;
  border: 1px solid var(--border-subtle);
  border-radius: 5px;
  background: var(--surface);
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}
.side-btn:hover:not(:disabled) {
  border-color: var(--brand-300);
  color: var(--brand-600);
}
.side-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* 拖拽态 */
:deep(.drag-ghost) {
  opacity: 0.5;
  background: var(--brand-50) !important;
  border: 1px dashed var(--brand-400) !important;
}
:deep(.drag-chosen) {
  box-shadow: var(--shadow-pop);
}

/* ===================== 右：组卷栏 ===================== */
.pane-side {
  overflow-y: auto;
}
.side-block {
  padding: 16px;
  border-bottom: 1px solid var(--border-subtle);
}
.side-block:last-child {
  border-bottom: none;
}
.side-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 12px;
  letter-spacing: 0.4px;
}
.side-stat {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-bottom: 12px;
}
.stat-num {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-strong);
  line-height: 1;
  font-family: 'Inter', sans-serif;
}
.stat-unit {
  font-size: 12px;
  color: var(--text-muted);
}
.side-rows {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.side-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12.5px;
  color: var(--text-body);
}
.side-row-name {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-400);
}
.side-row-val {
  color: var(--text-muted);
  font-family: 'Inter', sans-serif;
}
.side-empty {
  font-size: 12px;
  color: var(--text-faint);
}
.side-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12.5px;
  color: var(--text-body);
  margin-bottom: 12px;
}
.side-field:last-child {
  margin-bottom: 0;
}
.side-field :deep(.el-select) {
  width: 130px;
}
.side-text {
  color: var(--text-muted);
}
.side-status {
  font-size: 11.5px;
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  font-weight: 500;
}
.side-status.ok {
  color: var(--success);
  background: rgba(16, 185, 129, 0.1);
}
.side-status.draft {
  color: var(--text-muted);
  background: var(--surface-sunken);
}
.side-ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.side-ops :deep(.el-button) {
  margin-left: 0;
  width: 100%;
}
.side-tip {
  margin: 12px 0 0;
  font-size: 11px;
  line-height: 1.65;
  color: var(--text-faint);
}

/* ===================== 弹窗通用 ===================== */
.qtype-chip,
.kp-chip {
  font-size: 11px;
  padding: 2px 9px;
  border-radius: var(--radius-pill);
}
.kp-chip {
  background: var(--surface-sunken);
  color: var(--text-muted);
}
.q-detail-meta {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  align-items: center;
}
.q-detail-stem {
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  color: var(--text-strong);
}
.q-detail-opts {
  margin: 8px 0 0 12px;
  font-size: 13px;
  color: var(--text-body);
  line-height: 1.9;
}
.q-detail-line {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-body);
  white-space: pre-wrap;
}

/* 编辑弹窗 */
.edit-alert {
  margin-bottom: 16px;
}
.edit-alert :deep(.el-alert__title) {
  font-size: 12.5px;
  line-height: 1.6;
}
.edit-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.edit-row {
  display: flex;
  align-items: center;
}
.edit-field {
  display: flex;
  gap: 12px;
}
.edit-label {
  flex: none;
  width: 42px;
  padding-top: 6px;
  font-size: 13px;
  color: var(--text-body);
  text-align: right;
}
.edit-opt-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.edit-opt-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.edit-opt-key {
  width: 18px;
  flex: none;
  font-weight: 600;
  color: var(--text-muted);
  font-family: 'Inter', sans-serif;
}
.edit-ans {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 2px;
}
.ans-chip {
  width: 30px;
  height: 30px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-base);
  border-radius: 7px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
  font-family: 'Inter', sans-serif;
}
.ans-chip:hover {
  border-color: var(--brand-500);
  color: var(--brand-500);
}
.ans-chip.on {
  background: var(--brand-500);
  border-color: var(--brand-500);
  color: #fff;
}

/* 导入弹窗 */
.import-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.import-tip {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 10px;
}
.import-list {
  max-height: 360px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.import-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.15s;
}
.import-item:hover {
  border-color: var(--brand-300);
  background: var(--surface-sunken);
}
.import-item.on {
  border-color: var(--brand-500);
  background: var(--brand-50);
  box-shadow: var(--ring-brand);
}
.import-item-main {
  flex: 1;
  min-width: 0;
}
.import-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.import-meta {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--text-muted);
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}
.import-check {
  flex: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid var(--border-base);
  display: grid;
  place-items: center;
  font-size: 12px;
  color: transparent;
}
.import-check.on {
  background: var(--brand-500);
  border-color: var(--brand-500);
  color: #fff;
}
.reuse-tip {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-body);
  margin: 0 0 16px;
}
.reuse-field {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
