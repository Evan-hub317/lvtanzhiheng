# 绿碳智衡 · 区域碳排放大数据监测与仿真决策平台

面向政府碳管理部门的一体化平台，覆盖碳排放「**监测—核算—预警—预测—仿真—决策**」全链路，以大数据分析与 AI 技术支撑区域"双碳"治理。

> 软件设计比赛项目 · 演示数据为公开统计近似口径（无真实涉密数据）

---

## ✨ 核心亮点

| 亮点 | 说明 |
|---|---|
| 🌏 **全国地图热力大屏** | 31 省排放热力可视化 + 省级下钻，离线可用 |
| 🤖 **AI 预测与情景仿真** | LSTM 多步直出 + 历史增速校准仿真模型，滑杆拖动实时推演达峰路径 |
| 💬 **AI 碳管家（RAG）** | 政策知识库上传 → 分块向量化 → 检索增强问答，回答必标引用来源 |
| 🔍 **AI 异常检测** | 孤立森林全量建模，自动识别排放异常点（召回率 92%） |
| 📄 **AIGC 监测报告** | 一键生成图文报告（DeepSeek 撰写 + 平台图表），导出 PDF |
| 🏭 **全国数据仿真** | 31 省 + 424 市级单位、六大区域电网因子、北方供暖季节特征，全国总量 126.55 亿吨（与真实量级一致） |

## 🧩 功能模块

1. **数据总览**：KPI、年度趋势、行业结构，一键生成/核算全国模拟数据（2021 至今约 70 万条）
2. **监测大屏**：全国地图热力 + 省份排行 + 能源结构，30 秒自动刷新
3. **数据分析**：省/市/行业/能源多维钻取、月度季节规律、城市排行、排放明细
4. **情景仿真**：基线预测（LSTM）与政策仿真（历史增速校准 + 省级基准参数），蒙特卡洛置信区间、达峰识别、情景保存叠加对比
5. **预警中心**：阈值规则扫描（每日自动）+ 孤立森林 AI 异常检测，处置闭环
6. **AI 碳管家**：知识库上传（txt/md/pdf/docx）→ 流式 RAG 问答 → 会话留痕
7. **监测报告**：年报/月报一键生成（AI 撰写 + 趋势/结构附图），打印导出 PDF
8. **系统管理**：用户/角色管理（RBAC）、个人中心

## 🛠 技术架构

```
Vue3 + Element Plus + ECharts（前端）
        │ HTTP / SSE
SpringBoot 3.5 + MyBatis-Plus + MySQL 8 + Redis + Sa-Token（业务层）
        │ HTTP
Python FastAPI（算法层）：PyTorch LSTM · sklearn 孤立森林 · 蒙特卡洛
        │              · sentence-transformers RAG · DeepSeek API
```

**架构特点**：Java 业务层与 Python 算法层解耦的异构架构；数据库含 19+ 张表（维度/事实/预警/情景/知识库），核算由 SQL 聚合完成（70 万条秒级）。

## 📁 目录结构

```
smart/                 后端（SpringBoot 3.5）
  src/main/java/com/smart/
    controller/        接口层（认证/数据/核算/分析/仿真/预警/知识库/报告）
    service/           业务层（生成器/核算引擎/仿真引擎/预警/RAG 管理）
    mapper/            MyBatis-Plus 数据访问（核算与钻取为 SQL 聚合）
    client/AlgoClient  Python 算法服务客户端
web/                   前端（Vue3 + Vite）
  src/views/           页面（大屏/总览/分析/仿真/预警/AI 管家/报告/登录）
  src/components/      公共组件（RegionSelect 省市级联等）
  src/assets/china.json 全国地图 GeoJSON（本地化，离线可用）
algo/                  Python 算法服务（FastAPI，端口 8000）
docs/
  sql/                 数据库脚本（init / migration_cn / regions_cn）
  需求规格说明书.md     主 SRS（38 条功能需求）
  需求规格说明书-全国数据生成.md
  可行性分析-全国数据生成.md
  运行指南.md          详细运行与排障指南
```

## 🚀 快速开始

### 环境要求

JDK 17+ · MySQL 8.0+ · Python 3.9+ · Node.js 18+

### 1. 数据库初始化（仅首次）

```bash
mysql -uroot -p < docs/sql/init.sql           # 建库建表 + 基础字典
mysql -uroot -p < docs/sql/migration_cn.sql   # 结构迁移（省级参数/电网因子/唯一键）
mysql -uroot -p < docs/sql/regions_cn.sql     # 全国行政区划 + 省级参数 + 电网因子
```

### 2. Python 算法服务（端口 8000）

```bash
cd algo
pip install -r requirements.txt               # 仅首次
# 复制 .env.example 为 .env 并填入 DeepSeek API Key（AI 问答/报告需要）
python -m uvicorn main:app --host 0.0.0.0 --port 8000
# 验证：浏览器打开 http://localhost:8000/health
```

### 3. 后端（端口 8080）

```bash
cd smart
# 修改 src/main/resources/application.yml 中数据库密码
./mvnw.cmd spring-boot:run
# 接口文档：http://localhost:8080/doc.html
```

### 4. 前端（端口 5173）

```bash
cd web
npm install
npm run dev
```

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

## 🎬 演示故事线（10 分钟）

```
数据总览（趋势 110→126 亿吨）
  → 监测大屏（地图热力 + 省份下钻）
  → 预警中心（阈值扫描 + AI 检出异常，92% 召回）
  → 数据分析（多维钻取定位异常）
  → 情景仿真（基线不达峰 → 拖滑杆 → 政策路径达峰）
  → AI 碳管家（政策问答，带引用来源）
  → 监测报告（一键成文导出 PDF）
```

## 📊 数据说明

- **行政区划**：大陆 31 省级 + 424 市级单位（含直辖市辖区），名称与代码符合 GB/T 2260
- **省级参数**：GDP、二产占比、煤炭占比、能耗强度等为**公开统计近似值**（演示口径）
- **电网因子**：六大区域电网排放因子（生态环境部 2022 年度公告口径）
- **总量校准**：全国年排放 126.55 亿吨，落在 100~130 亿吨合理区间；省际格局与真实一致（江苏/山东/广东前列，西藏居末）

## 📚 文档

| 文档 | 说明 |
|---|---|
| [docs/运行指南.md](docs/运行指南.md) | 完整启动流程与常见问题排查 |
| [docs/需求规格说明书.md](docs/需求规格说明书.md) | 软件需求规格说明书（SRS） |
| [docs/可行性分析-全国数据生成.md](docs/可行性分析-全国数据生成.md) | 全国数据生成可行性分析 |
| [docs/需求规格说明书-全国数据生成.md](docs/需求规格说明书-全国数据生成.md) | 全国数据生成功能 SRS |

## 📄 License

本项目仅用于教学演示与软件设计比赛，数据均为模拟/公开统计近似口径。
