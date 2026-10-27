# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

「绿碳智衡」区域碳排放大数据监测与仿真决策平台（软件设计比赛项目，演示数据为模拟/公开统计近似口径）。覆盖排放「监测—核算—预警—预测—仿真—决策」全链路，含 9 大功能模块：数据总览、监测大屏、数据分析、情景仿真、预警中心、AI 知识库（RAG）、AI 分析助手（Agent）、监测报告（AIGC）、系统管理。详见 [README.md](README.md) 与 [docs/运行指南.md](docs/运行指南.md)。

**三服务异构架构**（Java 管业务与鉴权，算法一律走 Python HTTP 调用）：

```
web/   Vue3 + Element Plus + ECharts（端口 5173，dev 代理 /api→8080、/algo→8000）
smart/ SpringBoot 3.5.4 + MyBatis-Plus 3.5.9 + MySQL + Redis + Sa-Token + Spring AI 1.0（端口 8080）
algo/  Python FastAPI 单文件 main.py（端口 8000）：LSTM 预测、政策仿真、孤立森林、RAG、报告生成
```

## 常用命令

### 后端（smart/）

```bash
cd smart
mvn compile              # 编译（本机 Maven 3.9.9 + JDK 23，pom 已显式配 Lombok 注解处理器，勿删）
mvn package -DskipTests  # 打包 → target/smart-0.0.1-SNAPSHOT.jar（测试仅有 1 个上下文加载用例，可跳过）
mvn test -Dtest=SmartApplicationTests   # 跑测试

# 启动：必须回到项目根目录再执行（upload-dir: uploads 按进程工作目录解析，
# 在项目根启动时上传文件才落在项目根 uploads/，与知识库 kb 同级）
cd ..
mvn -f smart/pom.xml spring-boot:run    # 接口文档 http://localhost:8080/doc.html
```

- **本地 Maven 仓库在 `d:/maven/localRepository`**（`~/.m2/settings.xml` 指定，阿里云镜像），不是默认 `~/.m2/repository`。怀疑依赖问题时先查该目录下 jar 是否完整。
- 数据库连接改 [application.yml](smart/src/main/resources/application.yml)；大模型 Key 在 `application-secret.yml`（不入库，缺失也能启动）。

### 前端（web/）

```bash
cd web
npm install && npm run dev     # 开发（5173）
npm run build                  # 产物 dist/，Nginx 托管
```

- 后端/算法地址由 [public/config.js](web/public/config.js) 运行时决定：默认直连服务器，本地联调改为 `'/api'`、`'/algo'` 走 Vite 代理。

### 算法服务（algo/）

```bash
cd algo
pip install -r requirements.txt   # 版本已锁定；torch 未装时预测自动降级为趋势模型
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

- 配置在 `algo/.env`（DeepSeek Key / MySQL 连接，优先于系统环境变量，不入库）；Key 缺失时 AI 问答走离线兜底。
- **改了 main.py 必须重启 uvicorn 才生效**；BGE 模型走国内镜像 `hf-mirror.com`。

### 数据库（MySQL，库名 smart_carbon）

```bash
mysql -uroot -p < docs/sql/smart_carbon_struct.sql   # 结构脚本（仓库内，27 张表，幂等可重跑）
# 结构与数据完整版 smart_carbon.sql 未随仓库分发（网盘，见运行指南）；远程服务器 MySQL 为 5.7
```

交付版数据随完整版 SQL 导入（含 2016 至今模拟数据与核算结果）；数据生成/核算接口保留在后端，仅供开发调试（需管理员 token 放 `satoken` 请求头；全量约 5+2 分钟）：

```bash
curl -X POST http://localhost:8080/data/generate -H "satoken: <token>" -H "Content-Type: application/json" -d '{"mode":"all","startYear":2016}'
curl -X POST http://localhost:8080/calc/execute  -H "satoken: <token>" -H "Content-Type: application/json" -d '{"startYear":0,"endYear":0}'
```

此后每月 1 日 00:30 定时任务自动增量生成并核算。

## 架构要点

### 后端分层与数据流（smart/）

`controller → service(+impl) → mapper`，统一 `Result` 包装 + `BizException` + `GlobalExceptionHandler`。核心数据链路：

1. **数据生成** [DataGenerateService.java](smart/src/main/java/com/smart/service/DataGenerateService.java)：生成 369 个统计单元（333 地级 + 4 直辖市 + 32 省直辖县级）× 5 行业 × 8 能源的月度活动数据（2016 起，约 115 万条，入库 `fact_energy_month`；模拟数据，生成依据见 README「数据说明」）。以 2021 为校准锚年（`BASE_YEAR`），此前回推、此后外推，含 2020 疫情/2017-18 煤电年份调整。
2. **核算** [CalcServiceImpl.java](smart/src/main/java/com/smart/service/impl/CalcServiceImpl.java)：SQL 聚合活动数据 → `fact_emission_month`/`fact_emission_year`（排放 = 活动量 × 因子 × 氧化率）。执行有 AtomicBoolean 互斥防重复点击。完成后**后台线程**重建 6 张 stat_* 汇总表（不拖长接口耗时）。
3. **查询提速（汇总表 + 回退模式）**：全国/省级口径的大屏、分析、仿真查询优先走 [SummaryMapper.java](smart/src/main/java/com/smart/mapper/SummaryMapper.java)（`stat_year_total` 等 6 张表，`region_id=1` 为全国哨兵值，省 ID 用 dim_region 的 id）；**汇总表为空/未建时自动回退明细表聚合**（各 ServiceImpl 均已实现该模式，新增查询请沿用）。市级口径始终走明细表。
4. **排放口径约定**：`energy_id ≤ 6`（原煤/焦炭/原油/汽油/柴油/天然气）= 直接排放，计入总量；7=热力、8=电力为间接排放，单列展示不计总量。SQL 里 `energy_id <= 6` 是常见过滤条件，别误删。
5. **算法调用** [AlgoClient](smart/src/main/java/com/smart/client/AlgoClient.java)：HTTP 调 algo 服务（`algo.base-url` 配置），接口 `/api/alg/predict|simulate|anomaly|embed|kb/refresh|rag-chat|report`。

### AI 分析助手（Agent，Spring AI 原生）

- [AgentToolService.java](smart/src/main/java/com/smart/service/AgentToolService.java)：14 个分析工具用 `@Tool` 注解声明式注册，schema 由注解生成。
- 提示词模板 `resources/prompts/*.st`（system/plan/conclusion 三段式），部署时 jar 旁放同名 `prompts/` 目录可热改。
- SSE 流式输出执行链路（分析规划 → 工具执行 → 综合结论），会话存 `agent_session`/`chat_message`。

### 鉴权（Sa-Token 1.39，已踩坑）

- 前端请求头 `satoken` 携带 token；除 `/auth/login`、文档、静态资源外全部需登录（[SaTokenConfig.java](smart/src/main/java/com/smart/config/SaTokenConfig.java)）。
- **1.39 版本坑**：`SaInterceptor` 只有单参构造（auth 参数是 handler，需经 `SaHolder.getRequest()` 取请求）；注解鉴权链式 `.isAnnotation(true)`；`@SaCheckRole` 依赖 [StpInterfaceImpl.java](smart/src/main/java/com/smart/config/StpInterfaceImpl.java) 从库查角色，缺了全员 403。
- CORS 预检 OPTIONS 已在拦截器内显式放行，勿删。

### 前端结构（web/）

`src/api/*.js` 按模块封装接口（axios 实例 [request.js](web/src/utils/request.js) 统一带 token/错误提示）→ `src/views/` 页面 + `src/components/` 公共组件；大屏地图用本地 `src/assets/china.json`（离线可用）；报告导出 PDF 在浏览器端。

## 关键约定

- **配置与密钥不入库**：`application-secret.yml`、`algo/.env`、`web/public/config.js` 均为部署环境文件；知识库原文件存 `uploads/kb/`（`app.upload-dir` 配置）。
- **SQL 文件两份**：仓库内 `docs/sql/smart_carbon_struct.sql` 仅表结构；完整版在网盘（见运行指南）。**提交前需从当前库重新导出网盘版**：必须含 stat_* 表与 2016+ 模拟数据及核算结果（旧网盘版暂缺，勿直接沿用）。
- **依赖版本锁定勿动**：SB 3.5.4（SB4 生态不兼容 MP/Sa-Token/Knife4j）、MP 3.5.9 + 单独引 `mybatis-plus-jsqlparser`（分页插件）、Sa-Token 1.39、Knife4j 4.5 + springdoc 2.8.9 显式覆盖；easyexcel 已注释禁用（poi 与知识库解析的 poi-ooxml 5.2.5 冲突）。
- **部署（宝塔）**：jar 同级放 `uploads/`（含 kb、images 子目录）、可选 `prompts/`，启动加 `--app.upload-dir=uploads`（开发默认 `../uploads` 指向项目根，见 application.yml）；Nginx history 路由 `try_files` + `.mjs` 补 MIME + SSE 反代需 `proxy_buffering off`。详见运行指南。
- 文档更新需同步：改功能/口径后记得更新 README 与运行指南（比赛交付物，答辩演示故事线在 README 附录）。
# Summary Instructions
When summarizing this conversation, always preserve:
- The current task objective and acceptance criteria
- File paths that have been read or modified
- Test results and error messages
- Decisions made and the reasoning behind them