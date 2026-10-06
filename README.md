# 绿碳智衡 · 区域碳排放大数据监测与仿真决策平台

碳管理一体化平台，覆盖碳排放「**监测—核算—预警—预测—仿真—决策**」全链路，以大数据分析与 AI 技术支撑区域"双碳"治理。

> 软件设计比赛项目 · 演示数据为公开统计近似口径（无真实涉密数据）

---

## 核心亮点

| 亮点 | 说明 |
|---|---|
| AI 分析助手（Agent） | 自然语言提问，基于 Spring AI 原生 ChatClient + @Tool 声明式工具编排，14 个分析工具自主调用，执行链路全程可视化（分析规划 → 工具执行 → 综合结论），"江苏明年会超标吗"一问即答 |
| 全国地图热力大屏 | 31 省排放热力可视化 + 省级下钻 |
| AI 预测与情景仿真 | LSTM 多步直出 + 政策逐年深化仿真模型（省级基准参数），达峰年份随政策强度移动，滑杆拖动实时推演 |
| AI 知识库（RAG） | 政策知识库上传 → 分块向量化 → 检索增强问答，回答必标引用来源，点击引用可跳转原文定位；docx/pdf/txt 应用内浏览（pdf.js 渲染，不依赖浏览器） |
| AI 异常检测 | 孤立森林全量建模，自动识别排放异常点（召回率 92%） |
| AIGC 监测报告 | 一键生成图文报告（大模型撰写 + 平台图表），导出 PDF |
| 全国数据仿真 | 31 省 + 424 市级单位、六大区域电网因子、北方供暖季节特征，全国总量 111.59 亿吨（与真实量级一致） |

## 功能模块

1. **数据总览**：KPI、年度趋势、行业结构，一键生成/核算全国模拟数据（2021 至今约 70 万条）
2. **监测大屏**：全国地图热力 + 省份排行 + 能源结构（直接/间接排放口径分列），30 秒自动刷新
3. **数据分析**：省/市/行业/能源多维钻取、月度季节规律、城市排行、排放明细
4. **情景仿真**：基线预测（LSTM）与政策仿真（历史增速校准 + 政策逐年深化 + 省级基准参数），蒙特卡洛置信区间、达峰识别、情景保存叠加对比
5. **预警中心**：阈值规则扫描（每日自动）+ 孤立森林 AI 异常检测，处置闭环
6. **AI 知识库**：知识库上传（txt/md/pdf/docx）→ 流式 RAG 问答 → 会话留痕；下载/渲染进度条；删除文档同步清理原文件
7. **AI 分析助手**：对话式分析 Agent——14 个工具自主编排（查询/预测/仿真/异常检测/阈值判断/报告），SSE 流式执行链路可视化，多轮会话有记忆
8. **监测报告**：年报/月报一键生成（AI 撰写 + 趋势/结构附图），打印导出 PDF
9. **系统管理**：用户/角色管理（RBAC）、个人中心

## 技术架构

```
Vue3 + Element Plus + ECharts + pdf.js（前端）
        │ HTTP / SSE
SpringBoot 3.5 + MyBatis-Plus + MySQL 8 + Sa-Token + Spring AI 1.0（业务层）
        │ HTTP（算法服务）│ ChatClient（Agent 直连大模型）
Python FastAPI（算法层）：PyTorch LSTM · sklearn 孤立森林 · 蒙特卡洛
        │              · sentence-transformers RAG · DeepSeek API
```

**架构特点**：

- Java 业务层与 Python 算法层解耦的异构架构
- **对话式 Agent 全 Spring AI 原生**：ChatClient（DeepSeek Starter）调用 + `@Tool` 声明式工具注册（`AgentToolService` 14 个工具，schema 由注解生成）+ `PromptTemplate` 渲染提示词（`resources/prompts/*.st`，部署时可在 jar 旁放置同名文件热改）
- **仿真模型**：历史增速校准 + 政策效应逐年深化（体现"政策逐年加码"），达峰年份随煤炭占比/工业占比/能效下降率三个政策参数移动，基年纳入峰值判定（起点即低于去年时报"已达峰"）
- 数据库 20 张表（维度/事实/预警/情景/知识库/Agent 会话），核算由 SQL 聚合完成（70 万条秒级）

## 目录结构

```
smart/                 后端（SpringBoot 3.5 + Spring AI 1.0）
  src/main/java/com/smart/
    controller/        接口层（认证/数据/核算/分析/仿真/预警/知识库/报告/Agent）
    service/           业务层（生成器/核算引擎/仿真引擎/预警/RAG 管理）
      AgentToolService       Agent 的 14 个分析工具（@Tool 声明式注册）
      impl/AgentServiceImpl  Agent 会话与工具循环（SSE 执行链路）
    mapper/            MyBatis-Plus 数据访问（核算与钻取为 SQL 聚合）
    client/AlgoClient  Python 算法服务客户端
    config/            鉴权/跨域/DeepSeek 定制（thinking 关闭拦截器）
  src/main/resources/
    prompts/           Agent 提示词模板（.st；部署时 jar 旁 prompts/ 目录可热改覆盖）
    application-secret.yml  大模型密钥（不入库，由 application.yml 导入）
web/                   前端（Vue3 + Vite）
  public/config.js     部署配置：后端/Python 地址（直连或相对路径走代理，改完即生效）
  src/views/           页面（大屏/总览/分析/仿真/预警/AI 知识库/AI 分析助手/报告/登录）
  src/components/      公共组件（RegionSelect 省市级联等）
  src/assets/china.json 全国地图 GeoJSON（本地化，离线可用）
algo/                  Python 算法服务（FastAPI 单文件，端口 8000）
  .env                 配置文件（DeepSeek Key/模型/MySQL 连接，不入库）
docs/
  sql/                 数据库脚本（smart_carbon：结构与数据；仅表结构版在网盘，见运行指南）
  需求规格说明书.md     主 SRS（38 条功能需求）
  需求规格说明书-全国数据生成.md
  可行性分析-全国数据生成.md
  可行性分析-对话式分析Agent.md
  可行性分析-SpringAI迁移.md
  运行指南.md          详细运行与排障指南
tools/                 数据准备工具（gen_regions.py：行政区划 SQL 种子生成，一次性脚本）
```

## 快速开始

### 环境要求

JDK 17+ · MySQL 8.0+ · Python 3.9+ · Node.js 18+

### 1. 数据库初始化（仅首次）

```bash
# 完整初始化：结构与数据一步到位（建库建表 + 基础字典/因子/账号/预设情景）
mysql -uroot -p < docs/sql/smart_carbon.sql
```

> 仅表结构版 `smart_carbon_struct.sql` 未随仓库分发（体积较大），见运行指南中的网盘链接。

### 2. Python 算法服务（端口 8000）

```bash
cd algo
pip install -r requirements.txt               # 仅首次（版本已锁定；torch 可选，未装自动降级）
# 编辑 .env：DeepSeek Key / 模型名 / MySQL 连接（.env 为配置文件，优先于系统环境变量）
python -m uvicorn main:app --host 0.0.0.0 --port 8000
# 验证：浏览器打开 http://localhost:8000/health
```

### 3. 后端（端口 8080）

```bash
cd smart
# 修改 src/main/resources/application.yml 中数据库密码
# 大模型 Key 在 application-secret.yml（不入库；未创建时复制 application.yml 中注释说明的结构自建）
./mvnw.cmd spring-boot:run
# 接口文档：http://localhost:8080/doc.html
```

### 4. 前端（端口 5173）

```bash
cd web
npm install
npm run dev
```

前端后端地址由 `public/config.js` 决定：默认直连服务器（`http://47.93.59.151:8080` / `:8000`）；本地联调时改为 `'/api'`、`'/algo'` 走 Vite 代理（vite.config.js 内置规则，无需改动）。

### 5. 初始化全国数据（首次）

登录后在工作台点击「生成模拟数据」（全量约 5 分钟）→「执行核算」（约 2 分钟），或调接口：

```bash
curl -X POST http://localhost:8080/data/generate -H "satoken: <登录token>" -H "Content-Type: application/json" -d '{"mode":"all","startYear":2021}'
curl -X POST http://localhost:8080/calc/execute  -H "satoken: <登录token>" -H "Content-Type: application/json" -d '{"startYear":0,"endYear":0}'
```

此后数据**每月 1 日 00:30 自动增量生成上月数据并核算**，无需人工干预。

### 默认账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 系统管理员 |
| demo | admin123 | 业务用户 |

## 服务器部署要点（宝塔面板）

```bash
# 后端启动命令（绝对 upload-dir 摆脱工作目录依赖）
/www/server/java/jdk-17.0.8/bin/java -jar -Xmx1024M -Xms256M /www/wwwroot/shienhao/smart/smart/smart-0.0.1-SNAPSHOT.jar --server.port=8080 --app.upload-dir=/www/wwwroot/shienhao/smart/smart/uploads
```

- **目录布局**：jar 同级放 `uploads/kb/`（知识库原文件）；可选放 `prompts/`（提示词热改）
- **Nginx 静态站**：`location / { try_files $uri $uri/ /index.html; }`（history 路由刷新防 404）；`.mjs` 文件补 MIME（`location ~ \.mjs$ { default_type application/javascript; }`，勿用 types 块）
- **反代模式**（可选，config.js 改回相对路径后启用）：`/api/` → 8080、`/algo/` → 8000，两端都要 `proxy_buffering off; proxy_cache off;`（SSE 流式）
- **端口**：直连模式需放行 8080/8000；反代模式只需 80/443

## 演示故事线（10 分钟）

```
数据总览（趋势 97→112 亿吨）
  → 监测大屏（地图热力 + 省份下钻）
  → 预警中心（阈值扫描 + AI 检出异常，92% 召回）
  → 数据分析（多维钻取定位异常）
  → 情景仿真（基线未达峰 → 低碳情景 2028 达峰 → 强化情景 2027 达峰）
  → AI 分析助手（"江苏明年会超标吗"→ Agent 自主编排：分析规划→预测→阈值判断→结论）
  → AI 知识库（政策问答，带引用来源，点击引用跳转原文）
  → 监测报告（一键成文导出 PDF）
```

**AI 分析助手示例提问**：

- "江苏明年会超标吗？按 12 亿吨算"
- "河南排放最多的城市是哪个"
- "江苏各产业的碳排放情况是什么"
- "全国 2030 年会达峰吗"

## 数据说明

- **行政区划**：大陆 31 省级 + 424 市级单位（含直辖市辖区），名称与代码符合 GB/T 2260
- **省级参数**：GDP、二产占比、煤炭占比、能耗强度等为公开统计近似值（演示口径）
- **电网因子**：六大区域电网排放因子（生态环境部 2022 年度公告口径）
- **排放口径**：总量为化石能源直接排放（原煤/焦炭/原油/汽油/柴油/天然气 6 类）；电力/热力为间接排放，单列展示、不计入总量，避免重复计算
- **总量校准**：全国年排放 111.59 亿吨，落在 100~130 亿吨合理区间；省际格局与真实一致（江苏/山东/广东前列，西藏居末）
- **情景预设**：低碳情景（煤 ×0.94、工业 ×0.96、能效 2.5%）与强化低碳情景（煤 ×0.85、工业 ×0.92、能效 3.5%）按现实政策节奏校准

## 文档

| 文档 | 说明 |
|---|---|
| [docs/运行指南.md](docs/运行指南.md) | 完整启动流程与常见问题排查 |
| [docs/需求规格说明书.md](docs/需求规格说明书.md) | 软件需求规格说明书（SRS） |
| [docs/可行性分析-全国数据生成.md](docs/可行性分析-全国数据生成.md) | 全国数据生成可行性分析 |
| [docs/需求规格说明书-全国数据生成.md](docs/需求规格说明书-全国数据生成.md) | 全国数据生成功能 SRS |
| [docs/可行性分析-对话式分析Agent.md](docs/可行性分析-对话式分析Agent.md) | AI 分析助手（Function Calling Agent）可行性分析 |
| [docs/可行性分析-SpringAI迁移.md](docs/可行性分析-SpringAI迁移.md) | AI 分析助手迁移 Spring AI（ChatClient + @Tool）可行性分析 |

## License

本项目仅用于教学演示与软件设计比赛，数据均为模拟/公开统计近似口径。
