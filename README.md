# 备课排课管理系统

面向中小学语文教师的单人排课工具：排课录入、请假顺延、补课闭环、按月总览与收入统计。
Web 优先，暂不做登录与多角色；排课只排当月，每格 1 人，保存当月时自动关闭上月顺延。

> 完整需求见仓库根 `备课排课管理系统PRD.md`；开发规范见 `AGENTS.md` / `CLAUDE.md`；设计文档归档于 `doc-repo/`。

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue 3 + Vite + TypeScript + Element Plus，axios 封装 `/api` 请求 |
| 后端 | Spring Boot 3.2.5（Java 17）+ MyBatis-Plus 3.5.7 + MySQL 8.0 |
| 构建 | 前端 `vite`；后端 Maven（`spring-boot-maven-plugin`） |
| 规范 | 天溯 SDD 方法论（前端 `ts-*`、后端 `ai-knowledge` 技能），阶段门禁 + 人工卡点 |

## 仓库结构

```
prepare-lesson-miniprogram/
├── frontend/                  # Vue3 + Vite 前端（dev 端口 5173，代理 /api → 后端）
│   └── src/
│       ├── api/               # 接口请求层（按模块拆分）
│       ├── views/             # 管理后台 / 前台看板
│       ├── components/        # 业务组件（Element Plus 之上自绘排课网格/拖拽）
│       ├── router/ store/ utils/ styles/
│       └── vite.config.ts
├── backend/                   # Spring Boot 后端（默认端口 18899）
│   ├── db/schema.sql          # 建库建表脚本
│   ├── src/main/resources/    # application.yml + application-dev/prod.yml
│   └── src/main/java/com/lesson/schedule/
│       ├── controller/ service/ entity/ mapper/ common(dto,vo)/
│       └── ScheduleApplication.java
├── doc-repo/                  # 概设 / 详设 / 接口 / 库表文档
├── prototype/                 # UI 原型
├── AGENTS.md  CLAUDE.md        # 开发规范（强制遵循）
└── 备课排课管理系统PRD.md
```

## 环境要求

- JDK 17、Maven 3.9+
- Node.js 18+（前端）
- MySQL 8.0（开发默认 `localhost`，生产库部署于 `47.116.35.76`）

## 快速开始

### 1. 初始化数据库

```bash
mysql -uroot -p < backend/db/schema.sql
```

脚本建库 `lesson_db` 及 Student / TimeSlot / Course / Lesson / Dict 等表。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
# 或打 jar 后运行（默认 dev 环境）：
mvn package -DskipTests
java -jar target/schedule-1.0.0.jar
```

默认 `SPRING_PROFILES_ACTIVE=dev`，监听 `SERVER_PORT:18899`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

Vite 通过 `server.proxy` 把 `/api` 转发到后端（默认 `http://47.116.35.76:18899`，本地联调可在 `vite.config.ts` 改为 `http://localhost:18899`）。

## 配置（dev / prod 分层）

配置拆为三份，公共部分恒定生效，环境专属部分按 profile 加载：

| 文件 | 作用 |
|---|---|
| `application.yml` | 公共：端口、应用名、profile 切换、`mybatis-plus` 基础、日志 root 级别 |
| `application-dev.yml` | 开发：连本机 MySQL，开 SQL 日志 + debug 日志 |
| `application-prod.yml` | 生产：连 `47.116.35.76`，关 SQL 日志、日志降为 warn，**账号密码无默认值（未注入即启动失败）** |

切换环境：`java -jar schedule.jar --SPRING_PROFILES_ACTIVE=prod`。

数据源可经环境变量 / 命令行参数覆盖：

| 变量 | 含义 | dev 默认 | prod 默认 |
|---|---|---|---|
| `DB_HOST` | 数据库主机 | `localhost` | `47.116.35.76` |
| `DB_PORT` | 端口 | `3306` | `3306` |
| `DB_NAME` | 库名 | `lesson_db` | `lesson_db` |
| `DB_USER` | 用户名 | `root` | （必填） |
| `DB_PASSWORD` | 密码 | `123456` | （必填） |
| `SERVER_PORT` | 服务端口 | `18899` | `18899` |

> 字符集用 Java 字符集名 `UTF-8`（非 MySQL 的 `utf8mb4`，驱动不识别）；
> 生产 `useSSL=false` 因 `47.116.35.76` 未配 SSL 证书（已确认）。

## 接口约定（动作化 RESTful）

后端统一 **POST + 动作化子路径**，路径中不含业务参数，`id` 等统一放 `@RequestBody`：

- 资源基路径 + 动作子路径：`/api/students` + `/list` `/detail` `/create` `/update` `/delete` `/pause`
- `id` 类参数用 `IdDTO { id }` 经 body 传入；列表/查询参数用 `*QueryDTO`
- 月 / 年类统计查询（如 `BE-API-15/20/30~32`）保留 `@RequestParam`（`year` / `month` / `date`）
- 每个接口在 Controller 内有 `BE-API-xx` 编号与用途注释，前后端据此对齐

前端 `src/api/*` 通过 `request.post(url, body)` 调用（月查询用 `request.post(url, undefined, { params })` 透传 query），
路径与后端一一对应；本地联调可设 `USE_MOCK=true` 走内存假数据。

## 文档索引

- `备课排课管理系统PRD.md`：业务需求（终稿）
- `AGENTS.md` / `CLAUDE.md`：开发规范、分层架构、阶段门禁
- `doc-repo/`：概设、详设、接口设计、库表设计
- `backend/ai-knowledge/skill/`：后端方法论文档（DDD / Java 规范 / 测试 / 设计文档模板）
