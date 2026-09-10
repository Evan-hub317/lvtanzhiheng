# 绿碳智衡 - Python 算法服务

Java 业务层的计算后端：LSTM 预测、情景仿真、孤立森林异常检测、文本向量化、RAG 问答。

## 运行

```bash
pip install -r requirements.txt
# 可选：设置 DeepSeek API Key（不设置则 AI 问答走离线兜底话术）
set DEEPSEEK_API_KEY=sk-xxx        # Windows
export DEEPSEEK_API_KEY=sk-xxx     # Linux
uvicorn main:app --host 0.0.0.0 --port 8000
```

接口文档：http://localhost:8000/docs

## 接口一览

| 接口 | 说明 |
|---|---|
| GET /health | 健康检查 |
| POST /api/alg/predict | 历史序列 → 未来 10 年预测 + 蒙特卡洛 95% 置信区间 + 达峰年份 |
| POST /api/alg/simulate | 情景参数（煤炭占比/工业占比/能效下降率）→ 排放轨迹仿真 |
| POST /api/alg/anomaly | 孤立森林异常检测，返回异常点与分数 |
| POST /api/alg/embed | 文本向量化（512 维） |
| POST /api/alg/rag-chat | RAG 问答（SSE 流式），检索 MySQL 知识库 + DeepSeek 生成 |

## 注意

- **首次调用** embed/rag-chat 会自动下载 bge-small-zh-v1.5 模型（约 100MB），需联网；之后离线可用（RAG 检索走本地向量，仅大模型生成需 DeepSeek API）。
- 知识库分块首次检索时自动补算向量并回写 `kb_chunk.vector`。
- DeepSeek 不可用时自动降级为预置政策常识兜底回答，保证演示不中断。
- 蒙特卡洛抽样次数可调（默认 300），保证仿真接口 2 秒内返回。
