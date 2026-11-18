# agent-console

本机 AI 编程助手 **用量成本 + Agent 资产** 统一控制台。

日常同时使用 Codex、Trae、Claude Code 时，token 消耗、积分和费用分散在各自的本地日志里，Skill 与 MCP 配置也散落在不同目录。本项目把这些数据在本机采集、归一化入库，用一个页面统一查看。

## 功能

### 用量与成本
- **多平台采集**：Codex（读取 `~/.codex/sessions/**/*.jsonl`）、Trae（导入积分用量 Excel）、Claude Code（资产）
- **增量扫描**：按文件 `byte_offset` 记录游标，只读取新增内容，避免重复解析；单次扫描通过 `AtomicBoolean` 防并发
- **成本核算**：按「provider + model + 生效日期」维护价格表，支持输入 / 缓存命中 / 缓存写入 / 输出四段计价；缺少价格的记录打 `price_missing` 标记，补价后可批量重算
- **统计口径**：今日 / 本月 / 近 30 天趋势 / 累计，按模型、平台、项目多维聚合，输出调用次数、token 数、积分与金额

### 资产盘点
- **Agent 实例**：扫描各平台会话与配置，落库平台、项目、工作目录、模型、状态等
- **Skill**：解析 `SKILL.md` 的 front matter（name / description），按路径哈希去重
- **MCP**：解析 `config.toml`、`.claude.json`、`mcp.json`，记录传输方式、命令、参数、环境变量和健康状态

### 其他
- 定时扫描（默认 2 分钟一次）与手动触发，保留最近扫描记录与错误信息
- 单页前端，含总览 / Agents / 模型开销 / Skills / MCP / 扫描与导入 / 设置七个视图

## 技术栈

| 层次 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 3.5、Spring Web、Validation |
| 持久层 | MyBatis-Plus 3.5（含 JSqlParser）、MySQL 8 |
| 日志解析 | Jackson、tomlj（TOML）、Apache POI（xlsx） |
| 前端 | 原生 HTML / CSS / JavaScript，无构建步骤 |

## 整体结构

```
本地 AI 工具目录                     采集层                        存储                      展示
~/.codex/sessions/*.jsonl  ─┐
~/.codex/skills, config.toml│
~/.trae-cn/skills, mcp.json ├─→  Adapter（Codex/Trae/Claude）─→ ScanService ─→ MySQL ─→ REST API ─→ SPA
Trae 积分 xlsx             ─┤        增量游标 + 去重                        (10 张表)      /api/**    (static/)
~/.claude/skills, .claude.json┘
```

采集只读取本机文件，不上传任何数据到外部服务。

## 数据表

| 表 | 说明 |
| --- | --- |
| `model_usage` | 调用明细：模型、平台、项目、token 分类、积分、金额，按 `(platform, external_id)` 去重 |
| `model_price` | 分时段价格表，按 `(provider, model, effective_from)` 唯一定价 |
| `agent_instance` | Agent 实例及其工作目录、项目、模型、累计消耗 |
| `skill` / `mcp_server` / `mcp_tool` | 资产清单与 MCP 工具定义 |
| `scan_run` / `scan_cursor` | 扫描执行记录与文件读取游标 |
| `usage_import` | Excel 导入记录（文件哈希去重、行数、积分） |
| `app_setting` | 运行时配置，如积分汇率、默认 xlsx 路径 |

建表脚本 [schema.sql](src/main/resources/schema.sql) 在启动时自动执行（`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`，可重复运行）。

## 快速开始

### 1. 准备环境
- JDK 21
- Maven 3.9+
- MySQL 8（库不存在时会自动创建）

### 2. 配置数据库
数据库连接通过环境变量注入，仓库中不含任何凭据：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `localhost` | MySQL 地址 |
| `DB_PORT` | `3306` | 端口 |
| `DB_NAME` | `agent_console` | 数据库名 |
| `DB_USER` | `root` | 用户名 |
| `DB_PASSWORD` | 空 | 密码，**必须设置** |

PowerShell：
```powershell
$env:DB_PASSWORD='your-password'
```

### 3. 配置采集路径（可选）
默认从当前用户主目录推导，非默认安装位置时再覆盖：

| 变量 | 默认值 |
| --- | --- |
| `CODEX_HOME` | `~/.codex` |
| `TRAE_HOME` | `~/AppData/Roaming/Trae CN` |
| `TRAE_CN_HOME` | `~/.trae-cn` |
| `CLAUDE_HOME` | `~/.claude` |
| `TRAE_USAGE_XLSX` | `~/Desktop/Trae积分用量.xlsx` |

### 4. 启动
```bash
mvn spring-boot:run
```

打开 <http://127.0.0.1:8848>。首次启动会建表，随后由定时任务自动采集；也可在「扫描与导入」页手动触发。

## 接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/overview` | 总览：今日/本月/累计、趋势、平台卡片、Top 模型、缺价数量 |
| GET | `/api/usage/summary` | 用量汇总（支持时间、平台、项目过滤） |
| GET | `/api/usage/daily` | 按天趋势 |
| GET | `/api/usage/by-model` | 按模型聚合 |
| GET | `/api/usage/by-platform` | 按平台聚合 |
| GET | `/api/usage/by-project` | 按项目聚合 |
| GET | `/api/usage/records` | 调用明细分页 |
| GET/POST/DELETE | `/api/prices` | 价格表查询、新增、删除 |
| POST | `/api/scan` | 手动触发全量扫描 |
| GET | `/api/scan/runs` | 扫描执行记录 |
| POST | `/api/import/trae` | 导入 Trae 积分 Excel |
| GET | `/api/import/history` | 导入历史 |
| GET | `/api/agents` | Agent 实例列表 / 详情 |
| GET | `/api/skills` `/api/mcp` `/api/assets/list` `/api/assets/content` | 资产查询与内容预览 |
| GET/PUT | `/api/settings` | 运行时配置读写 |

统一返回 `{ code, message, data }`，异常由 `GlobalExceptionHandler` 兜底。

## 目录结构

```
src/main/java/com/lenovo/agentconsole
├── adapter        # 各平台日志/配置解析（Codex / Trae / Claude）
├── controller     # REST 接口，仅做参数接收与结果封装
├── service        # 业务逻辑：扫描、成本重算、统计、导入、资产
├── domain/entity  # MyBatis-Plus 实体
├── mapper         # Mapper 接口
├── job            # 定时扫描任务
├── config         # 配置绑定与 MyBatis-Plus 配置
├── common         # 统一返回体与全局异常
└── support        # 哈希、路径、Map 取值等工具
src/main/resources
├── application.yml
├── schema.sql
└── static         # 单页前端（index.html / app.js / style.css）
```

## 已知限制

- 定位为**单机工具**，仅监听 `127.0.0.1`，没有登录与鉴权，不建议对外暴露
- 用量数据依赖各平台本地日志格式，工具版本升级后可能需要调整解析逻辑
- 价格表为手工维护，汇率（Trae 积分 → CNY）在「设置」中配置
