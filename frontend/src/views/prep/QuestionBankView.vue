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
  stem: [{ required: true, message: '请输入题干', trigger: 'blur' }],
  kpId: [{ required: true, message: '请选择知识点', trigger: 'change' }]
}

const isChoice = computed(() => ['选择', '判断'].includes(form.qtype))

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
  drawerVisible.value = true
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
        form.options = ['', '', '', '']
        form.answer = ''
        form.analysis = ''
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
      size="620px"
      @closed="formRef?.clearValidate?.()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="年级">
          <el-select v-model="form.grade" placeholder="选填" clearable style="width: 200px">
            <el-option v-for="g in grades" :key="g.id" :label="g.dictValue" :value="g.dictValue" />
          </el-select>
          <span class="tip">录题时已自动带入列表筛选的年级</span>
        </el-form-item>
        <el-form-item label="知识点" prop="kpId">
          <el-select v-model="form.kpId" placeholder="选择本题考查的知识点" filterable style="width: 100%">
            <el-option v-for="k in kps" :key="k.id" :label="`${k.name}${k.grade ? '（' + k.grade + '）' : ''}`" :value="k.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="form.qtype" style="width: 200px">
            <el-option v-for="t in QTYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干" prop="stem">
          <el-input v-model="form.stem" type="textarea" :rows="4" placeholder="支持换行" />
        </el-form-item>
        <template v-if="isChoice">
          <el-form-item label="选项">
            <div class="opt-list">
              <el-input
                v-for="(_, i) in form.options"
                :key="i"
                v-model="form.options[i]"
                :placeholder="`${String.fromCharCode(65 + i)} 选项内容`"
              />
              <el-button link type="primary" @click="form.options.push('')">+ 添加选项</el-button>
            </div>
          </el-form-item>
        </template>
        <el-form-item label="答案">
          <el-input v-model="form.answer" placeholder="如 A 或参考答案文本" />
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
