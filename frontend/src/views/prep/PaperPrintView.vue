<script setup lang="ts">
// 打印视图：A4 学生卷（/print/paper/:id，脱离 AdminLayout 独立成页）
// 目标：老师直接 Ctrl+P / 点「打印」就能拿到干净的纸质卷面，页眉页脚由浏览器补。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchPaperDetail } from '@/api/paper'
import { errMsg } from '@/utils/error'
import { qtypeWeight } from '@/utils/qtype'
import type { PaperDetail, Question } from '@/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const paper = ref<PaperDetail | null>(null)
const error = ref('')

interface BigQuestion {
  qtype: string
  items: { q: Question; no: number }[]
  /** 该大题下每题的分值留白行数，按题型给不同高度 */
  blankLines: number
}

const numbered = computed(() => {
  const list = paper.value?.questions || []
  return list.map((q, i) => ({ q, no: i + 1 }))
})

const groups = computed<BigQuestion[]>(() => {
  const map = new Map<string, { q: Question; no: number }[]>()
  for (const it of numbered.value) {
    const key = it.q.qtype || '其他'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(it)
  }
  // 大题顺序与制卷台、右侧构成统计共用同一张题型表
  const keys = [...map.keys()].sort((a, b) => qtypeWeight(a) - qtypeWeight(b))
  return keys.map((k) => ({
    qtype: k,
    items: map.get(k)!,
    blankLines: blankLinesFor(k)
  }))
})

/** 不同类型题给不同作答留白：选择/判断只需一行，写作给整页 */
function blankLinesFor(qtype: string): number {
  if (qtype === '写作') return 0
  if (qtype === '阅读' || qtype === '古诗文') return 3
  if (qtype === '填空') return 1
  return 1
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

function letter(i: number): string {
  return String.fromCharCode(65 + i)
}

function doPrint(): void {
  window.print()
}

onMounted(async () => {
  const id = Number(route.params.id)
  if (!id || Number.isNaN(id)) {
    error.value = '缺少试卷编号'
    return
  }
  loading.value = true
  try {
    paper.value = await fetchPaperDetail(id)
  } catch (e) {
    error.value = errMsg(e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="print-page">
    <!-- 屏幕上可见的操作条，打印时隐藏 -->
    <div class="print-toolbar no-print">
      <el-button @click="router.back()">返回</el-button>
      <span class="print-tip">打印时请选择 A4、纵向，并关闭「页眉页脚」以外的浏览器附加内容</span>
      <el-button type="primary" @click="doPrint">打印</el-button>
    </div>

    <div v-if="loading" class="print-loading no-print">加载中…</div>
    <div v-else-if="error" class="print-loading no-print">{{ error }}</div>

    <!-- 真正的卷面 -->
    <article v-else-if="paper" class="sheet">
      <header class="sheet-head">
        <h1 class="sheet-title">{{ paper.title }}</h1>
        <div class="sheet-meta">
          <span class="meta-item">姓名：<span class="fill-line"></span></span>
          <span class="meta-item">班级：<span class="fill-line"></span></span>
          <span class="meta-item">日期：<span class="fill-line"></span></span>
          <span class="meta-item score-box">得分：<span class="score-box-inner"></span></span>
        </div>
      </header>

      <p v-if="!paper.questions.length" class="sheet-empty">（这份卷还没有题目）</p>

      <section v-for="(g, gi) in groups" :key="g.qtype" class="big-q">
        <h2 class="big-q-title">
          {{ ['一', '二', '三', '四', '五', '六', '七', '八'][gi] || gi + 1 }}、{{ g.qtype }}题
          <span class="big-q-sub">（共 {{ g.items.length }} 小题）</span>
        </h2>
        <div v-for="it in g.items" :key="it.q.id" class="q">
          <div class="q-stem">
            <span class="q-no">{{ it.no }}.</span>
            <span class="q-stem-text">{{ it.q.stem }}</span>
          </div>
          <ol v-if="parseOptions(it.q.options).length" class="q-opts">
            <li v-for="(o, oi) in parseOptions(it.q.options)" :key="oi">
              <span class="opt-letter">{{ letter(oi) }}.</span> {{ o }}
            </li>
          </ol>
          <!-- 学生作答留白 -->
          <div v-if="g.qtype !== '写作'" class="answer-space" :class="`lines-${g.blankLines}`">
            <div v-for="n in g.blankLines" :key="n" class="answer-line"></div>
          </div>
          <div v-else class="writing-space"></div>
        </div>
      </section>
    </article>
  </div>
</template>

<style scoped>
/* ===== 屏幕预览：把卷面渲染成一张竖向 A4 纸 ===== */
.print-page {
  min-height: 100vh;
  background: #f0f2f5;
  padding: 24px 0 48px;
}
.no-print {
  /* 打印时统一隐藏交互元素 */
}
.print-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 210mm;
  margin: 0 auto 16px;
  padding: 10px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.print-tip {
  margin-right: auto;
  font-size: 12px;
  color: #909399;
}
.print-loading {
  max-width: 210mm;
  margin: 24px auto;
  text-align: center;
  color: #909399;
}
.sheet {
  width: 210mm;
  min-height: 297mm;
  margin: 0 auto;
  padding: 18mm 16mm;
  background: #fff;
  color: #1a1a1a;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  font-family: 'Songti SC', 'SimSun', 'STSong', serif;
  font-size: 14px;
  line-height: 1.9;
  box-sizing: border-box;
}
.sheet-head {
  border-bottom: 1.5px solid #1a1a1a;
  padding-bottom: 10px;
  margin-bottom: 16px;
}
.sheet-title {
  margin: 0 0 12px;
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}
.sheet-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 26px;
  font-size: 13px;
}
.meta-item {
  display: inline-flex;
  align-items: center;
}
.fill-line {
  display: inline-block;
  min-width: 96px;
  border-bottom: 1px solid #666;
  height: 1.1em;
  margin-left: 2px;
}
.score-box {
  margin-left: auto;
}
.score-box-inner {
  display: inline-block;
  width: 52px;
  height: 22px;
  border: 1px solid #1a1a1a;
  border-radius: 3px;
  vertical-align: middle;
}
.sheet-empty {
  text-align: center;
  color: #909399;
  margin-top: 60px;
}
.big-q {
  margin-top: 18px;
}
.big-q-title {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 8px;
}
.big-q-sub {
  font-weight: 400;
  font-size: 12px;
  color: #666;
}
.q {
  margin-bottom: 12px;
}
.q-stem {
  display: flex;
  gap: 4px;
}
.q-no {
  flex: none;
  font-weight: 600;
}
.q-stem-text {
  white-space: pre-wrap;
  word-break: break-word;
}
.q-opts {
  list-style: none;
  padding: 0;
  margin: 4px 0 0 24px;
}
.q-opts li {
  margin-bottom: 2px;
}
.opt-letter {
  font-weight: 600;
}
.answer-space {
  margin: 6px 0 0 24px;
}
.answer-line {
  border-bottom: 1px solid #ccc;
  height: 1.6em;
}
.writing-space {
  margin: 6px 0 0 24px;
  height: 200px;
  border: 1px dashed #ddd;
  border-radius: 4px;
}

/* ===== 打印：去掉底色与留白，按 A4 排版 ===== */
@media print {
  .no-print {
    display: none !important;
  }
  .print-page {
    min-height: auto;
    background: #fff;
    padding: 0;
  }
  .sheet {
    width: auto;
    min-height: auto;
    margin: 0;
    padding: 0;
    box-shadow: none;
  }
  /* 大题尽量不在分页处被切断 */
  .big-q {
    break-inside: avoid;
  }
  .q {
    break-inside: avoid;
  }
  @page {
    size: A4 portrait;
    margin: 16mm 14mm;
  }
}
</style>
