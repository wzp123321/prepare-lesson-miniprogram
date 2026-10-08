// 全局「智能排课助手」抽屉 —— 模块级单例状态
//
// 组件 AiScheduleFab.vue 挂在 AdminLayout 里（全站可用）；
// 页面（如排课页工具条按钮）通过 openAiDrawer(月份) 唤起它；
// AI 动作落库后调 notifyAiApplied()，相关页面据此刷新数据。
import { ref } from 'vue'

/** 抽屉是否打开 */
export const aiDrawerVisible = ref(false)

/** 唤起时预置的目标月份（YYYY-MM）；用完即清，为空则沿用抽屉里上次选的月份 */
export const aiPresetMonth = ref('')

/** 每次 AI 动作落库 +1；排课页 watch 它来刷新网格 */
export const aiAppliedTick = ref(0)

/**
 * 打开抽屉。
 * @param month 当前页面的目标月份（YYYY-MM），传了就让抽屉对齐这个月
 */
export function openAiDrawer(month?: string): void {
  if (month) aiPresetMonth.value = month
  aiDrawerVisible.value = true
}

/** AI 动作已写入数据库，通知页面刷新 */
export function notifyAiApplied(): void {
  aiAppliedTick.value += 1
}
