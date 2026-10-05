# AI 分析助手迁移 Spring AI 可行性分析

> 版本：1.0 · 日期：2026-10-05 · 状态：待评审

## 一、背景与目标

AI 分析助手（`AgentServiceImpl`，约 800 行）当前为**手写实现**：hutool `HttpRequest` 直连大模型 + 手写 Function Calling 循环 + 手写工具定义 JSON（`agent/tools.json`）。项目已引入 Spring AI 1.0.2（当前仅使用 `PromptTemplate` 渲染提示词模板）。

本分析评估：将大模型调用与工具编排迁移到 **Spring AI 原生能力（ChatClient + DeepSeek Starter + @Tool）** 的可行性、方案与工作量。

## 二、现状盘点（迁移对象）

| 组成 | 现状 | Spring AI 对应能力 | 是否可替代 |
|---|---|---|---|
| HTTP 调用（callDeepSeek） | hutool 手写 POST + 响应校验 | ChatClient + `spring-ai-starter-model-deepseek` 自动配置 | 可替代 |
| 工具定义（14 个工具 JSON） | 手写 `agent/tools.json` | `@Tool` 注解自动生成 schema | 可替代 |
| 工具调用循环（≤6 轮） | 手写：解析 tool_calls → 执行 → 回填 → 再问 | ChatClient 内置 ToolCallingManager（自动循环）/ 或 Agent 抽象 | 部分替代（见风险 2） |
| 两阶段流程（计划→工具→结论） | 手写三段提示词组装 | 无对应抽象，仍需自组织 | 保留 |
| SSE 事件与执行链路可视化 | SseEmitter 手工推送 tool_call/tool_result/step | 框架无此能力 | **保留** |
| 步骤留痕（含图表指令 chart） | steps 列表 + 会话落库 | 无对应 | **保留** |
| 历史会话恢复（messages_json） | 手工序列化/回放 | ChatMemory 可做内存态，持久化仍需自管 | 保留现状 |
| 提示词模板 | 已用 PromptTemplate（prompts/*.st） | - | 已迁移 |
| 配置（key/model/base-url） | application.yml + secret 文件 | `spring.ai.deepseek.*` 属性族 | 迁移为 Spring AI 属性 |

## 三、Spring AI 1.0.2 能力核对（与本项目相关性）

1. **DeepSeek 官方支持**：BOM 含 `spring-ai-autoconfigure-model-deepseek`，提供 `spring.ai.deepseek.api-key / base-url / chat.options.model` 自动配置；base-url 可指向任意 OpenAI 兼容端点（含代理）。
2. **ChatClient**：统一对话入口，支持多轮 messages、系统提示词、温度等选项；非流式调用与现状一致。
3. **@Tool**：Bean 方法加 `@Tool(description=...)`，框架基于方法签名+参数注解自动生成 OpenAI function calling schema，无需维护 tools.json。
4. **结构化输出**（BeanOutputConverter）：可将"分析计划/核心结论"直接映射为 POJO（可选增强，非必需）。

## 四、推荐迁移方案（方案 B：ChatClient 单轮调用 + @Tool 注册 + 保留手写循环）

**关键取舍**：Spring AI 的**自动工具循环**（`.tools()` 后自动执行并继续对话）在内部完成多轮调用，**不暴露每轮的工具调用事件**——而"执行链路可视化"是本项目核心演示点，必须逐轮推送 SSE 事件。因此：

- 循环仍由 Java 手写（≤6 轮），每轮用 **ChatClient 单轮调用**（替代 hutool）；
- 工具 schema 由 **@Tool 注解**生成（删除 tools.json 与手写解析）；
- SSE、steps 留痕、会话持久化、图表指令**全部保留**。

### 改造清单

| # | 改动 | 文件 | 规模 |
|---|---|---|---|
| 1 | 依赖：`spring-ai-starter-model-deepseek`（含 openai 兼容客户端） | pom.xml | 1 行 |
| 2 | 配置迁移：`deepseek.*` → `spring.ai.deepseek.*`（secret 文件同步） | application.yml / application-secret.yml | 小 |
| 3 | 新建 `AgentTools` 组件：executeTool 的 14 个 switch 分支拆为 14 个 `@Tool` 方法（入参即参数，描述复用 tools.json） | 新增 1 个类 | 中 |
| 4 | callDeepSeek 改为 ChatClient 调用（保留响应校验与异常语义） | AgentServiceImpl | 小 |
| 5 | 工具循环改为：读取返回 `toolCalls`（含 @Tool 生成的 id/name/arguments）→ 反射调用 AgentTools → 回填 | AgentServiceImpl | 中 |
| 6 | 删除 `agent/tools.json` 与 loadToolsJson（或保留作为文档） | - | 清理 |

### 不改动的部分

- 两阶段流程、SSE 事件结构（前端零改动）、步骤留痕/图表指令、会话持久化与恢复、提示词模板体系
- Python 算法服务（RAG/报告仍走 httpx + FastAPI）

## 五、风险与应对

| # | 风险 | 等级 | 应对 |
|---|---|---|---|
| 1 | **@Tool 生成的 schema 描述比手写 tools.json 简略**，可能影响模型工具选择的准确率 | 低 | 用 `@Tool(description=...)` 原样保留工具级中文描述，`@ToolParam(description=..., required=...)` 逐参数保留描述与必填约束，语义与手写版等价；注意参数名 camelCase（原 snake_case）、JSON Schema 2020-12 方言两个小差异，迁移后对 14 个工具触发场景做一轮回归即可 |
| 2 | 自动工具循环不暴露轮次事件，若强用会导致执行链路功能丢失 | 高（方案性） | 已规避：采用单轮调用 + 手写循环 |
| 3 | DeepSeek starter 版本与 1.0.2 BOM 的实际兼容性（本项目只实测过 PromptTemplate） | 中 | 先在开发环境做一次连通性冒烟（对话 + 工具调用）再全面迁移 |
| 4 | Spring AI 异常语义（限流/超时/非 200）与现有 BizException 提示不一致 | 低 | 保留现有"响应校验 + 友好报错"包装层 |
| 5 | 比赛周期内改动量大导致回归风险 | 中 | 分两步提交：先换 ChatClient（行为等价），再切 @Tool；每步独立回归 |

## 六、工作量评估

| 阶段 | 内容 | 预估 |
|---|---|---|
| 1 | 依赖 + 配置迁移 + ChatClient 替换（行为等价，循环不动） | 0.5 天 |
| 2 | @Tool 改造 14 工具 + 循环适配 | 1 天 |
| 3 | 回归测试（典型提问样本、执行链路事件、会话恢复、图表指令） | 1 天 |
| **合计** | | **2.5 ~ 3 天** |

## 六.5 迁移进度（2026-10-05 更新）

**阶段 1（ChatClient 等价替换）已完成并通过端到端验证**：

- 依赖 `spring-ai-starter-model-deepseek` + 配置迁移到 `spring.ai.deepseek.*`（密钥仍在 application-secret.yml）
- `callDeepSeek` 改为 ChatClient 调用；响应还原为与原实现同构的 JSON（上层解析/会话持久化零改动）
- 工具 schema 继续以 `agent/tools.json` 为唯一来源，通过 `FunctionToolCallback`（inputSchema 原样透传）携带——描述/必填语义与手写完全一致，风险 1 实际为零
- 内部工具执行显式关闭（`internalToolExecutionEnabled=false`），执行链路逐轮 SSE 事件不受影响

**实战发现并解决的两个版本坑（重要）**：

1. **思考模式多轮回传缺陷**：Spring AI 1.0.2 的 DeepSeek 实现请求侧将 `reasoning_content` 硬编码为 null，且不支持 thinking 开关参数 → 思考模式下多轮工具调用必现 400。解决：新增 `DeepSeekAiConfig`——自定义 `RestClient.Builder`（@Primary）+ 拦截器向每个请求体注入 `{"thinking":{"type":"disabled"}}`，统一关闭思考模式（V4 通过请求参数切换）。副作用为正向：响应更快、token 更省。
2. **hutool `JSONArray.add` 返回 boolean**：链式调用误用会把布尔值写入 JSON，已修正为显式构造。

**阶段 2（@Tool 注解化）已完成并通过端到端验证（2026-10-05）**：

- 新建 `AgentToolService`：14 个工具方法 + `@Tool(name=...)` / `@ToolParam(description/required)` 声明式注册，逻辑与原 switch 逐字一致（含全部辅助方法）
- `@Tool.name` 显式保持 snake_case（query_kpi 等）、参数名保持 snake_case（start_year 等）——**与模型长期使用的 schema 完全一致，工具选择零退化**（实测模型直接调用 query_kpi 成功）
- `AgentServiceImpl.executeTool` 变为薄委托层（Map 参数提取 + 调用服务方法）；`buildToolCallbacks` 改用 `MethodToolCallbackProvider` 生成 schema
- `agent/tools.json` 与手工 FunctionToolCallback 路径已删除——schema 唯一来源为注解
- 实战坑：Spring AI 默认用 Java 方法名（camelCase）作为工具名，模型不认识导致执行失败——必须显式 `@Tool(name=...)`；同名工具会导致启动期 IllegalStateException（重复注册）

## 七、结论与建议

**结论：可行，风险可控，建议在时间允许时执行。**

收益：

1. **架构叙事升级**（比赛评分视角）：由"手写 hutool 调用 + 手写工具 JSON"变为"**基于 Spring AI 原生 ChatClient 与 @Tool 声明式工具编排**"，技术栈亮点更完整；
2. 维护性：新增工具只需一个 `@Tool` 方法，删除 tools.json 维护负担；
3. 与已引入的 PromptTemplate 形成统一的 Spring AI 技术体系。

不做的部分明确保留：执行链路可视化、SSE、会话体系——这些是平台差异化能力，Spring AI 无对应抽象，迁移反而会损失。

**建议节奏**：当前先完成比赛其他事项；若剩余 ≥3 天时间再启动本迁移，按第五节风险 5 的两步法推进。
