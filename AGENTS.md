# AGENTS.md — 备课排课管理系统 开发规范

> 本文档是 AI 协作助手进入本仓库的**详细规范**。总入口见 [`CLAUDE.md`](./CLAUDE.md)。
> 全部开发模式**照搬**自公司项目 `energy-vpp-web`（前端）与 `energy-vpp-service`（后端）的天溯（tiansu）SDD 规范；本文件在第 8 章登记「已照搬 / 待确认」清单。

---

## 1. 项目上下文

| 项 | 内容 |
|---|---|
| 系统 | 备课排课管理系统（独立中小学语文教师用） |
| 形态 | Web 优先，单人使用，暂不做登录与多角色 |
| 业务核心 | 排课录入、请假顺延、补课闭环、按月总览与收入统计 |
| 关键约束 | 只上语文；排课只排当月；每格 1 人；保存排课时关闭上月顺延 |

完整需求见 `备课排课管理系统PRD.md`（仓库根）。

---

## 2. 技术栈与工程结构

### 2.1 技术栈
- **前端**：Vue 3 + Vite + TypeScript（参照 `energy-vpp-web`）。
- **后端**：Spring Boot（Maven）+ MySQL 8.0（参照 `energy-vpp-service`），库部署于 `47.116.35.76`。
- **协作/规范**：天溯 SDD 方法论（前端 `ts-*` 技能，后端 `ai-knowledge` 技能）。

### 2.2 工程结构（照搬两项目）
```
prepare-lesson-miniprogram/
├── CLAUDE.md
├── AGENTS.md                    # 本文件
├── 备课排课管理系统PRD.md
├── frontend/                    # = energy-vpp-web 前端工程结构
│   ├── .workbuddy/              # 前端规范方法论（ts-* 技能，等价 tiansupowers）
│   │   └── skills/              # ts-code-review / vue-standard 等
│   ├── public/
│   ├── src/
│   │   ├── api/                 # 接口请求层
│   │   ├── components/          # 业务组件（基于 Element Plus 封装）
│   │   ├── views/               # 页面（管理后台 / 前台看板）
│   │   ├── router/
│   │   ├── store/               # 状态管理
│   │   ├── utils/
│   │   ├── styles/
│   │   ├── App.vue
│   │   └── main.ts
│   ├── index.html
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── package.json
├── backend/                     # = energy-vpp-service 后端工程结构
│   ├── ai-knowledge/            # 后端技能（照搬 energy-vpp-service/ai-knowledge）
│   │   └── skill/
│   │       ├── tiansu-ddd/
│   │       ├── tiansu-java-standards/
│   │       ├── design-document-skill/
│   │       ├── springboot-testing/
│   │       ├── energy-sdd-workflow/
│   │       ├── java-logging-standard/
│   │       ├── frontend-api-sync/
│   │       ├── documind/
│   │       └── update-scaffold/
│   ├── energy-lesson-api/       # Spring Boot 模块（可按需拆分）
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/com/lesson/  # controller / service / domain / repository
│           └── resources/        # application.yml / mapper
└── doc-repo/                    # 概设 / 详设 / 接口 / 库表文档归档
```

---

## 3. 前端开发规范（照搬 energy-vpp-web + tiansupowers）

### 3.1 规范来源
- **tiansupowers 方法论**（照搬自 `energy-vpp-web`）：提供 `ts-code-review`、`vue-standard` 等技能，作为本系统的前端**规范与评审准则**。
- 本机 WorkBuddy 已具备等价 `ts-*` 用户级技能，落地位置见 `CLAUDE.md` 第 5 章。

### 3.2 代码规范
- **组件库 = Element Plus**（用户确认：本系统无天溯组件库，UI 统一用开源 `element-plus`）。
  - 引入：`import ElementPlus from 'element-plus'` + `import 'element-plus/dist/index.css'`。
  - 常用：`el-table` / `el-form` / `el-dialog` / `el-select` / `el-date-picker` / `el-message` / `el-tag` 等。
  - 排课网格、拖拽等高度定制交互在 Element Plus 之上自绘，不强行套组件。
- **TS 规范**：开启 `strict`；接口/类型命名见名知义；禁止含糊字段名。
- **Vue 规范**（vue-standard）：SFC 结构、组合式 API、`ref`/`reactive` 约定、Props 类型化、事件命名。
- **CSS**：跟随组件库主题变量；类名见名知义，不引入 BEM `__`（除非项目已有）。

### 3.3 目录与分层
- `api/`：所有后端请求集中管理，按模块拆分（student / slot / course / lesson / dict / stats）。
- `views/`：页面按「管理后台 / 前台看板」分目录。
- `store/`：跨页状态（如当前月份、待补课数）。
- `utils/`：纯函数工具（日期、金额、导出 PNG）。

### 3.4 前端 SDD 阶段（技能对应）
| 阶段 | 技能 | 产物 |
|---|---|---|
| 概设 | `ts-overview` | 后端概设 + 前端概设 + UI 原型（Vue SFC） |
| 详设 | `ts-design` | 详细设计文档（DDD） |
| 实现 | `ts-implement` | 编码实现 + 自检 |
| 评审 | `ts-code-review` + `vue-standard` | 分级审查报告 |
| 提交 | `ts-git` | 规范 commit |

---

## 4. 后端开发规范（只借 ai-knowledge 方法论，不参考 energy-vpp-service 具体实现）

> ⚠️ **范围边界**：本系统后端是**独立 Spring Boot 服务**，只借用 `energy-vpp-service/ai-knowledge` 的**方法论技能**（DDD 分层、Java 规范、设计文档模板、测试规范）作为编码准则；**不引入**天溯 `energy-parent` 私有依赖体系（私有 Nexus / Nacos / RocketMQ / 动态数据源等），那些连不上内网、也不适合单用户小系统。具体代码实现由本系统自行编写。

### 4.1 规范来源：ai-knowledge 技能（已拷至 `backend/ai-knowledge/skill/`）
| 技能 | 作用 |
|---|---|
| `tiansu-ddd` | 领域驱动设计：实体 / 值对象 / 聚合根 / 仓储分层 |
| `tiansu-java-standards` | Java / Spring Boot 编码规范（命名、分层、注解、异常处理） |
| `design-document-skill` | 设计文档模板（概设 / 详设 / 接口设计 / 数据库设计）及示例 |
| `springboot-testing` | 单元测试 / 集成测试规范 |
| `energy-sdd-workflow` | SDD 多阶段编排，与前端 `ts-*` 对齐 |
| `java-logging-standard` | 日志规范 |
| `frontend-api-sync` | 前后端接口契约同步 |
| `documind` / `update-scaffold` | 文档辅助 / 脚手架同步 |

### 4.2 分层架构（DDD）
```
controller  (REST 入口，参数校验、DTO 转换)
  └─ application / service (业务逻辑、事务)
       └─ domain (实体、聚合、领域规则，如「保存排课关闭上月顺延」)
            └─ repository / mapper (持久化，MySQL)
```
- **实体**：Student、TimeSlot、Course、Lesson、Dict。
- **核心领域规则**（写在 domain/service，不散落在 controller）：
  - 排课保存时查询上月 `ABSENT` 课并批量关闭；
  - 同格（同日期+同时段）仅允许 1 名学生（冲突拦截）；
  - 时间段重叠校验；
  - 月收入 = (NORMAL + MADEUP) 节数 × 学生价格。
- **状态机**：`UNTAKEN → NORMAL / ABSENT → MADEUP / CANCELLED`（见 PRD 第 6 章）。

### 4.3 Java 编码规范要点
- 包名 `com.lesson.*`；类名大驼峰、方法/变量小驼峰；常量全大写下划线。
- 统一返回体（code/message/data）；业务异常自定义、全局异常处理。
- 数据库访问用 **MyBatis-Plus**（与天溯 `tiansu-java-standards` / `springboot-testing` 技能一致；`energy-vpp-service` 即此选型）。
- 配置 `application.yml` 中数据库连接（`47.116.35.76`）走环境变量占位，不硬编码密码。

### 4.4 测试规范（springboot-testing）
- 关键领域规则（排课冲突、关闭上月顺延、月收入计算）必须有单元测试。
- Controller 层用 MockMvc / 集成测试覆盖主流程。

---

## 5. SDD 协作流程与门禁

> 照搬天溯「阶段门禁 + 人工卡点」原则：每个阶段产物需**用户确认**后方可进入下一阶段；实现阶段前禁止写业务代码。

1. **阶段一（概设）**：消化 PRD → 产出后端概设 + 前端概设 + UI 原型。**用户确认**后才进详设。
2. **阶段二（详设）**：按概设产物写详细设计文档（后端用 `design-document-skill` 模板，前端用 `ts-design`）。设计阶段**禁止写业务代码**。
3. **阶段三（实现）**：严格按详设编码；完成后自检。
4. **评审**：`ts-code-review` / `vue-standard`（前端）、`tiansu-java-standards`（后端）产出分级报告，仅列项、由用户决策修/跳。
5. **提交**：`ts-git` 规范提交，禁 `reset --hard` / `--no-verify` / 强推等危险操作。

**人工卡点**：概设确认、详设确认、上线/发布/删数据前，必须停下等用户。

---

## 6. 设计文档约定（doc-repo）
- 概设 / 详设 / 接口设计 / 数据库设计文档存放于 `doc-repo/`，模板取自 `design-document-skill/examples`。
- 文档与代码冲突时，以**代码现状**为准并更新文档，避免把陈旧文档误判为「缺失实现」。

---

## 7. 协作边界（硬性）
- **只做用户当次要求的最小改动**；额外发现的问题（重复代码、可抽组件、潜在 bug）只汇报、不动手，等确认。
- 删除 / 覆盖 / 发布 / 改线上：先列清单、说清后果，等确认。
- 交付四件事：改了哪些文件、为什么、怎么验证、还剩什么。
- 路径用绝对路径；中文回复；代码、命令、路径、标识符保持 ASCII 原样。

---

## 8. 已照搬 / 待确认清单

### 8.1 已完成
- [x] `energy-vpp-service/ai-knowledge/skill/*`（9 个方法论技能）已逐字拷至 `backend/ai-knowledge/skill/`，均为明文：
  - `design-document-skill`、`documind`、`energy-sdd-workflow`、`frontend-api-sync`、`java-logging-standard`、`springboot-testing`、`tiansu-ddd`、`tiansu-java-standards`、`update-scaffold`
- [x] 后端 ORM 选型 = **MyBatis-Plus**（依 ai-knowledge 技能确定，无需再确认）
- [x] 前端规范方法论来源 `tiansupowers` 已登记（`CLAUDE.md` 第 5 章）；本系统**不引天溯 `Te-*` 组件库**，UI 改用开源 **Element Plus**，WorkBuddy 环境等价能力为 `ts-*` 用户级技能

### 8.2 待确认（不阻塞开工）
- [x] **前端组件库 = Element Plus**（用户确认：本系统无天溯组件库，UI 统一用开源 `element-plus`；高度定制的排课网格/拖拽在 Element Plus 之上自绘）。
- [ ] 两公司项目 `AGENTS.md / CLAUDE.md` 中**与本系统相关**的通用方法论条款（非能源业务、非私有 infra）可继续抽取并入本文档（按需，不强制）。
- [ ] tiansupowers 为内部 marketplace 插件（`http://192.168.20.76:8000/TA/tiansupowers.git`），本机 WorkBuddy 已具备等价 `ts-*` 技能；若后续改用 Claude Code 才需按 `CLAUDE.md` 第 5 章安装。
