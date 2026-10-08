// 统一错误处理工具。
// 原寄居在 api/mockData.ts（Wave 2 临时 mock 兜底层）里，导致该 mock 文件无法退场，故独立出来。

/** 提取错误信息文案（统一返回体 message 优先，其次 Error.message） */
export function errMsg(e: unknown): string {
  const respMsg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
  const msg = (e as { message?: string })?.message
  return respMsg || msg || '操作失败'
}

/** 判断错误是否为 409 冲突（删除保护 / 时段重叠 / 重复建课） */
export function isConflict(e: unknown): boolean {
  return (e as { response?: { status?: number } })?.response?.status === 409
}
