<script setup lang="ts">
// 备课 · 试卷（BE-P-01~08）：列表 / 一键组卷 / 编排题目 / 派生给学生 / 预览
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchPapers,
  fetchPaperDetail,
  createPaper,
  updatePaper,
  deletePaper,
  clonePaper,
  setPaperQuestions,
  previewGeneratePaper,
  commitGeneratePaper,
  type PaperGeneratePreview
} from '@/api/paper'
import { fetchQuestions } from '@/api/question'
import { fetchKps } from '@/api/kp'
import { fetchStudents } from '@/api/student'
import { fetchDicts } from '@/api/dict'
import type { Dict, KnowledgePoint, Paper, PaperDetail, Question, Student } from '@/types'
import { errMsg } from '@/utils/error'

const loading = ref(false)
const router = useRouter()
const list = ref<Paper[]>([])
const students = ref<Student[]>([])
const grades = ref<Dict[]>([])
const kps = ref<KnowledgePoint[]>([])

const filter = reactive<{ grade?: string; keyword?: string }>({ grade: undefined, keyword: '' })

// ===================== 一键组卷（试抽 → 预览换题 → 落库） =====================
const genVisible = ref(false)
/** 步骤：pick=选题与设量，preview=预览换题 */
const genStep = ref<'pick' | 'preview'>('pick')
const gen = reactive({
  grade: '',
  kpIds: [] as number[],
  /** 每个知识点各自的题量 */
  counts: {} as Record<number, number>,
  maxTotal: 0,
  difficulty: 0,
  title: ''
})

/** 试抽结果 */
const genPreview = ref<PaperGeneratePreview | null>(null)
const genPicked = ref<Question[]>([])
/** 每个位置的"排除"集合：换一题时把旧题放进来，避免再抽到 */
const genExcluded = ref<Record<number, number[]>>({})
const genLoading = ref(false)
const genCommitting = ref(false)

const genKps = computed(() => kps.value.filter((k) => !gen.grade || k.grade === gen.grade || !k.grade))
const genSelectedKps = computed(() => genKps.value.filter((k) => gen.kpIds.includes(k.id)))
/** 期望总题数（含老师设量），最终可能因余量不足变少 */
const genWantedTotal = computed(() =>
  genSelectedKps.value.reduce((sum, k) => sum + (gen.counts[k.id] ?? 5), 0)
)
/** 抽题明细里"没抽够"的知识点 */
const genShortPicks = computed(() =>
  (genPreview.value?.picks || []).filter((p) => p.picked < p.wanted)
)

function openGenerate(): void {
  Object.assign(gen, { grade: filter.grade || '', kpIds: [], counts: {}, maxTotal: 0, difficulty: 0, title: '' })
  genStep.value = 'pick'
  genPreview.value = null
  genPicked.value = []
  genExcluded.value = {}
  genVisible.value = true
}

function toggleGenKp(kpId: number, on: boolean): void {
  if (on) {
    if (!gen.kpIds.includes(kpId)) gen.kpIds.push(kpId)
    if (gen.counts[kpId] == null) gen.counts[kpId] = 5
  } else {
    gen.kpIds = gen.kpIds.filter((x) => x !== kpId)
  }
}

function setGenCount(kpId: number, v: number | undefined): void {
  gen.counts[kpId] = v && v > 0 ? v : 1
}

function genTitle(): string {
  return (
    gen.title.trim() ||
    `${gen.grade ? gen.grade + ' ' : ''}${
      genSelectedKps.value.map((k) => k.name).slice(0, 2).join('·') || '知识点'
    } 专项`
  )
}

function genParams() {
  return {
    title: gen.title.trim() || undefined,
    grade: gen.grade || undefined,
    kpIds: gen.kpIds,
    kpCounts: gen.kpIds.map((id) => ({ kpId: id, count: gen.counts[id] ?? 5 })),
    maxTotal: gen.maxTotal > 0 ? gen.maxTotal : undefined,
    difficulty: gen.difficulty || undefined
  }
}

/** 试抽（首次进入预览，或点「换一批」重抽） */
async function doPreview(refresh = false): Promise<void> {
  if (!gen.kpIds.length) {
    ElMessage.warning('请至少勾选一个知识点')
    return
  }
  genLoading.value = true
  try {
    const res = await previewGeneratePaper(genParams())
    genPreview.value = res
    genPicked.value = res.questions
    genExcluded.value = {}
    genStep.value = 'preview'
    if (!res.questions.length) {
      ElMessage.warning('这些知识点下没有符合条件的题目')
    } else if (refresh) {
      ElMessage.success(`已重新抽取 ${res.total} 题`)
    }
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    genLoading.value = false
  }
}

/** 换一题：排除当前的，按该题知识点单独补抽一道 */
async function swapQuestion(index: number): Promise<void> {
  const cur = genPicked.value[index]
  if (!cur) return
  const kpId = cur.kpId ?? gen.kpIds[0]
  const excluded = genExcluded.value[index] || []
  excluded.push(cur.id)
  genExcluded.value[index] = excluded
  // 已在本卷中的题（除当前位）都不再抽
  const usedElsewhere = genPicked.value.filter((_, i) => i !== index).map((q) => q.id)
  genLoading.value = true
  try {
    const res = await previewGeneratePaper({
      title: gen.title.trim() || undefined,
      grade: gen.grade || undefined,
      kpIds: [kpId],
      kpCounts: [{ kpId, count: usedElsewhere.length + excluded.length + 1 }],
      difficulty: gen.difficulty || undefined
    })
    const candidate = res.questions.find(
      (q) => !usedElsewhere.includes(q.id) && !excluded.includes(q.id)
    )
    if (!candidate) {
      ElMessage.warning('该知识点下已经没有别的题可换了')
      return
    }
    genPicked.value[index] = candidate
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    genLoading.value = false
  }
}

/** 从预览里移除某题 */
function dropQuestion(index: number): void {
  genPicked.value.splice(index, 1)
}

/** 落库 */
async function doCommit(): Promise<void> {
  if (!genPicked.value.length) {
    ElMessage.warning('卷面为空，至少保留一道题')
    return
  }
  genCommitting.value = true
  try {
    const id = await commitGeneratePaper({
      title: genTitle(),
      grade: gen.grade || undefined,
      paperType: 'KP',
      questionIds: genPicked.value.map((q) => q.id)
    })
    genVisible.value = false
    await loadList()
    openDetailById(id)
    ElMessage.success(`已生成试卷（${genPicked.value.length} 题）`)
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    genCommitting.value = false
  }
}

// ===================== 新建空卷 =====================
const newVisible = ref(false)
const newForm = reactive({ title: '', grade: '', paperType: 'KP' as 'KP' | 'LESSON', studentId: undefined as number | undefined })

function openNew(): void {
  Object.assign(newForm, { title: '', grade: filter.grade || '', paperType: 'KP', studentId: undefined })
  newVisible.value = true
}

async function onCreate(): Promise<void> {
  if (!newForm.title.trim()) {
    ElMessage.warning('请填写卷名')
    return
  }
  try {
    const id = await createPaper({
      title: newForm.title.trim(),
      grade: newForm.grade || null,
      paperType: newForm.paperType,
      studentId: newForm.studentId ?? null
    })
    newVisible.value = false
    await loadList()
    openDetailById(id)
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

// ===================== 卷详情 / 编排 =====================
const detailVisible = ref(false)
const detail = ref<PaperDetail | null>(null)
const qPickerVisible = ref(false)
const pickLoading = ref(false)
const pickList = ref<Question[]>([])
const pickIds = ref<number[]>([])
const pickFilter = reactive<{ grade?: string; kpId?: number; keyword?: string }>({})

const previewVisible = ref(false)

/** 打开独立打印页（新标签，避免丢掉当前列表状态） */
function openPrint(): void {
  if (!detail.value) return
  const url = router.resolve({ name: 'PaperPrint', params: { id: detail.value.id } }).href
  window.open(url, '_blank')
}

function printPaper(row: Paper): void {
  const url = router.resolve({ name: 'PaperPrint', params: { id: row.id } }).href
  window.open(url, '_blank')
}

async function openDetailById(id: number): Promise<void> {
  try {
    detail.value = await fetchPaperDetail(id)
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

/** 保存编排（整体覆盖，数组顺序即题号） */
async function saveOrder(): Promise<void> {
  if (!detail.value) return
  try {
    await setPaperQuestions({
      paperId: detail.value.id,
      questionIds: detail.value.questions.map((q) => q.id)
    })
    detail.value.questionCount = detail.value.questions.length
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

async function removeQuestion(index: number): Promise<void> {
  if (!detail.value) return
  detail.value.questions.splice(index, 1)
  await saveOrder()
  loadList()
}

async function moveQuestion(index: number, dir: -1 | 1): Promise<void> {
  if (!detail.value) return
  const target = index + dir
  const arr = detail.value.questions
  if (target < 0 || target >= arr.length) return
  const tmp = arr[index]
  arr[index] = arr[target]
  arr[target] = tmp
  await saveOrder()
}

async function openPicker(): Promise<void> {
  if (!detail.value) return
  pickIds.value = []
  Object.assign(pickFilter, { grade: detail.value.grade || undefined, kpId: undefined, keyword: '' })
  pickPickerList()
  qPickerVisible.value = true
}

async function pickPickerList(): Promise<void> {
  pickLoading.value = true
  try {
    const res = await fetchQuestions({ ...pickFilter, page: 1, size: 100 })
    pickList.value = res.list
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    pickLoading.value = false
  }
}

function alreadyIn(qid: number): boolean {
  return !!detail.value?.questions.some((q) => q.id === qid)
}

async function confirmPick(): Promise<void> {
  if (!detail.value || !pickIds.value.length) {
    qPickerVisible.value = false
    return
  }
  const exist = detail.value.questions.map((q) => q.id)
  const added: Question[] = []
  for (const id of pickIds.value) {
    if (exist.includes(id)) continue
    const q = pickList.value.find((x) => x.id === id)
    if (q) added.push(q)
  }
  detail.value.questions = [...detail.value.questions, ...added]
  qPickerVisible.value = false
  await saveOrder()
  loadList()
  ElMessage.success(`已加入 ${added.length} 题`)
}

async function toggleStatus(): Promise<void> {
  if (!detail.value) return
  const next = detail.value.status === 'READY' ? 'DRAFT' : 'READY'
  try {
    await updatePaper({ id: detail.value.id, status: next })
    detail.value.status = next
    loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

// ===================== 派生给学生 =====================
const cloneVisible = ref(false)
const cloneTarget = ref<Paper | null>(null)
const cloneStudentId = ref<number | undefined>(undefined)

function openClone(row: Paper): void {
  cloneTarget.value = row
  cloneStudentId.value = undefined
  cloneVisible.value = true
}

async function doClone(): Promise<void> {
  if (!cloneTarget.value || !cloneStudentId.value) {
    ElMessage.warning('请选择学生')
    return
  }
  try {
    const id = await clonePaper({ id: cloneTarget.value.id, studentId: cloneStudentId.value })
    cloneVisible.value = false
    await loadList()
    openDetailById(id)
    ElMessage.success('已派生一份学生专属卷')
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

async function onDelete(row: Paper): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除试卷「${row.title}」？\n编排与课次关联会一并清理，题目仍保留在题库。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deletePaper(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
  }
}

// ===================== 通用 =====================
async function loadList(): Promise<void> {
  loading.value = true
  try {
    list.value = await fetchPapers({ grade: filter.grade, keyword: filter.keyword || undefined })
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

async function loadRefs(): Promise<void> {
  try {
    students.value = await fetchStudents({ page: 1, size: 200 }).then((r) => r.list)
  } catch {
    students.value = []
  }
  try {
    grades.value = await fetchDicts('grade')
  } catch {
    grades.value = []
  }
  try {
    kps.value = await fetchKps()
  } catch {
    kps.value = []
  }
}

function parseOptions(options: string | null): string[] {
  if (!options) return []
  try {
    const arr = JSON.parse(options)
    return Array.isArray(arr) ? (arr as string[]) : []
  } catch {
    return []
  }
}

onMounted(() => {
  loadRefs()
  loadList()
})
</script>

<template>
  <el-card>
    <div class="toolbar">
      <div class="filters">
        <el-select v-model="filter.grade" placeholder="全部年级" clearable style="width: 120px" @change="loadList">
          <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
        </el-select>
        <el-input
          v-model="filter.keyword"
          placeholder="搜索卷名"
          clearable
          style="width: 180px"
          @keyup.enter="loadList"
        />
        <el-button type="primary" @click="loadList">查询</el-button>
      </div>
      <div class="actions">
        <el-button type="primary" @click="openGenerate">一键组卷</el-button>
        <el-button @click="openNew">新建空卷</el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      empty-text="还没有试卷，用「一键组卷」按知识点快速生成一份"
    >
      <el-table-column prop="title" label="卷名" min-width="220" />
      <el-table-column prop="grade" label="年级" width="90" align="center">
        <template #default="{ row }">{{ row.grade || '-' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.paperType === 'LESSON' ? '课时题单' : '知识点卷' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="归属" width="130">
        <template #default="{ row }">
          <span v-if="row.studentName">{{ row.studentName }}</span>
          <el-tag v-else size="small" type="info" effect="plain">通用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="题数" width="80" align="center">
        <template #default="{ row }">{{ row.questionCount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'READY' ? 'success' : 'info'">
            {{ row.status === 'READY' ? '可用' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetailById(row.id)">查看</el-button>
          <el-button link type="primary" @click="printPaper(row)">打印</el-button>
          <el-button link type="primary" @click="openClone(row)">派给学生</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 一键组卷：试抽 → 预览换题 → 落库 -->
    <el-dialog v-model="genVisible" title="一键组卷" width="720px" top="5vh">
      <el-steps :active="genStep === 'pick' ? 0 : 1" simple style="margin-bottom: 16px">
        <el-step title="选知识点 · 设题量" />
        <el-step title="预览卷面 · 逐题替换" />
      </el-steps>

      <!-- 步骤一：选题与设量 -->
      <el-form v-if="genStep === 'pick'" label-width="90px">
        <el-form-item label="年级">
          <el-select v-model="gen.grade" placeholder="不限" clearable style="width: 200px">
            <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识点">
          <div class="kp-pick">
            <div
              v-for="k in genKps"
              :key="k.id"
              class="kp-row"
              :class="{ disabled: k.questionCount === 0 }"
            >
              <el-checkbox
                :model-value="gen.kpIds.includes(k.id)"
                :disabled="k.questionCount === 0"
                @change="(v: boolean | string | number) => toggleGenKp(k.id, !!v)"
              />
              <span class="kp-name">{{ k.name }}</span>
              <span class="kp-count">{{ k.questionCount === 0 ? '暂无题' : k.questionCount + ' 题' }}</span>
              <el-input-number
                v-if="gen.kpIds.includes(k.id)"
                :model-value="gen.counts[k.id] ?? 5"
                :min="1"
                :max="Math.max(1, k.questionCount)"
                size="small"
                controls-position="right"
                style="width: 96px"
                @change="(v: number | undefined) => setGenCount(k.id, v)"
              />
            </div>
            <el-empty v-if="!genKps.length" description="没有匹配的知识点" :image-size="60" />
          </div>
        </el-form-item>
        <el-form-item label="整卷上限">
          <el-input-number v-model="gen.maxTotal" :min="0" :max="200" />
          <span class="tip" style="margin-left: 8px">0 = 不限</span>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="gen.difficulty" style="width: 160px">
            <el-option :value="0" label="不限" />
            <el-option :value="1" label="易" />
            <el-option :value="2" label="中" />
            <el-option :value="3" label="难" />
          </el-select>
        </el-form-item>
        <el-form-item label="卷名">
          <el-input v-model="gen.title" placeholder="留空自动命名" />
        </el-form-item>
        <el-form-item label=" ">
          <span class="tip">
            已选 {{ gen.kpIds.length }} 个知识点，期望成卷 {{ genWantedTotal }} 题（题量不足的知识点按现有题量抽取）
          </span>
        </el-form-item>
      </el-form>

      <!-- 步骤二：预览与换题 -->
      <template v-else>
        <div class="preview-bar">
          <span class="preview-count">
            共 <b>{{ genPicked.length }}</b> 题
          </span>
          <el-button size="small" :loading="genLoading" @click="doPreview(true)">换一批</el-button>
          <el-button size="small" @click="genStep = 'pick'">返回调整</el-button>
        </div>
        <el-alert
          v-if="genShortPicks.length"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 10px"
        >
          <template #title>
            {{ genShortPicks.map((p) => `${p.kpName || '未命名'}：想抽 ${p.wanted} 题，题库仅 ${p.available} 题`).join('；') }}
          </template>
        </el-alert>
        <el-empty v-if="!genPicked.length" description="没有抽到题目，返回上一步调整条件" :image-size="60" />
        <div v-else class="q-list" v-loading="genLoading">
          <div v-for="(q, i) in genPicked" :key="`${q.id}-${i}`" class="q-item">
            <span class="q-no">{{ i + 1 }}</span>
            <div class="q-body">
              <div class="q-stem">{{ q.stem }}</div>
              <div v-if="parseOptions(q.options).length" class="q-opts-preview">
                <span v-for="(o, oi) in parseOptions(q.options)" :key="oi" class="q-opt">
                  {{ String.fromCharCode(65 + oi) }}. {{ o }}
                </span>
              </div>
              <div class="q-sub">
                <span>{{ q.kpName || '未标知识点' }}</span>
                <span v-if="q.qtype"> · {{ q.qtype }}</span>
              </div>
            </div>
            <div class="q-ops">
              <el-button link type="primary" @click="swapQuestion(i)">换一题</el-button>
              <el-button link type="danger" @click="dropQuestion(i)">去掉</el-button>
            </div>
          </div>
        </div>
      </template>

      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button v-if="genStep === 'pick'" type="primary" :loading="genLoading" @click="doPreview(false)">
          试抽预览
        </el-button>
        <el-button v-else type="primary" :loading="genCommitting" @click="doCommit">
          确认生成试卷
        </el-button>
      </template>
    </el-dialog>

    <!-- 新建空卷 -->
    <el-dialog v-model="newVisible" title="新建空卷" width="480px">
      <el-form label-width="80px">
        <el-form-item label="卷名">
          <el-input v-model="newForm.title" placeholder="如：五年级 病句 专项" />
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="newForm.grade" placeholder="不限" clearable style="width: 100%">
            <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="newForm.paperType" style="width: 100%">
            <el-option value="KP" label="知识点专项卷" />
            <el-option value="LESSON" label="课时题单" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属">
          <el-select v-model="newForm.studentId" placeholder="不选 = 通用卷" clearable style="width: 100%">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newVisible = false">取消</el-button>
        <el-button type="primary" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 卷详情 -->
    <el-drawer v-model="detailVisible" size="680px">
      <template #header>
        <div class="detail-head">
          <span class="detail-title">{{ detail?.title }}</span>
          <el-tag v-if="detail?.studentName" size="small" type="warning" effect="plain">
            {{ detail.studentName }} 专属
          </el-tag>
          <el-tag v-else size="small" type="info" effect="plain">通用卷</el-tag>
        </div>
      </template>
      <template v-if="detail">
        <div class="detail-meta">
          <span>{{ detail.grade || '不限年级' }}</span>
          <span>共 {{ detail.questions.length }} 题</span>
          <span v-if="detail.parentTitle" class="muted">派生自：{{ detail.parentTitle }}</span>
        </div>
        <div class="detail-actions">
          <el-button size="small" @click="openPicker">从题库加题</el-button>
          <el-button size="small" @click="previewVisible = true">预览卷面</el-button>
          <el-button size="small" @click="openPrint">打印</el-button>
          <el-button size="small" :type="detail.status === 'READY' ? 'warning' : 'success'" @click="toggleStatus">
            {{ detail.status === 'READY' ? '退回草稿' : '标记可用' }}
          </el-button>
        </div>
        <el-empty v-if="!detail.questions.length" description="卷里还没有题，点「从题库加题」" :image-size="60" />
        <div v-else class="q-list">
          <div v-for="(q, i) in detail.questions" :key="q.id" class="q-item">
            <span class="q-no">{{ i + 1 }}</span>
            <div class="q-body">
              <div class="q-stem">{{ q.stem }}</div>
              <div class="q-sub">
                <span>{{ q.kpName || '未标知识点' }}</span>
                <span v-if="q.qtype"> · {{ q.qtype }}</span>
              </div>
            </div>
            <div class="q-ops">
              <el-button link type="primary" :disabled="i === 0" @click="moveQuestion(i, -1)">上移</el-button>
              <el-button link type="primary" :disabled="i === detail.questions.length - 1" @click="moveQuestion(i, 1)">下移</el-button>
              <el-button link type="danger" @click="removeQuestion(i)">移出</el-button>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 从题库选题 -->
    <el-dialog v-model="qPickerVisible" title="从题库加题" width="760px">
      <div class="filters" style="margin-bottom: 12px">
        <el-select v-model="pickFilter.grade" placeholder="全部年级" clearable style="width: 120px">
          <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
        </el-select>
        <el-select v-model="pickFilter.kpId" placeholder="全部知识点" clearable filterable style="width: 190px">
          <el-option v-for="k in kps" :key="k.id" :label="k.name" :value="k.id" />
        </el-select>
        <el-input v-model="pickFilter.keyword" placeholder="搜索题干" clearable style="width: 180px" @keyup.enter="pickPickerList" />
        <el-button type="primary" @click="pickPickerList">查询</el-button>
      </div>
      <el-table
        v-loading="pickLoading"
        :data="pickList"
        height="360"
        border
        @selection-change="(rows: Question[]) => (pickIds = rows.map((r) => r.id))"
      >
        <el-table-column type="selection" width="46" :selectable="(row: Question) => !alreadyIn(row.id)" />
        <el-table-column label="题干" min-width="300">
          <template #default="{ row }">
            <span>{{ row.stem.split('\n')[0] }}</span>
            <el-tag v-if="alreadyIn(row.id)" size="small" type="info" style="margin-left: 6px">已在卷中</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="kpName" label="知识点" width="140" />
        <el-table-column prop="qtype" label="题型" width="80" align="center" />
      </el-table>
      <template #footer>
        <span class="tip" style="margin-right: auto">已选 {{ pickIds.length }} 题</span>
        <el-button @click="qPickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPick">加入试卷</el-button>
      </template>
    </el-dialog>

    <!-- 卷面预览（学生版：无答案、带作答留白；打印走独立整页） -->
    <el-dialog v-model="previewVisible" title="卷面预览（学生版）" width="760px" top="5vh">
      <div v-if="detail" class="paper-preview">
        <div class="paper-meta-row">
          <span>姓名：______</span>
          <span>班级：______</span>
          <span>日期：______</span>
          <span>得分：______</span>
        </div>
        <h3 class="paper-title">{{ detail.title }}</h3>
        <div v-for="(q, i) in detail.questions" :key="q.id" class="paper-q">
          <div class="paper-stem">{{ i + 1 }}. {{ q.stem }}</div>
          <div v-if="parseOptions(q.options).length" class="paper-opts">
            <div v-for="(o, oi) in parseOptions(q.options)" :key="oi">
              {{ String.fromCharCode(65 + oi) }}. {{ o }}
            </div>
          </div>
          <div class="paper-blank"></div>
        </div>
      </div>
      <template #footer>
        <span class="tip" style="margin-right: auto">学生版不含答案，打印后即为可直接下发的卷面</span>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="openPrint">打印 / 导出 PDF</el-button>
      </template>
    </el-dialog>

    <!-- 派生给学生 -->
    <el-dialog v-model="cloneVisible" title="派发给学生" width="420px">
      <p class="tip">
        将复制整卷（含 {{ cloneTarget?.questionCount || 0 }} 道题），之后修改这份卷<b>不影响</b>原通用卷。
      </p>
      <el-select v-model="cloneStudentId" placeholder="选择学生" filterable style="width: 100%">
        <el-option v-for="s in students" :key="s.id" :label="`${s.name}${s.grade ? '（' + s.grade + '）' : ''}`" :value="s.id" />
      </el-select>
      <template #footer>
        <el-button @click="cloneVisible = false">取消</el-button>
        <el-button type="primary" @click="doClone">确认派生</el-button>
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
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.filters,
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.kp-pick {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 260px;
  overflow-y: auto;
  width: 100%;
}
.kp-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border-radius: 6px;
}
.kp-row:hover {
  background: var(--surface-sunken);
}
.kp-row.disabled {
  opacity: 0.5;
}
.kp-name {
  font-size: 13px;
}
.kp-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-muted);
}
.preview-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.preview-count {
  margin-right: auto;
  font-size: 13px;
  color: var(--text-muted);
}
.q-opts-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}
.q-opt {
  white-space: nowrap;
}
.detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.detail-title {
  font-weight: 600;
  font-size: 15px;
}
.detail-meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 10px;
}
.detail-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.q-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.q-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 8px 10px;
}
.q-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--brand-50);
  color: var(--brand-500);
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.q-body {
  flex: 1;
  min-width: 0;
}
.q-stem {
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}
.q-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-muted);
}
.q-ops {
  flex: none;
}
.paper-preview {
  padding: 0 8px;
  font-family: 'Songti SC', 'SimSun', 'STSong', serif;
}
.paper-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 24px;
  font-size: 13px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-base);
  margin-bottom: 12px;
}
.paper-title {
  text-align: center;
  font-size: 16px;
  margin: 0 0 16px;
}
.paper-q {
  margin-bottom: 14px;
}
.paper-stem {
  font-size: 13px;
  white-space: pre-wrap;
}
.paper-opts {
  margin: 4px 0 0 16px;
  font-size: 13px;
  color: var(--text-muted);
}
.paper-blank {
  margin: 6px 0 0 16px;
  height: 22px;
  border-bottom: 1px solid var(--border-subtle);
}
.tip {
  font-size: 12px;
  color: var(--text-muted);
}
.muted {
  color: var(--text-muted);
}
</style>
