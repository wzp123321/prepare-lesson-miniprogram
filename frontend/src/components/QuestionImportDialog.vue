<script setup lang="ts">
// 题库批量导入
// 流程：粘贴整卷 → 规则切分（不联网）→（可选）AI 补答案/解析/难度/知识点 → 核对 → 批量入库
// 未配 DeepSeek Key 时，AI 那一步会失败并给出提示，但不影响手工核对与入库。
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { parseQuestions, type ParsedQuestion } from '@/utils/questionParser'
import { batchCreateQuestions } from '@/api/question'
import { parseQuestionsByAi } from '@/api/ai'
import { fetchKps } from '@/api/kp'
import { fetchDicts } from '@/api/dict'
import { QTYPES } from '@/constants/question'
import { errMsg } from '@/utils/error'
import type { Dict, KnowledgePoint, Question } from '@/types'

/** 切分结果 + 后面补上的知识点 */
interface ImportRow extends ParsedQuestion {
  kpId: number | null
  kpName: string | null
}

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
const aiFilled = ref(false)

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

/** ① 智能切分（纯前端规则，不联网） */
function onSplit(): void {
  const parsed = parseQuestions(rawText.value)
  if (!parsed.length) {
    ElMessage.warning('没切出题目，检查一下是不是还没粘贴内容')
    return
  }
  rows.value = parsed.map((q) => ({ ...q, kpId: null, kpName: null }))
  aiFilled.value = false
  ElMessage.success(`已切出 ${parsed.length} 道题，请核对后入库`)
}

/** 把一行还原成给 AI 的文本 */
function toItemText(r: ImportRow): string {
  const parts = [r.stem]
  if (r.options.length) parts.push(r.options.join('\n'))
  if (r.answer) parts.push(`答案：${r.answer}`)
  if (r.analysis) parts.push(`解析：${r.analysis}`)
  return parts.join('\n')
}

/** ② AI 补答案 / 解析 / 难度 / 知识点 */
async function onAiFill(): Promise<void> {
  if (!rows.value.length) return
  if (rows.value.length > 30) {
    ElMessage.warning('一次最多交给 AI 解析 30 道题，请先删掉多余的')
    return
  }
  aiLoading.value = true
  try {
    const res = await parseQuestionsByAi({
      items: rows.value.map(toItemText),
      grade: batch.grade || undefined,
      source: batch.source || undefined,
      kpOptions: kps.value.map((k) => ({ id: k.id, name: k.name }))
    })
    rows.value = rows.value.map((row, i) => {
      const r = res[i]
      if (!r) return row
      return {
        ...row,
        stem: r.stem?.trim() || row.stem,
        qtype: r.qtype || row.qtype,
        options: r.options?.length ? r.options : row.options,
        answer: r.answer || row.answer,
        analysis: r.analysis || row.analysis,
        difficulty: r.difficulty || row.difficulty,
        kpId: r.kpId ?? row.kpId,
        kpName: r.kpName ?? row.kpName
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

function onRemove(i: number): void {
  rows.value.splice(i, 1)
}

/** ③ 批量入库 */
async function onImport(): Promise<void> {
  const valid = rows.value.filter((r) => r.stem.trim())
  if (!valid.length) {
    ElMessage.warning('没有可入库的题目')
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
    ElMessage.success(`已入库 ${count} 道题`)
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
</script>

<template>
  <el-dialog v-model="visible" title="批量导入题目" width="1060px" top="5vh" class="import-dialog">
    <div class="import-layout">
      <!-- 左：粘贴 + 公共属性 -->
      <div class="pane pane-left">
        <div class="pane-title">① 粘贴卷子原文</div>
        <el-input
          v-model="rawText"
          type="textarea"
          :rows="13"
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

      <!-- 右：切分结果 -->
      <div class="pane pane-right">
        <div class="pane-title">
          <span>③ 核对（{{ rows.length }} 道）</span>
          <span class="pane-actions">
            <el-tag v-if="aiFilled" size="small" type="success" effect="plain">AI 已补全</el-tag>
            <el-button
              size="small"
              :loading="aiLoading"
              :disabled="!rows.length"
              @click="onAiFill"
            >
              AI 补答案与解析
            </el-button>
          </span>
        </div>

        <div class="rows">
          <el-empty
            v-if="!rows.length"
            description="切分结果会显示在这里"
            :image-size="70"
          />
          <div v-for="(r, i) in rows" :key="i" class="row">
            <div class="row-head">
              <span class="row-no">{{ i + 1 }}</span>
              <el-select v-model="r.qtype" size="small" style="width: 92px">
                <el-option v-for="t in QTYPES" :key="t" :label="t" :value="t" />
              </el-select>
              <el-tag v-if="r.kpName" size="small" effect="plain">{{ r.kpName }}</el-tag>
              <span class="row-spacer"></span>
              <el-button link type="danger" size="small" @click="onRemove(i)">移除</el-button>
            </div>

            <el-input v-model="r.stem" type="textarea" :rows="2" size="small" placeholder="题干" />

            <div v-if="r.options.length" class="row-opts">
              <el-input
                v-for="(_, oi) in r.options"
                :key="oi"
                v-model="r.options[oi]"
                size="small"
              />
            </div>

            <div class="row-line">
              <el-input v-model="r.answer" size="small" placeholder="答案">
                <template #prepend>答案</template>
              </el-input>
              <el-select v-model="r.difficulty" size="small" style="width: 88px">
                <el-option :value="1" label="易" />
                <el-option :value="2" label="中" />
                <el-option :value="3" label="难" />
              </el-select>
            </div>

            <el-input
              v-model="r.analysis"
              type="textarea"
              :rows="2"
              size="small"
              placeholder="解析（讲题要点）"
            />
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="importing"
        :disabled="!rows.length"
        @click="onImport"
      >
        批量入库（{{ rows.length }}）
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
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.35fr);
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

/* 右侧结果列表 */
.rows {
  flex: 1;
  min-height: 320px;
  max-height: calc(100vh - 260px);
  overflow-y: auto;
  padding-right: 4px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--surface);
}
.row-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.row-no {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-xs);
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 11px;
  font-weight: 500;
}
.row-spacer {
  flex: 1;
}
.row-opts {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 10px;
}
.row-line {
  display: flex;
  gap: 8px;
}
.row-line :deep(.el-input) {
  flex: 1;
}
</style>
