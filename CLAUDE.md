# CLAUDE.md — 备课排课管理系统

> 本文件是 AI 协作助手（WorkBuddy / Claude）进入本仓库的**总入口**。详细规范见 [`AGENTS.md`](./AGENTS.md)。
> 开发方法论**照搬**自公司项目 `energy-vpp-web`（前端）与 `energy-vpp-service`（后端）的天溯（tiansu）SDD 规范。

## 1. 这是什么
- **系统**：独立中小学（语文）教师的备课排课管理系统。
- **目标**：排课录入、请假顺延、补课闭环、按月数据总览与收入统计。
- **形态**：Web 优先（移动端功能稳定后做），单人使用，暂不做登录。

## 2. 技术栈（与公司项目一致）
| 层 | 技术 | 参照项目 |
|---|---|---|
| 前端 | Vue 3 + Vite + TypeScript | `energy-vpp-web` |
| 后端 | Spring Boot + MySQL 8.0 | `energy-vpp-service` |
| 数据库 | MySQL（`47.116.35.76`） | `energy-vpp-service` |

## 3. 仓库结构
```
prepare-lesson-miniprogram/
├── CLAUDE.md                 # 本文件（总入口）
├── AGENTS.md                 # 详细开发规范（本文件的下位文档）
├── frontend/                 # Vue3 + Vite + TS（照搬 energy-vpp-web 的前端工程结构）
│   ├── .workbuddy/           # 前端规范方法论（ts-* 技能，等价 tiansupowers）
│   ├── src/
│   ├── vite.config.ts
│   └── package.json
├── backend/                  # Spring Boot + MySQL（照搬 energy-vpp-service 的工程结构）
│   ├── ai-knowledge/         # 后端技能（从 energy-vpp-service/ai-knowledge 照搬）
│   │   └── skill/
│   ├── pom.xml
│   └── src/
└── doc-repo/                 # 设计文档 / 概设 / 详设归档
```

## 4. 开发模式（照搬天溯 SDD）
本仓库采用**规范驱动开发（SDD）**，前端、后端共用同一套阶段化流程：
1. **阶段一 概设**（`ts-overview`）：PRD → 后端概设 + 前端概设 + UI 原型。
2. **阶段二 详设**（`ts-design` / 后端 `design-document-skill` + `tiansu-ddd`）：产出详细设计文档（DDD）。
3. **阶段三 实现**（`ts-implement` / 后端 `tiansu-java-standards`）：按已确认设计编码。
4. **评审**：`ts-code-review` + `vue-standard`（前端）、`tiansu-java-standards`（后端）。
5. **提交**：`ts-git`（规范 commit，禁危险操作）。

> 详细阶段门禁、产物校验、人工卡点规则见 `AGENTS.md` 第 3~5 章。

## 5. 前端规范来源：tiansupowers（方法论，非组件）
- 前端工程的**开发规范方法论**来自 tiansupowers（`energy-vpp-web` 的插件），提供 `ts-code-review`、`vue-standard` 等技能；**照搬**为 WorkBuddy 用户级 `ts-*` 技能（已就绪），落地登记见 `AGENTS.md`。
- 本系统**没有天溯 `Te-*` 组件库**，UI 组件统一使用开源的 **Element Plus**（`element-plus` + `el-*` 组件），不引入天溯私有依赖。
- 排课网格 / 拖拽等高度定制的业务交互在 Element Plus 基础上自绘（或直接用原生 + 少量封装），不强行套组件。

## 6. 后端规范来源：ai-knowledge 技能
- 后端工程使用 `energy-vpp-service/ai-knowledge/skill/` 下的技能，**照搬**到 `backend/ai-knowledge/skill/`：
  - `tiansu-ddd`：领域驱动设计（实体/聚合/仓储分层）。
  - `tiansu-java-standards`：Java / Spring Boot 编码规范。
  - `design-document-skill`：设计文档（概设 / 详设 / 接口 / 库表）模板与示例。
  - `springboot-testing`：测试规范。
  - `energy-sdd-workflow`：SDD 多阶段编排（与前端 `ts-*` 对齐）。
  - `java-logging-standard`、`frontend-api-sync`、`documind`、`update-scaffold`：辅助规范。

## 7. 协作原则（必须）
- **只做用户当次要求的最小改动**；发现的额外问题只汇报、不动手，等确认。
- 涉及删除 / 覆盖 / 发布 / 改线上，先列清单、说清后果，再等确认。
- 交付四件套：改了哪些文件、为什么、怎么验证、还剩什么。
- 用绝对路径；中文回复；代码/命令/路径保持 ASCII 原样。

## 8. 已照搬 / 待确认
### 已完成
- [x] `energy-vpp-service/ai-knowledge/skill/*`（9 个方法论技能）已逐字拷至 `backend/ai-knowledge/skill/`（明文）。
- [x] 后端 ORM = **MyBatis-Plus**（依 ai-knowledge 技能确定）。
- [x] 前端规范方法论来源 `tiansupowers` 已登记（`CLAUDE.md` 第 5 章）；UI 组件统一用开源 **Element Plus**（本系统无天溯组件库），WorkBuddy 等价能力为 `ts-*` 用户级技能。

### 待确认（不阻塞开工）
- [x] **前端组件库 = Element Plus**（用户确认：本系统无天溯组件，统一用开源 Element Plus）。
- [ ] 两公司项目 `AGENTS.md / CLAUDE.md` 中通用方法论条款可继续抽取并入（按需）。
