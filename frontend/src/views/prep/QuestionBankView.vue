<script setup lang="ts">
// 备课 · 题库（BE-Q-01~05）：录题 / 改题 / 删题，选择题动态出选项行
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchQuestions,
  createQuestion,
  updateQuestion,
  deleteQuestion,
  type QuestionQuery
} from '@/api/question'
import { fetchKps } from '@/api/kp'
import { fetchDicts } from '@/api/dict'
import QuestionImportDialog from '@/components/QuestionImportDialog.vue'
import { QTYPES, DIFFICULTY_LABEL } from '@/constants/question'
import type { Dict, KnowledgePoint, Question } from '@/types'
import { errMsg } from '@/utils/error'

const loading = ref(false)
const importVisible = ref(false)

/** 批量导入完成：回到第一页并刷新列表 */
function onImported(): void {
  query.page = 1
  loadList()
}
const list = ref<Question[]>([])
const total = ref(0)
const kps = ref<KnowledgePoint[]>([])
const grades = ref<Dict[]>([])

const query = reactive<QuestionQuery>({
  grade: undefined,
  kpId: undefined,
  qtype: undefined,
  keyword: '',
  page: 1,
  size: 20
})

const drawerVisible = ref(false)
const mode = ref<'add' | 'edit'>('add')
const submitting = ref(false)
const formRef = ref()

const form = reactive({
  id: undefined as number | undefined,
  grade: '',
  kpId: undefined as number | undefined,
  qtype: '选择',
  stem: '',
  options: ['', '', '', ''],
  answer: '',
  analysis: '',
  difficulty: 2,
  source: ''
})

const rules = {
  stem: [{ required: true, message: '请输入题干', trigger: 'blur' }]
}

const isChoice = computed(() => ['选择', '判断'].includes(form.qtype))

/** 选择题的选项字母 */
const OPT_LETTERS = ['A', 'B', 'C', 'D', 'E', 'F']

/** 判断题固定两个选项，避免老师手输 */
const JUDGE_OPTIONS = ['正确', '错误']

/** 答案点选：选择题按 A/B/C/D 点，判断题按 正确/错误 点；可多选（多选题连点） */
const answerTokens = computed(() => (form.answer || '').split(/[,，、\s]+/).filter(Boolean))

function toggleAnswer(letter: string): void {
  const cur = answerTokens.value
  const next = cur.includes(letter) ? cur.filter((x) => x !== letter) : [...cur, letter]
  next.sort((a, b) => OPT_LETTERS.indexOf(a) - OPT_LETTERS.indexOf(b))
  form.answer = next.join('')
}

function isAnswerOn(letter: string): boolean {
  return answerTokens.value.includes(letter)
}

/** 知识点按「分类」分组，便于在抽屉里快速定位 */
const kpGroups = computed(() => {
  const map = new Map<string, KnowledgePoint[]>()
  for (const k of kps.value) {
    const key = k.category || '未分类'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(k)
  }
  return [...map.entries()].map(([category, items]) => ({ category, items }))
})

/** 抽屉里的知识点候选项：按年级联动（知识点自身年级为空则视为通用） */
const formKps = computed(() =>
  kps.value.filter((k) => !form.grade || !k.grade || k.grade === form.grade)
)

/** 年级变了但已选知识点不在新范围时，清掉选择 */
function onFormGradeChange(): void {
  if (!form.kpId) return
  const still = formKps.value.some((k) => k.id === form.kpId)
  if (!still) form.kpId = undefined
}

/** 题干查重：输入题干时按关键词查库，命中疑似重复的题就提示 */
const dupLoading = ref(false)
const dups = ref<Question[]>([])
let dupTimer: ReturnType<typeof setTimeout> | null = null

function checkDuplicate(): void {
  if (dupTimer) clearTimeout(dupTimer)
  const kw = form.stem.trim()
  if (kw.length < 6) {
    dups.value = []
    return
  }
  dupTimer = setTimeout(async () => {
    dupLoading.value = true
    try {
      const res = await fetchQuestions({ keyword: kw.slice(0, 30), page: 1, size: 5 })
      // 只留题干高度相似的（同一条题干或包含关系）
      dups.value = res.list.filter((q) => q.id !== form.id && q.stem.includes(kw.slice(0, 12)))
    } catch {
      dups.value = []
    } finally {
      dupLoading.value = false
    }
  }, 400)
}

/** 题干摘要：取首行前 60 字 */
function stemBrief(stem: string): string {
  const first = (stem || '').split('\n')[0]
  return first.length > 60 ? `${first.slice(0, 60)}…` : first
}

/** 选项 JSON 字符串 → 数组 */
function parseOptions(options: string | null): string[] {
  if (!options) return []
  try {
    const arr = JSON.parse(options)
    return Array.isArray(arr) ? (arr as string[]) : []
  } catch {
    return []
  }
}

function optionsPreview(options: string | null): string {
  const arr = parseOptions(options)
  return arr.filter((x) => x && x.trim()).join(' / ')
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const res = await fetchQuestions({ ...query })
    list.value = res.list
    total.value = res.total
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    loading.value = false
  }
}

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

function resetQuery(): void {
  query.grade = undefined
  query.kpId = undefined
  query.qtype = undefined
  query.keyword = ''
  query.page = 1
  loadList()
}

function onSearch(): void {
  query.page = 1
  loadList()
}

/** 打开录题抽屉；自动带入当前筛选的年级 + 知识点，减少录入字段 */
function openAdd(): void {
  mode.value = 'add'
  Object.assign(form, {
    id: undefined,
    grade: query.grade || '',
    kpId: query.kpId,
    qtype: '选择',
    stem: '',
    options: ['', '', '', ''],
    answer: '',
    analysis: '',
    difficulty: 2,
    source: ''
  })
  dups.value = []
  drawerVisible.value = true
}

function openEdit(row: Question): void {
  mode.value = 'edit'
  const opts = parseOptions(row.options)
  Object.assign(form, {
    id: row.id,
    grade: row.grade || '',
    kpId: row.kpId ?? undefined,
    qtype: row.qtype || '选择',
    stem: row.stem,
    options: opts.length ? [...opts, '', '', '', ''].slice(0, Math.max(4, opts.length)) : ['', '', '', ''],
    answer: row.answer || '',
    analysis: row.analysis || '',
    difficulty: row.difficulty ?? 2,
    source: row.source || ''
  })
  dups.value = []
  drawerVisible.value = true
}

/** 切题型时对齐选项：判断题固定两项，其他选择题补足四行 */
function onQtypeChange(): void {
  if (form.qtype === '判断') {
    form.options = [...JUDGE_OPTIONS]
  } else if (form.qtype === '选择') {
    if (form.options.length < 4) form.options = [...form.options, '', '', '', ''].slice(0, 4)
  }
}

/** 用已有题目的题干覆盖当前（去重场景：其实是同一题，直接放弃录入） */
function useDupAsIs(q: Question): void {
  dups.value = []
  ElMessage.info(`已存在同题：#${q.id}`)
}

/** 保存；again=true 时保存后继续录下一题（录题是高频动作） */
async function onSubmit(again: boolean): Promise<void> {
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    submitting.value = true
    try {
      const opts = isChoice.value ? form.options.filter((o) => o && o.trim()) : []
      const payload = {
        grade: form.grade || null,
        kpId: form.kpId,
        qtype: form.qtype,
        stem: form.stem,
        options: opts.length ? JSON.stringify(opts) : null,
        answer: form.answer || null,
        analysis: form.analysis || null,
        difficulty: form.difficulty,
        source: form.source || null
      }
      if (mode.value === 'add') {
        await createQuestion(payload)
      } else {
        await updateQuestion({ ...payload, id: form.id })
      }
      ElMessage.success('已保存')
      loadList()
      if (again) {
        form.stem = ''
        form.options = form.qtype === '判断' ? [...JUDGE_OPTIONS] : ['', '', '', '']
        form.answer = ''
        form.analysis = ''
        dups.value = []
      } else {
        drawerVisible.value = false
      }
    } catch (e) {
      ElMessage.error(errMsg(e))
    } finally {
      submitting.value = false
    }
  })
}

async function onDelete(row: Question): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除该题？\n${stemBrief(row.stem)}`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteQuestion(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error(errMsg(e))
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
        <el-select v-model="query.grade" placeholder="全部年级" clearable style="width: 120px">
          <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
        </el-select>
        <el-select v-model="query.kpId" placeholder="全部知识点" clearable filterable style="width: 190px">
          <el-option v-for="k in kps" :key="k.id" :label="k.name" :value="k.id" />
        </el-select>
        <el-select v-model="query.qtype" placeholder="全部题型" clearable style="width: 120px">
          <el-option v-for="t in QTYPES" :key="t" :label="t" :value="t" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索题干"
          clearable
          style="width: 180px"
          @keyup.enter="onSearch"
        />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <div class="actions">
        <el-button @click="importVisible = true">批量导入</el-button>
        <el-button type="primary" @click="openAdd">录入题目</el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      empty-text="题库还没有题，先录几道就能一键组卷了"
    >
      <el-table-column label="题干" min-width="280">
        <template #default="{ row }">
          <div class="stem">{{ stemBrief(row.stem) }}</div>
          <div v-if="optionsPreview(row.options)" class="opts">{{ optionsPreview(row.options) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="kpName" label="知识点" width="150">
        <template #default="{ row }">{{ row.kpName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="qtype" label="题型" width="90" align="center">
        <template #default="{ row }">{{ row.qtype || '-' }}</template>
      </el-table-column>
      <el-table-column label="难度" width="80" align="center">
        <template #default="{ row }">{{ DIFFICULTY_LABEL[row.difficulty] || '中' }}</template>
      </el-table-column>
      <el-table-column prop="grade" label="年级" width="90" align="center">
        <template #default="{ row }">{{ row.grade || '-' }}</template>
      </el-table-column>
      <el-table-column prop="source" label="来源" width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pager"
      @current-change="loadList"
      @size-change="onSearch"
    />

    <el-drawer
      v-model="drawerVisible"
      :title="mode === 'add' ? '录入题目' : '编辑题目'"
      size="660px"
      @closed="formRef?.clearValidate?.()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <div class="form-row">
          <el-form-item label="年级" class="form-col">
            <el-select
              v-model="form.grade"
              placeholder="选填"
              clearable
              style="width: 100%"
              @change="onFormGradeChange"
            >
              <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
            </el-select>
          </el-form-item>
          <el-form-item label="题型" class="form-col">
            <el-select v-model="form.qtype" style="width: 100%" @change="onQtypeChange">
              <el-option v-for="t in QTYPES" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="知识点">
          <el-select
            v-model="form.kpId"
            placeholder="按知识点归档，便于一键组卷（可留空）"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option-group v-for="g in kpGroups" :key="g.category" :label="g.category">
              <el-option
                v-for="k in g.items"
                :key="k.id"
                :label="`${k.name}${k.grade ? '（' + k.grade + '）' : ''}`"
                :value="k.id"
              />
            </el-option-group>
          </el-select>
          <span class="tip">留空也能存，但一键组卷时不会被抽到</span>
        </el-form-item>
        <el-form-item label="题干" prop="stem">
          <el-input
            v-model="form.stem"
            type="textarea"
            :rows="4"
            placeholder="支持换行；可直接从 Word / PDF 粘贴"
            @input="checkDuplicate"
          />
        </el-form-item>
        <!-- 查重提示 -->
        <el-form-item v-if="dups.length" label=" ">
          <el-alert type="warning" :closable="false" show-icon>
            <template #title>
              题库里已有 {{ dups.length }} 道相似题，确认不是重复录入？
            </template>
            <div class="dup-list">
              <div v-for="d in dups" :key="d.id" class="dup-item">
                <span class="dup-id">#{{ d.id }}</span>
                <span class="dup-stem">{{ stemBrief(d.stem) }}</span>
                <el-button link type="primary" size="small" @click="useDupAsIs(d)">知道了</el-button>
              </div>
            </div>
          </el-alert>
        </el-form-item>
        <template v-if="isChoice">
          <el-form-item label="选项">
            <div class="opt-list">
              <div v-for="(_, i) in form.options" :key="i" class="opt-row">
                <span class="opt-letter">{{ OPT_LETTERS[i] }}</span>
                <el-input
                  v-model="form.options[i]"
                  :placeholder="form.qtype === '判断' ? JUDGE_OPTIONS[i] : `${OPT_LETTERS[i]} 选项内容`"
                />
                <el-button
                  v-if="form.qtype === '选择' && form.options.length > 2"
                  link
                  type="danger"
                  @click="form.options.splice(i, 1)"
                >
                  删除
                </el-button>
              </div>
              <el-button
                v-if="form.qtype === '选择' && form.options.length < 6"
                link
                type="primary"
                @click="form.options.push('')"
              >
                + 添加选项
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="答案">
            <div class="ans-pick">
              <span
                v-for="(_, i) in form.options"
                :key="i"
                class="ans-chip"
                :class="{ on: isAnswerOn(OPT_LETTERS[i]) }"
                @click="toggleAnswer(OPT_LETTERS[i])"
              >
                {{ OPT_LETTERS[i] }}
              </span>
              <el-input
                v-model="form.answer"
                size="small"
                placeholder="也可手写，如 A / AB / 自定义答案"
                style="width: 240px"
              />
            </div>
            <span class="tip">点字母即可设为答案，多选连点</span>
          </el-form-item>
        </template>
        <el-form-item v-else label="答案">
          <el-input v-model="form.answer" placeholder="参考答案文本" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="4" placeholder="讲题要点，上课照着讲" />
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="form.difficulty">
            <el-radio :value="1">易</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">难</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" placeholder="如 2024 期末卷（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button :loading="submitting" @click="onSubmit(true)">保存并再录一题</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit(false)">保存</el-button>
      </template>
    </el-drawer>

    <!-- 批量导入：粘贴整卷 → 规则切分 →（AI 补全）→ 核对入库 -->
    <QuestionImportDialog
      v-model="importVisible"
      :default-grade="query.grade || ''"
      :default-kp-id="query.kpId ?? null"
      @imported="onImported"
    />
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
.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.stem {
  font-weight: 500;
}
.opts {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-faint);
}
.opt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.opt-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.opt-letter {
  width: 18px;
  flex: none;
  font-weight: 600;
  color: var(--text-muted);
}
.form-row {
  display: flex;
  gap: 12px;
}
.form-col {
  flex: 1;
  min-width: 0;
}
.ans-pick {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  width: 100%;
}
.ans-chip {
  width: 30px;
  height: 30px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-base);
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
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
.dup-list {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.dup-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.dup-id {
  color: var(--text-faint);
  flex: none;
}
.dup-stem {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-faint);
}
.actions {
  display: flex;
  gap: 8px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
