<script setup lang="ts">
// 全局智能排课助手：右下角悬浮球 + 右侧抽屉（挂在 AdminLayout，全站可用）
// 两阶段交互：chat 只出方案 → 勾选 → apply 才落库。
import { nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { chatSchedule, applySchedule } from '@/api/ai'
import { errMsg } from '@/utils/error'
import { aiDrawerVisible, aiPresetMonth, notifyAiApplied } from '@/utils/aiDrawer'
import type { AiAction, AiAsk, AiAskOption, AiSelection } from '@/types'

interface AiMsgAction extends AiAction {
  checked: boolean
}
interface AiMsg {
  role: 'user' | 'ai'
  text: string
  actions?: AiMsgAction[]
  /** 缺参数时 AI 的反问（候选项可点选） */
  ask?: AiAsk | null
  /** 该轮反问是否已被点选过（点过就置灰，防重复） */
  answered?: boolean
}

const AI_SAMPLES = [
  '给张三排课',
  '看看本月还有谁没排课',
  '把张三排到本周六上午',
  '把李四周三下午的课改到周五同一时段'
]

const visible = aiDrawerVisible
const month = ref(currentMonthStr())
const input = ref('')
const sending = ref(false)
const applying = ref(false)
const messages = ref<AiMsg[]>([])
const msgsRef = ref<HTMLElement | null>(null)

function currentMonthStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function parseMonth(v: string): { year: number; month: number } {
  const [y, m] = (v || '').split('-').map(Number)
  const d = new Date()
  return { year: y || d.getFullYear(), month: m || d.getMonth() + 1 }
}

// 打开时：若带入了页面月份就对一次（一次性），否则沿用上次选的月份
watch(visible, (v) => {
  if (!v) return
  if (aiPresetMonth.value) {
    month.value = aiPresetMonth.value
    aiPresetMonth.value = ''
  }
  nextTick(scrollToBottom)
})

function scrollToBottom(): void {
  const el = msgsRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/**
 * 发一轮对话。
 * @param presetText 点选时由候选项带出的文本（不传则取输入框）
 * @param selection  点选时结构化回传的选中项，让模型不必从文本里猜 id
 */
async function onSend(presetText?: string, selection?: AiSelection): Promise<void> {
  const text = (presetText ?? input.value).trim()
  if (!text || sending.value) return
  // 上下文取「本次输入之前」的最近 8 条，支持「改成周日上午」这类追问
  const history = messages.value.slice(-8).map((x) => ({ role: x.role, text: x.text }))
  messages.value.push({ role: 'user', text })
  if (presetText === undefined) input.value = ''
  sending.value = true
  await nextTick()
  scrollToBottom()
  try {
    const { year, month: m } = parseMonth(month.value)
    const res = await chatSchedule({ message: text, year, month: m, history, selection })
    messages.value.push({
      role: 'ai',
      text: res.reply || '（AI 没有给出说明）',
      actions: (res.actions || []).map((a) => ({ ...a, checked: !a.blocked })),
      ask: res.ask || null
    })
  } catch (e) {
    messages.value.push({ role: 'ai', text: `调用失败：${errMsg(e)}` })
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom()
  }
}

/** 点选 AI 反问里的候选项：把选择结构化回传，并把这轮选项置灰 */
function onPick(m: AiMsg, opt: AiAskOption): void {
  if (m.answered || sending.value || !m.ask) return
  m.answered = true
  onSend(opt.label, { field: m.ask.field, value: opt.value, label: opt.label })
}

/** 后端没给 note 时按动作类型生成的兜底描述 */
function describeAction(a: AiAction): string {
  const type = (a.type || '').toUpperCase()
  const who = a.studentName || ''
  switch (type) {
    case 'CREATE':
      return `新增排课：${who} ${a.lessonDate || ''} ${a.slotLabel || ''}`
    case 'DELETE':
      return `删除课次${a.lessonId ? ` #${a.lessonId}` : ''}${who ? `（${who}）` : ''}`
    case 'UPDATE':
      return `调整课次${a.lessonId ? ` #${a.lessonId}` : ''}：${who} → ${a.lessonDate || ''} ${a.slotLabel || ''}`
    case 'ABSENT':
      return `标记顺延：${who || `课次 #${a.lessonId}`}（${
        a.absentBy === 'teacher' ? '老师请假' : '学生请假'
      }：${a.absentReason || ''}）`
    case 'MAKEUP_ARRANGE':
      return `安排补课：课次 #${a.lessonId} → ${a.makeUpDate || ''}`
    case 'MAKEUP_DONE':
      return `标记已补：课次 #${a.lessonId}`
    case 'BATCH_CREATE':
      return `批量排课：${who} 每周${(a.weekdays || []).map(weekdayLabel).join('、')} ${
        a.slotLabel || ''
      }，${a.startDate || ''} ~ ${a.endDate || ''}`
    case 'COPY_WEEK':
      return `复制课表：${a.sourceFrom || ''} 那周 → ${a.targetFrom || ''} 那周`
    default:
      return a.note || '未识别的动作'
  }
}

/** 1..7 → 周一..周日 */
function weekdayLabel(n: number): string {
  return ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][n - 1] || `周${n}`
}

function checkedCount(m: AiMsg): number {
  return (m.actions || []).filter((a) => a.checked && !a.blocked).length
}

async function onApply(m: AiMsg): Promise<void> {
  const actions = (m.actions || []).filter((a) => a.checked && !a.blocked)
  if (!actions.length || applying.value) return
  applying.value = true
  try {
    const res = await applySchedule(actions)
    const parts: string[] = []
    if (res.created) parts.push(`新增 ${res.created} 节`)
    if (res.updated) parts.push(`调整 ${res.updated} 节`)
    if (res.deleted) parts.push(`删除 ${res.deleted} 节`)
    if (res.failed) parts.push(`失败 ${res.failed} 节`)
    m.text += `\n已执行：${parts.length ? parts.join('，') : '无变化'}`
    if (res.messages && res.messages.length) m.text += `\n${res.messages.join('\n')}`
    m.actions = []
    // 通知当前页面刷新（排课页监听后重载网格）
    notifyAiApplied()
  } catch (e) {
    ElMessage.error(errMsg(e))
  } finally {
    applying.value = false
    await nextTick()
    scrollToBottom()
  }
}
</script>

<template>
  <!-- 全局悬浮球 -->
  <div class="ai-fab" title="智能排课助手" @click="visible = true">
    <el-icon :size="22"><ChatDotRound /></el-icon>
  </div>

  <el-drawer
    v-model="visible"
    title="智能排课助手"
    size="440px"
    append-to-body
    class="ai-drawer"
  >
    <div class="ai-body">
      <div class="ai-top">
        <span class="ai-top-label">目标月份</span>
        <el-date-picker
          v-model="month"
          type="month"
          value-format="YYYY-MM"
          format="YYYY年MM月"
          :clearable="false"
          style="width: 152px"
        />
        <span class="ai-top-tip">AI 只给方案，确认后才落库</span>
      </div>

      <div ref="msgsRef" class="ai-msgs">
        <div v-if="!messages.length" class="ai-empty">
          <p>用一句话下指令，例如：</p>
          <ul>
            <li v-for="s in AI_SAMPLES" :key="s" @click="input = s">{{ s }}</li>
          </ul>
          <p class="ai-empty-tip">
            支持查课表、查统计、建课/调课/删课、请假顺延、安排补课、批量排课、复制某周课表。
            说不全也没关系——AI 会把缺的选项列出来让你点。
          </p>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="ai-msg" :class="m.role">
          <div class="ai-bubble">{{ m.text }}</div>

          <!-- 反问：候选项可点选，选完自动接着往下走 -->
          <div v-if="m.ask && m.ask.options && m.ask.options.length" class="ai-ask">
            <div class="ai-ask-label">{{ m.ask.label }}</div>
            <div class="ai-ask-opts">
              <button
                v-for="opt in m.ask.options"
                :key="opt.value"
                type="button"
                class="ai-chip"
                :class="{ picked: m.answered }"
                :disabled="m.answered || sending"
                @click="onPick(m, opt)"
              >
                <span>{{ opt.label }}</span>
                <span v-if="opt.hint" class="ai-chip-hint">{{ opt.hint }}</span>
              </button>
            </div>
          </div>

          <div v-if="m.actions && m.actions.length" class="ai-actions">
            <label
              v-for="(a, ai) in m.actions"
              :key="ai"
              class="ai-action"
              :class="{ blocked: !!a.blocked }"
            >
              <el-checkbox v-model="a.checked" :disabled="!!a.blocked" />
              <div class="ai-action-body">
                <div class="ai-action-note">{{ a.note || describeAction(a) }}</div>
                <div v-if="a.blocked" class="ai-action-blocked">{{ a.blocked }}</div>
              </div>
            </label>
            <el-button
              type="primary"
              size="small"
              :loading="applying"
              :disabled="checkedCount(m) === 0"
              @click="onApply(m)"
            >
              确认执行（{{ checkedCount(m) }}）
            </el-button>
          </div>
        </div>
      </div>

      <div class="ai-input">
        <el-input
          v-model="input"
          placeholder="例如：给张三排课（也可以直接点上面的选项）"
          :disabled="sending"
          @keyup.enter="onSend()"
        />
        <el-button type="primary" :loading="sending" @click="onSend()">发送</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
/* ===================== 悬浮球 ===================== */
.ai-fab {
  position: fixed;
  right: 28px;
  bottom: 28px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--brand-500);
  color: var(--text-on-brand);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 1500;
  user-select: none;
  transition: transform 0.18s ease, opacity 0.18s ease;
}
.ai-fab:hover {
  transform: translateY(-2px);
  opacity: 0.9;
}

/* ===================== 抽屉 ===================== */
.ai-drawer :deep(.el-drawer__body) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.ai-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.ai-top {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--border-subtle);
}
.ai-top-label {
  font-size: 13px;
  color: var(--text-muted);
}
.ai-top-tip {
  font-size: 11px;
  color: var(--text-faint);
  margin-left: auto;
}
.ai-msgs {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 2px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ai-empty {
  font-size: 12px;
  color: var(--text-muted);
}
.ai-empty p {
  margin: 0 0 6px;
}
.ai-empty ul {
  padding-left: 18px;
  margin: 0 0 10px;
}
.ai-empty li {
  color: var(--brand-500);
  cursor: pointer;
  margin-bottom: 4px;
}
.ai-empty li:hover {
  text-decoration: underline;
}
.ai-empty-tip {
  color: var(--text-faint);
  line-height: 1.7;
}
.ai-msg {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ai-msg.user {
  align-items: flex-end;
}
.ai-bubble {
  max-width: 88%;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--surface-sunken);
  color: var(--text-strong);
}
.ai-msg.user .ai-bubble {
  background: var(--brand-500);
  color: var(--text-on-brand);
}
/* 反问的候选项 chips */
.ai-ask {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ai-ask-label {
  font-size: 12px;
  color: var(--text-muted);
}
.ai-ask-opts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ai-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--brand-200);
  border-radius: var(--radius-pill);
  background: var(--brand-50);
  color: var(--brand-600);
  font-family: inherit;
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition: background 0.15s ease, opacity 0.15s ease;
}
.ai-chip:hover:not(:disabled) {
  background: var(--brand-100);
}
.ai-chip.picked,
.ai-chip:disabled {
  opacity: 0.45;
  cursor: default;
}
.ai-chip-hint {
  font-size: 11px;
  color: var(--brand-400);
}
.ai-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ai-action {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.ai-action.blocked {
  background: var(--surface-sunken);
  opacity: 0.65;
  cursor: not-allowed;
}
.ai-action-body {
  flex: 1;
  min-width: 0;
}
.ai-action-note {
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-strong);
}
.ai-action-blocked {
  margin-top: 2px;
  font-size: 11px;
  color: var(--warning);
}
.ai-input {
  display: flex;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--border-subtle);
}
</style>
