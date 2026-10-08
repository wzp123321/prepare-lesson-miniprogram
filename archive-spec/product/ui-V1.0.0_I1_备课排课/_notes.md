# 交接件 _notes.md —— 阶段一 UI 原型（备课排课管理系统）

> 产物版本：ui-V1.0.0_I1
> 生成角色：UI 原型编写者（SDD 阶段一）
> 组件库：**Element Plus（el-\*）**，绝不使用天溯 `te-*` / `@tiansu/*`
> 关联输入：`备课排课管理系统PRD.md`、`AGENTS.md`、`CLAUDE.md`、`.sdd/questions.md`

---

## 1. 原型落盘位置

```
prototype/                                    # 阶段一可构建原型（非 src/，不进正式工程）
├── index.html
├── main.ts                                   # 演示切换器（el-button 组在 S04/S09 间切换）
├── vite.config.ts
├── tsconfig.json
├── env.d.ts
├── package.json
├── S04_排课网格.vue                           # PRD 4.4 核心
└── S09_今日视图.vue                           # PRD 4.9 落地页
```

> 说明：原型刻意落在 `prototype/`，与正式前端工程 `frontend/`（AGENTS.md 2.2）隔离；正式实现阶段由 `ts-implement` 按 `frontend/src/views/**` 结构重写，本目录仅作视觉/交互确认与门禁材料。

---

## 2. 区域 → 组件映射

### 2.1 S04_排课网格.vue（PRD 4.4 排课系统）

| PRD 区域 / 规则 | 实现组件（Element Plus / 自绘） | 偏离 / 备注 |
|---|---|---|
| 左侧学生列表（可搜索、可拖拽） | `el-card` + `el-input`(搜索) + 原生 `div[draggable]` | 学生卡片自绘，`draggable` 用 HTML5 原生属性 |
| 中间当月网格（首列=日期/每行一天，首行=时间段/每列一时段） | 原生 `<table>` 自绘（非 el-table，因需逐格拖拽/着色） | 网格为高度定制交互，按 AGENTS 3.2「在 Element Plus 之上自绘」 |
| 单元格拖入学生生成排课 | HTML5 `dragstart`/`dragover`/`drop` | 自绘拖拽，未引 vuedraggable |
| 每格仅 1 人 · 冲突实时拦截 | `onDrop` 内查 `lessons[key]` 命中即 `ElMessage.warning` | 实时拦截，对齐 PRD 4.4 |
| 状态底色区分（未上/正常/顺延/已补/作废） | 单元格 `style` 背景 + `el-tag` 文字色 | 状态机见 PRD 第6章 |
| 只排当月 + 历史月只读（edge 态） | `el-date-picker`(month) 切换；非当月禁用拖拽并提示 | 演示五态中的 edge 态 |
| 顶部「保存」含「关闭上月顺延」提示 | `el-button` + `ElMessageBox.confirm` + `el-alert` | 提示文案对齐 PRD 4.4 保存逻辑 |
| 顶部「按学生出图」 | `el-button`（占位，点击 `ElMessage.info`） | 正式实现用 html2canvas 导出 PNG（PRD 8） |
| 右侧学生信息 | `el-card` + `el-descriptions` | 点选左侧学生联动 |
| 五态（loading/empty/error/populated/edge） | `el-skeleton` / `el-empty` / `el-result` / 主网格 / 只读 banner | 全部由本地 mock Promise 模拟 |

### 2.2 S09_今日视图.vue（PRD 4.9 今日视图 / 落地页）

| PRD 区域 / 规则 | 实现组件 | 备注 |
|---|---|---|
| 顶部今天日期条 | `el-alert` | — |
| 今日该补·待补课（有则显、无则隐） | `el-card` + `el-table` / `el-empty` | 对齐 PRD 4.6 首页逻辑 |
| 待补课「已安排进本月课程」关闭 | `el-button` + `ElMessageBox.confirm` | 手动关闭（Q12） |
| 今天课程列表（学生/时段/状态） | `el-table` + `el-tag`(状态色) | — |
| 一键标记 正常/顺延（弹窗填原因） | `el-dialog` + `el-form` + `el-radio-group` + `el-select` + `el-input` | 顺延必填 请假方+原因（PRD 4.5） |
| 五态 | `el-skeleton` / `el-result` / `el-empty` / 主表 | 本地 mock Promise 模拟 |

### 2.3 运行壳 main.ts

- `createApp` → `app.use(ElementPlus)` → `mount('#app')`
- 引入 `element-plus` 与 `element-plus/dist/index.css`（对齐 AGENTS 3.2 引入约定）
- 用 `el-button-group` 在 S04 / S09 间切换，无 router（原型最小集）

---

## 3. 偏离说明（用 Element Plus el-\* 替代天溯 te-\*）

| 项 | 说明 |
|---|---|
| 组件库偏离 | 本系统**无天溯 `Te-*` 组件库**（Q14 / AGENTS 3.2 / CLAUDE 第5章）。原公司模板 energy-vpp-web 用 `te-*`，本原型**全部替换为 Element Plus `el-*`**：`el-table`/`el-dialog`/`el-form`/`el-select`/`el-date-picker`/`el-button`/`el-tag`/`el-card`/`el-descriptions`/`el-message`/`el-empty`/`el-result`/`el-alert`/`el-skeleton`/`el-radio-group`/`el-input`。 |
| 排课网格 | PRD/AGENTS 明确「排课网格、拖拽在 Element Plus 之上自绘」。故网格用原生 `<table>` + HTML5 `draggable` 实现，**未强行套 `el-table`**（逐格拖拽/着色场景 `el-table` 反而不便）。这是刻意偏离，符合硬约束。 |
| 拖拽方案 | 采用浏览器原生 HTML5 拖拽 API，未引入 `vuedraggable`（保持依赖最小）。如需更顺滑的跨列拖拽，正式实现可评估引入。 |
| 图标 | 未使用 `@element-plus/icons-vue`，避免额外依赖，演示内以文字/色块表达。 |
| 路由/状态 | 原型用 `main.ts` 内 `el-button-group` 切换，未引 vue-router / pinia；正式实现按 `frontend/` 结构补 `router`/`store`（AGENTS 3.3）。 |
| 样式变量 | 仅用内联 `style` 与少量 `scoped` CSS，未引入组件库主题深度定制（原型期足够）。 |

---

## 4. 未决问题 / 待确认（提交人工卡点）

| 编号 | 问题 | 来源 | 影响原型处 |
|---|---|---|---|
| Q17 | MySQL 连接信息（库名/账号/密码）未定 | questions.md 待确认 | 全部数据走 mock，不影响 |
| U1 | 「按学生出图」具体出图范围/版式（单学生当月 vs 整月） | PRD 4.4 / 8 | 当前为占位按钮，待定版式 |
| U2 | 待补课「已安排进本月课程」精确动作：仅关闭待补项，还是同时在网格生成补课单元？ | PRD 10-2 / Q12 | 原型仅实现「关闭待补项」 |
| U3 | 跨多月遗留待补是否仅靠「首页手动关闭」足够 | PRD 10-3 | 原型未模拟更早月份遗留 |
| U4 | 网格是否需支持「拖动已排课单元格到其他格」调整 | PRD 4.4 未明确 | 当前仅支持删除后重拖 |
| U5 | 时间段重叠校验、学生删除保护等后台规则 | PRD 4.2/4.1 | 属管理后台，未在 S04/S09 体现，待详设 |

---

## 5. 验证方式与结果（已实跑）

```bash
cd prototype
npm install          # vue@3 + element-plus + vite + @vitejs/plugin-vue + typescript + vue-tsc
npm run build        # npx vite build，验证可构建渲染
# 或本地预览：npm run dev
```

### 5.1 实跑结论（2026-09-30）
- **npm install：成功**（本沙箱内需关闭沙箱执行；首次在沙箱内跑 esbuild 的 postinstall 触发 `spawnSync EBUSY`，属沙箱锁定 `node.exe`，非代码问题）。
- **vite build：通过**。`npx vite build` 成功编译，1593 个模块转译完成，产出 `dist/index.html` + `dist/assets/index-*.js|css`，两个 `.vue` 均正常编译渲染。
  - 仅有一条 chunk > 500kB 的告警（Element Plus 全量引入所致，原型可接受，非错误）。
- **未跑**：`vue-tsc` 类型检查（`npm run typecheck`）。`vite build` 仅做打包校验、不校验 TS 类型；本原型 .vue 模板与脚本已通过编译器解析，但正式实现前建议补 `vue-tsc` 全量类型检查。

### 5.2 本沙箱遇到的 npm 原生二进制缺包与处置（供参考，用户本地一般无此问题）
安装后构建报缺 `@esbuild/win32-x64` / `@rollup/rollup-win32-x64-msvc`（npm 在「node_modules 已存在的不干净状态」下安装时偶发的 optional 平台二进制未落地 bug）。处置：
1. 关闭沙箱重装可让 esbuild 的 postinstall 通过；
2. 若仍缺平台二进制，单独补装即可（不必 clean install，因为本环境安全删除钩子会拦截 >50 文件的批量删除，无法 `rm -rf node_modules` / `npm ci`）：
   ```bash
   npm install @esbuild/win32-x64@0.21.5 @rollup/rollup-win32-x64-msvc@4.63.5 --no-save
   npx vite build
   ```
3. 用户本地在干净目录下 `npm install && npm run build` 通常一次成功，无需上述步骤。

> 若后续在别的环境 `npm install` 仍失败：文件已落盘，用户本地执行上方命令即可。构建仅校验可编译/可打包，类型检查用 `npm run typecheck`。
