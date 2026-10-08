// 学生专属颜色分配。
// 原寄居在 api/mockData.ts（Wave 2 临时 mock 兜底层）里，故独立出来。

/** 专属颜色调色板 */
const COLOR_PALETTE = [
  '#409EFF',
  '#67C23A',
  '#E6A23C',
  '#F56C6C',
  '#909399',
  '#9254DE',
  '#13C2C2',
  '#EB2F96'
]

/** 自动分配一个未被占用的专属颜色；全部被占用时随机取一个 */
export function autoColor(used: string[]): string {
  const free = COLOR_PALETTE.find((c) => !used.includes(c))
  return free || COLOR_PALETTE[Math.floor(Math.random() * COLOR_PALETTE.length)]
}
