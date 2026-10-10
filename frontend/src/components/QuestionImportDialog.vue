<script setup lang="ts">
// 题库批量导入
// 流程：粘贴整卷 → 规则切分（不联网）→（可选）AI 补答案/解析/难度/知识点
//      → 结构化核对（缺项/重复题一眼可见）→ 勾选入库
// 未配 DeepSeek Key 时，AI 那一步会失败并给出提示，但不影响手工核对与入库。
//
// 逐题核对区为什么用表格而不是卡片堆：
// 卡片能看清一道题、看不清"这批题哪几道缺答案"；导入场景要先扫全局再挑毛病，
// 所以列式展示（题型/题干/选项/答案/知识点/难度）+ 缺项高亮。
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { parseQuestions, type ParsedQuestion } from '@/utils/questionParser'
import { batchCreateQuestions, checkQuestionDuplicates } from '@/api/question'
import { parseQuestionsByAi } from '@/api/ai'
import { fetchKps } from '@/api/kp'
import { fetchDicts } from '@/api/dict'
import { QTYPES, DIFFICULTY_LABEL } from '@/constants/question'
import { errMsg } from '@/utils/error'
import type { Dict, KnowledgePoint, Question } from '@/types'

/** 切分结果 + 核对阶段补上的字段 */
interface ImportRow extends ParsedQuestion {
  kpId: number | null
  kpName: string | null
  /** 是否勾选入库 */
  checked: boolean
  /** 加入本批的时间戳，仅用于 key，避免用下标造成输入框串行 */
  uid: number
  /** 查重命中的题库题 id（null = 未命中） */
  dupId: number | null
  /** 查重命中的题干摘要 */
  dupStem: string | null
  /** true = 与题库某题题干完全相同 */
  dupExact: boolean
}

/** 切分格式示例：给老师看"怎么写才切得准"，替代竞品的模板下载 */
const FORMAT_SAMPLE = `1. 下面句子使用了什么修辞手法？（　）
A. 拟人
B. 比喻
C. 夸张
D. 排比
答案：B
解析：把月亮比作小船，是比喻。

2. 「春风又绿江南岸」中的「绿」字用得极妙，它写出了______。
答案：春风的生机与动态美`

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 继承题库页当前的筛选条件，省一次选择 */
    defaultGrade?: string
    defaultKpId?: number | null
  }>(),
  { defaultGrade: '', defaultKpId: null }
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'imported', count: number): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const rawText = ref('')
const rows = ref<ImportRow[]>([])
const kps = ref<KnowledgePoint[]>([])
const grades = ref<Dict[]>([])
const aiLoading = ref(false)
const importing = ref(false)
const dupLoading = ref(false)
const aiFilled = ref(false)
const showSample = ref(false)

let uidSeq = 1

/** 这批题的公共属性（行内没单独指定时用它兜底） */
const batch = reactive<{ grade: string; kpId: number | null; source: string }>({
  grade: '',
  kpId: null,
  source: ''
})

watch(visible, async (v) => {
  if (!v) return
  batch.grade = props.defaultGrade || ''
  batch.kpId = props.defaultKpId ?? null
  aiFilled.value = false
  await loadOptions()
})

/** 拉年级与知识点（知识点既作 AI 白名单，也作手工指定） */
async function loadOptions(): Promise<void> {
  try {
    grades.value = await fetchDicts('grade')
  } catch {
    grades.value = []
  }
  try {
    kps.value = await fetchKps(batch.grade ? { grade: batch.grade } : {})
  } catch {
    kps.value = []
  }
}

// ===================== 核对统计 =====================
/** 已勾选、且可入库的行 */
const checkedRows = computed(() => rows.value.filter((r) => r.checked && r.stem.trim()))
/** 缺答案的行（选择题/判断题必须有答案，其余科目标注但不算错） */
function missingAnswer(r: ImportRow): boolean {
  return !r.answer.trim()
}
/** 缺知识点的行 */
function missingKp(r: ImportRow): boolean {
  return !(r.kpId ?? batch.kpId ?? null)
}
const statMissingAnswer = computed(() => rows.value.filter(missingAnswer).length)
const statMissingKp = computed(() => rows.value.filter(missingKp).length)
const statDup = computed(() => rows.value.filter((r) => r.dupId !== null).length)

/** 表格行样式：重复题最优先提示，其次缺项 */
function rowClass({ row }: { row: ImportRow }): string {
  if (row.dupId !== null) return 'row-dup'
  if (missingAnswer(row) || missingKp(row)) return 'row-warn'
  return ''
}

// ===================== 切分 =====================
function makeRow(q: ParsedQuestion): ImportRow {
  return {
    ...q,
    kpId: null,
    kpName: null,
    checked: true,
    uid: uidSeq++,
    dupId: null,
    dupStem: null,
    dupExact: false
  }
}

/** ① 智能切分（纯前端规则，不联网） */
async function onSplit(): Promise<void> {
  const parsed = parseQuestions(rawText.value)
  if (!parsed.length) {
    ElMessage.warning('没切出题目，检查一下是不是还没粘贴内容')
    return
  }
  rows.value = parsed.map(makeRow)
  aiFilled.value = false
  ElMessage.success(`已切出 ${parsed.length} 道题，请核对后入库`)
  checkDups()
}

/** ② 查重：一次把本批题干交给后端比对，命中就在行上标注 */
async function checkDups(): Promise<void> {
  if (!rows.value.length) return
  dupLoading.value = true
  try {
    const res = await checkQuestionDuplicates(rows.value.map((r) => r.stem))
    rows.value.forEach((row, i) => {
      const d = res[i]
      row.dupId = d?.dupId ?? null
      row.dupStem = d?.dupStem ?? null
      row.dupExact = !!d?.exact
      // 与题库完全相同的题默认不勾选，避免同题反复入库；相似题保留勾选由老师判断
      if (row.dupExact) row.checked = false
    })
  } catch {
    // 查重非关键路径，失败静默，不阻断导入
  } finally {
    dupLoading.value = false
  }
}

// ===================== 切分修正（合并 / 拆分） =====================
/** 题干按空行拆段，用于「拆分为两题」时找一个自然断点 */
function splitPoint(stem: string): number {
  const paras = stem.split(/\n\s*\n/)
  if (paras.length >= 2 && paras[0].trim()) return paras[0].trim().length
  const lines = stem.split('\n')
  if (lines.length >= 2) {
    const half = Math.ceil(lines.length / 2)
    return lines.slice(0, half).join('\n').length
  }
  // 单行无断点：返回 0 让调用方提示老师手工处理
  return 0
}

/** 并入上一题：题干接上一行，选项/答案/解析一并合并 */
function mergeToPrev(i: number): void {
  if (i <= 0) return
  const prev = rows.value[i - 1]
  const cur = rows.value[i]
  prev.stem = [prev.stem, cur.stem].filter(Boolean).join('\n')
  if (cur.options.length) prev.options = [...prev.options, ...cur.options]
  if (!prev.answer && cur.answer) prev.answer = cur.answer
  if (!prev.analysis && cur.analysis) prev.analysis = cur.analysis
  prev.qtype = prev.options.length >= 2 ? '选择' : prev.qtype
  rows.value.splice(i, 1)
}

/** 拆分为两题：在自然断点处切成两道，后半段保留原行属性 */
function splitRow(i: number): void {
  const row = rows.value[i]
  const at = splitPoint(row.stem)
  if (!at) {
    ElMessage.warning('题干只有一行、找不到断点，请手工改原文后重新切分')
    return
  }
  const first = row.stem.slice(0, at).trim()
  const rest = row.stem.slice(at).trim()
  if (!first || !rest) {
    ElMessage.warning('切不出有效的两段，请检查题干')
    return
  }
  row.stem = first
  const tail = makeRow({
    stem: rest,
    qtype: row.qtype,
    options: [],
    answer: '',
    analysis: '',
    difficulty: row.difficulty
  })
  tail.kpId = row.kpId
  tail.kpName = row.kpName
  rows.value.splice(i + 1, 0, tail)
}

function onRemove(i: number): void {
  rows.value.splice(i, 1)
}

// ===================== AI 补全 =====================
/** 把一行还原成给 AI 的文本 */
function toItemText(r: ImportRow): string {
  const parts = [r.stem]
  if (r.options.length) parts.push(r.options.join('\n'))
  if (r.answer) parts.push(`答案：${r.answer}`)
  if (r.analysis) parts.push(`解析：${r.analysis}`)
  return parts.join('\n')
}

async function onAiFill(): Promise<void> {
  const targets = checkedRows.value
  if (!targets.length) {
    ElMessage.warning('请先勾选要处理的题')
    return
  }
  if (targets.length > 30) {
    ElMessage.warning('一次最多交给 AI 解析 30 道题，请先取消勾选多余的')
    return
  }
  aiLoading.value = true
  try {
    const res = await parseQuestionsByAi({
      items: targets.map(toItemText),
      grade: batch.grade || undefined,
      source: batch.source || undefined,
      kpOptions: kps.value.map((k) => ({ id: k.id, name: k.name }))
    })
    targets.forEach((row, i) => {
      const r = res[i]
      if (!r) return
      row.stem = r.stem?.trim() || row.stem
      row.qtype = r.qtype || row.qtype
      row.options = r.options?.length ? r.options : row.options
      row.answer = r.answer || row.answer
      row.analysis = r.analysis || row.analysis
      row.difficulty = r.difficulty || row.difficulty
      if (r.kpId != null) {
        row.kpId = r.kpId
        row.kpName = r.kpName
      }
    })
    aiFilled.value = true
    ElMessage.success('AI 已补全，请核对后入库')
  } catch (e) {
    ElMessage.warning(`AI 补全不可用：${errMsg(e)}。可以手工填写后直接入库`)
  } finally {
    aiLoading.value = false
  }
}

// ===================== 入库 =====================
function toggleAll(on: boolean): void {
  rows.value.forEach((r) => (r.checked = on))
}

async function onImport(): Promise<void> {
  const valid = checkedRows.value
  if (!valid.length) {
    ElMessage.warning('没有勾选可入库的题目')
    return
  }
  importing.value = true
  try {
    const payload: Partial<Question>[] = valid.map((r) => ({
      grade: batch.grade || undefined,
      kpId: r.kpId ?? batch.kpId ?? undefined,
      qtype: r.qtype || undefined,
      stem: r.stem.trim(),
      options: r.options.length ? JSON.stringify(r.options) : undefined,
      answer: r.answer || undefined,
      analysis: r.analysis || undefined,
      difficulty: r.difficulty || 2,
      source: batch.source || undefined
    }))
    const count = await batchCreateQuestions(payload)
    const skipped = valid.length - count
    ElMessage.success(skipped > 0 ? `已入库 ${count} 道题（${skipped} 道重复已跳过）` : `已入库 ${count} 道题`)
    emit('imported', count)
    visible.value = false
    rawText.value = ''
    rows.value = []
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    importing.value = false
  }
}

/** 选中行的选项文案（表格里只显示数量） */
function optionCount(r: ImportRow): string {
  return r.options.length ? `${r.options.length} 项` : '—'
}

function kpLabel(r: ImportRow): string {
  if (r.kpName) return r.kpName
  if (r.kpId == null && batch.kpId != null) {
    const k = kps.value.find((x) => x.id === batch.kpId)
    return k ? `${k.name}（批次）` : '批次兜底'
  }
  return '未指定'
}
</script>

<template>
  <el-dialog v-model="visible" title="批量导入题目" width="1180px" top="4vh" class="import-dialog">
    <div class="import-layout">
      <!-- 左：粘贴 + 公共属性 -->
      <div class="pane pane-left">
        <div class="pane-title">
          <span>① 粘贴卷子原文</span>
          <el-button link type="primary" size="small" @click="showSample = !showSample">
            {{ showSample ? '收起格式示例' : '格式示例' }}
          </el-button>
        </div>
        <div v-if="showSample" class="sample-box">
          <div class="sample-head">
            <span>按这个格式粘，切分最准</span>
            <el-button link type="primary" size="small" @click="rawText = FORMAT_SAMPLE">填入示例</el-button>
          </div>
          <pre class="sample-text">{{ FORMAT_SAMPLE }}</pre>
          <div class="sample-tip">题号用「1. / 2、」；选项用「A. / B、」；答案写「答案：」；解析写「解析：」</div>
        </div>
        <el-input
          v-model="rawText"
          type="textarea"
          :rows="12"
          placeholder="从 Word / PDF 里整段复制粘贴，题号（1. 2. 一、）会自动识别切成多道题"
        />
        <el-button
          type="primary"
          class="split-btn"
          :disabled="!rawText.trim()"
          @click="onSplit"
        >
          智能切分
        </el-button>

        <div class="pane-title pane-title-gap">② 这批题的公共属性</div>
        <el-form label-width="60px" size="small">
          <el-form-item label="年级">
            <el-select v-model="batch.grade" placeholder="不限" clearable style="width: 100%" @change="loadOptions">
              <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
            </el-select>
          </el-form-item>
          <el-form-item label="知识点">
            <el-select v-model="batch.kpId" placeholder="行内没指定时用它兜底" clearable filterable style="width: 100%">
              <el-option v-for="k in kps" :key="k.id" :label="k.name" :value="k.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源">
            <el-input v-model="batch.source" placeholder="如 2024 期末卷（选填）" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 右：结构化核对 -->
      <div class="pane pane-right">
        <div class="pane-title">
          <span>③ 核对（{{ checkedRows.length }} / {{ rows.length }} 道待入库）</span>
          <span class="pane-actions">
            <el-tag v-if="aiFilled" size="small" type="success" effect="plain">AI 已补全</el-tag>
            <el-button size="small" :loading="dupLoading" :disabled="!rows.length" @click="checkDups">
              重新查重
            </el-button>
            <el-button size="small" :loading="aiLoading" :disabled="!rows.length" @click="onAiFill">
              AI 补答案与解析
            </el-button>
          </span>
        </div>

        <!-- 体检条：缺项 / 重复题一眼可见 -->
        <div v-if="rows.length" class="health">
          <span class="health-item" :class="{ bad: statMissingAnswer > 0 }">
            缺答案 <b>{{ statMissingAnswer }}</b>
          </span>
          <span class="health-item" :class="{ bad: statMissingKp > 0 }">
            缺知识点 <b>{{ statMissingKp }}</b>
          </span>
          <span class="health-item" :class="{ bad: statDup > 0 }">
            疑似重复 <b>{{ statDup }}</b>
          </span>
          <span class="health-hint">缺项不阻止入库，重复题默认不勾选</span>
        </div>

        <el-empty v-if="!rows.length" description="切分结果会显示在这里" :image-size="70" />

        <el-table
          v-else
          :data="rows"
          size="small"
          height="calc(100vh - 340px)"
          :row-class-name="rowClass"
          class="check-table"
        >
          <el-table-column width="40" align="center">
            <template #header>
              <el-checkbox
                :model-value="rows.length > 0 && checkedRows.length === rows.length"
                :indeterminate="checkedRows.length > 0 && checkedRows.length < rows.length"
                @change="(v: boolean | string | number) => toggleAll(!!v)"
              />
            </template>
            <template #default="{ row }">
              <el-checkbox v-model="row.checked" />
            </template>
          </el-table-column>

          <el-table-column label="#" width="36" align="center">
            <template #default="{ $index }">
              <span class="row-no">{{ $index + 1 }}</span>
            </template>
          </el-table-column>

          <el-table-column label="题型" width="92">
            <template #default="{ row }">
              <el-select v-model="row.qtype" size="small">
                <el-option v-for="t in QTYPES" :key="t" :label="t" :value="t" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="题干" min-width="260">
            <template #default="{ row }">
              <el-input v-model="row.stem" type="textarea" :rows="2" size="small" />
              <div v-if="row.dupId !== null" class="dup-line">
                <el-tag size="small" type="warning" effect="plain">
                  {{ row.dupExact ? '与题库 #' + row.dupId + ' 完全相同' : '疑似与 #' + row.dupId + ' 重复' }}
                </el-tag>
                <span class="dup-stem">{{ row.dupStem }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="选项" width="70" align="center">
            <template #default="{ row }">
              <span class="cell-muted">{{ optionCount(row) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="答案" width="150">
            <template #default="{ row }">
              <el-input v-model="row.answer" size="small" placeholder="必填" :class="{ 'input-missing': missingAnswer(row) }" />
            </template>
          </el-table-column>

          <el-table-column label="知识点" width="150">
            <template #default="{ row }">
              <el-select
                v-model="row.kpId"
                size="small"
                clearable
                filterable
                placeholder="未指定"
                :class="{ 'input-missing': missingKp(row) }"
              >
                <el-option v-for="k in kps" :key="k.id" :label="k.name" :value="k.id" />
              </el-select>
              <div class="cell-muted kp-fallback">{{ kpLabel(row) }}</div>
            </template>
          </el-table-column>

          <el-table-column label="难度" width="80">
            <template #default="{ row }">
              <el-select v-model="row.difficulty" size="small">
                <el-option v-for="(lab, val) in DIFFICULTY_LABEL" :key="val" :label="lab" :value="Number(val)" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="132" fixed="right">
            <template #default="{ $index }">
              <el-button link type="primary" size="small" :disabled="$index === 0" @click="mergeToPrev($index)">
                并入上一题
              </el-button>
              <el-button link type="primary" size="small" @click="splitRow($index)">拆分</el-button>
              <el-button link type="danger" size="small" @click="onRemove($index)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <span class="footer-tip">缺答案/知识点的题可以先入库，之后再补；重复题默认不勾选</span>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="importing"
        :disabled="!checkedRows.length"
        @click="onImport"
      >
        批量入库（{{ checkedRows.length }}）
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.import-dialog :deep(.el-dialog__body) {
  padding-top: 6px;
}
.import-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 2fr);
  gap: 16px;
}
.pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.pane-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-strong);
}
.pane-title-gap {
  margin-top: 16px;
}
.pane-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.split-btn {
  margin-top: 10px;
  align-self: flex-start;
}

/* 格式示例 */
.sample-box {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--surface-sunken);
  padding: 10px 12px;
  margin-bottom: 8px;
}
.sample-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}
.sample-text {
  margin: 0;
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-body);
  white-space: pre-wrap;
  word-break: break-word;
}
.sample-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-faint);
}

/* 体检条 */
.health {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 7px 10px;
  margin-bottom: 8px;
  border-radius: var(--radius-sm);
  background: var(--surface-sunken);
  font-size: 12px;
  color: var(--text-muted);
}
.health-item b {
  font-weight: 500;
  color: var(--text-strong);
}
.health-item.bad b {
  color: var(--warning);
}
.health-hint {
  margin-left: auto;
  color: var(--text-faint);
}

/* 核对表格 */
.check-table {
  flex: 1;
}
.row-no {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: var(--radius-xs);
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 11px;
  font-weight: 500;
}
.cell-muted {
  font-size: 12px;
  color: var(--text-faint);
}
.kp-fallback {
  margin-top: 2px;
  line-height: 1.4;
}
.dup-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  min-width: 0;
}
.dup-stem {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-faint);
}
/* 缺项输入框给一层浅橙底，不靠颜色也能看出（表格行整体标黄） */
.input-missing :deep(.el-input__wrapper),
.input-missing :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--warning) inset;
}
.check-table :deep(.row-warn) {
  background: rgba(245, 158, 11, 0.05);
}
.check-table :deep(.row-dup) {
  background: rgba(245, 158, 11, 0.1);
}
.footer-tip {
  margin-right: auto;
  font-size: 12px;
  color: var(--text-faint);
}
</style>
