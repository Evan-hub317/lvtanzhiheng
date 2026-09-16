# -*- coding: utf-8 -*-
"""
绿碳智衡 - Python 算法服务
================================================
职责：排放预测（LSTM+蒙特卡洛）、情景仿真、孤立森林异常检测、
      文本向量化、RAG 知识库问答（DeepSeek，SSE 流式）。
Java 业务层通过 HTTP 调用本服务（Java 负责鉴权与数据落库）。

启动：
    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000
接口文档：http://localhost:8000/docs

环境变量（可选，均有默认值）：
    MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD / MYSQL_DB
    DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL
"""
import json
import os

import numpy as np
import pymysql
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="绿碳智衡算法服务", version="1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_methods=["*"], allow_headers=["*"],
)

# ---------------- 配置 ----------------
# HuggingFace 国内镜像 + 下载超时（避免模型下载挂起，国内访问 huggingface.co 经常超时）
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
os.environ.setdefault("HF_HUB_DOWNLOAD_TIMEOUT", "60")
os.environ.setdefault("HF_HUB_ETAG_TIMEOUT", "30")


def _load_env_file(path=".env"):
    """加载 algo 目录下的 .env 文件（key=value 每行）；已存在的环境变量优先"""
    try:
        env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), path)
        with open(env_path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                os.environ.setdefault(k.strip(), v.strip())
    except FileNotFoundError:
        pass


_load_env_file()

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/")

# 情景仿真基准参数（与数据库预设情景一致：基准情景）
BASE_COAL = 58.0      # 基准煤炭占比 %
BASE_IND = 42.0       # 基准工业占 GDP 比重 %


def db_config() -> dict:
    return dict(
        host=os.getenv("MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", "root"),
        database=os.getenv("MYSQL_DB", "smart_carbon"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


# ---------------- 模型懒加载 ----------------
import threading
import time

_embedder_state = {"model": None, "started": False}


def _load_embedder_async():
    """后台线程加载 BGE 模型（约 100MB，走 hf-mirror 镜像）；失败置 False 不再重试"""
    try:
        from sentence_transformers import SentenceTransformer
        _embedder_state["model"] = SentenceTransformer("BAAI/bge-small-zh-v1.5")
        print("[algo] BGE 中文向量模型加载成功（语义检索已启用）")
    except Exception as e:
        print("[algo] BGE 模型加载失败（持续使用哈希向量检索）:", e)
        _embedder_state["model"] = False


def get_embedder(wait_seconds=8.0):
    """获取 BGE 模型：首次调用触发后台下载，最多等待 wait_seconds 秒；
    超时返回 None（调用方降级哈希向量检索，下载完成后自动升级）"""
    if _embedder_state["model"] is None:
        if not _embedder_state["started"]:
            _embedder_state["started"] = True
            threading.Thread(target=_load_embedder_async, daemon=True).start()
        deadline = time.time() + wait_seconds
        while _embedder_state["model"] is None and time.time() < deadline:
            time.sleep(0.2)
    return _embedder_state["model"] if _embedder_state["model"] is not False else None


def _hash_embed(texts):
    """字符 unigram+bigram 哈希向量（512 维），无外部依赖的检索兜底方案"""
    vecs = np.zeros((len(texts), 512), dtype=np.float32)
    for i, t in enumerate(texts):
        for ch in t:
            vecs[i, hash(ch) % 512] += 1.0
        for a, b in zip(t, t[1:]):
            vecs[i, hash(a + b) % 512] += 1.0
    norms = np.linalg.norm(vecs, axis=1, keepdims=True) + 1e-9
    return (vecs / norms).astype(np.float32)


# ---------------- SSE 工具 ----------------
def _sse(data: dict) -> str:
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


def _sse_done() -> str:
    return "data: [DONE]\n\n"


# ---------------- 请求模型 ----------------
class PredictReq(BaseModel):
    history: list          # [{"t": 1, "emission": 123.4}, ...] t 为时间序号（如月份，从 1 开始连续）
    future_steps: int = 120


class SimulateReq(BaseModel):
    base_year: int
    base_emission: float
    history: list = []         # [{"year": 2020, "emission": 123.4}, ...] 历史年度总量（增速校准用）
    coal_ratio: float          # 煤炭占能源消费比重 %（政策情景值）
    industry_ratio: float      # 工业占 GDP 比重 %（政策情景值）
    tech_efficiency: float     # 单位能耗年均下降率 %（1.5 为历史隐含水平）
    base_coal: float = 58.0    # 基准煤炭占比（默认全国均值；省级化时传该省实际值）
    base_ind: float = 42.0     # 基准工业占比（同上）
    gdp_growth: float = 5.0    # 无历史数据时的回退估算参数
    years: int = 6
    mc_iters: int = 300        # 蒙特卡洛抽样次数


class AnomalyReq(BaseModel):
    series: list           # [{"key": "region-industry-energy", "points": [{"year":2021,"month":1,"value":123.4}, ...]}, ...]


class EmbedReq(BaseModel):
    texts: list


class ChatReq(BaseModel):
    question: str
    history: list = []     # [{"role": "user"/"assistant", "content": "..."}]


class ReportReq(BaseModel):
    summary: dict          # 数据摘要 JSON（区域/时期/指标/结构/预警）


# ============================================================
# 1. 趋势预测（LSTM + 蒙特卡洛置信区间）
# ============================================================
def _lstm_forecast(values, n_future):
    """LSTM 直接多步预测：输入最近 seq_len 个月，一次前向输出未来 n_future 个月。
    与自回归滚动外推相比无误差累积，长程输出稳定；数据过短或 torch 不可用时抛异常由调用方降级。"""
    import torch
    import torch.nn as nn

    v = np.asarray(values, dtype=np.float32)
    seq_len = min(12, len(v) - n_future)
    if len(v) < seq_len + n_future + 6:
        raise ValueError("训练样本不足")

    # 训练样本：过去 seq_len 个月 → 未来 n_future 个月（有监督，无需滚动）
    X, y = [], []
    for i in range(len(v) - seq_len - n_future + 1):
        X.append(v[i:i + seq_len])
        y.append(v[i + seq_len:i + seq_len + n_future])
    mean = float(np.mean(v))
    std = float(np.std(v)) + 1e-6
    Xt = torch.tensor(np.asarray(X), dtype=torch.float32).unsqueeze(-1)
    Xt = (Xt - mean) / std
    yt = (torch.tensor(np.asarray(y), dtype=torch.float32) - mean) / std

    class LSTM(nn.Module):
        def __init__(self, hidden=32):
            super().__init__()
            self.lstm = nn.LSTM(1, hidden, batch_first=True)
            self.fc = nn.Linear(hidden, n_future)

        def forward(self, x):
            out, _ = self.lstm(x)
            return self.fc(out[:, -1, :])

    torch.manual_seed(42)
    model = LSTM()
    opt = torch.optim.Adam(model.parameters(), lr=0.01)
    loss_fn = nn.MSELoss()
    for _ in range(300):
        opt.zero_grad()
        loss = loss_fn(model(Xt), yt)
        loss.backward()
        opt.step()

    model.eval()
    with torch.no_grad():
        last = torch.tensor((v[-seq_len:] - mean) / std, dtype=torch.float32).view(1, seq_len, 1)
        pred = model(last)[0].numpy()
    return pred * std + mean


def _linear_forecast(values, n_future):
    from sklearn.linear_model import LinearRegression
    x = np.arange(len(values), dtype=np.float64).reshape(-1, 1)
    y = np.asarray(values, dtype=np.float64)
    model = LinearRegression().fit(x, y)
    xf = np.arange(len(values), len(values) + n_future, dtype=np.float64).reshape(-1, 1)
    return model.predict(xf)


def _trend_season_forecast(values, n_future):
    """季节调整的趋势回归（Ridge）：线性 + 二次趋势 + 12 个月季节哑变量。
    对"趋势 + 季节波动"型数据外推稳定，作为 LSTM 退化时的降级模型。"""
    from sklearn.linear_model import Ridge
    T = len(values)
    t = np.arange(1, T + 1, dtype=np.float64)
    X = np.zeros((T, 14))
    X[:, 0] = t
    X[:, 1] = t * t / 1000.0
    for i in range(T):
        X[i, 2 + (i % 12)] = 1.0
    model = Ridge(alpha=5.0).fit(X, values)
    tf = np.arange(T + 1, T + n_future + 1, dtype=np.float64)
    Xf = np.zeros((n_future, 14))
    Xf[:, 0] = tf
    Xf[:, 1] = tf * tf / 1000.0
    for i in range(n_future):
        Xf[i, 2 + ((T + i) % 12)] = 1.0
    pred = model.predict(Xf)
    return np.maximum(pred, 0.0)


@app.post("/api/alg/predict")
def predict(req: PredictReq):
    series = sorted(req.history, key=lambda h: h["t"])
    values = np.asarray([float(h["emission"]) for h in series], dtype=np.float64)
    if len(values) < 3:
        return {"code": 400, "msg": "历史数据不足，至少需要 3 个数据点"}
    n = max(1, req.future_steps)

    method = "lstm"
    LSTM_STEPS = 24  # LSTM 仅做短期预测；长程外推由趋势模型衔接（自回归滚动长程会误差累积衰减）
    try:
        fc_lstm = _lstm_forecast(values, min(n, LSTM_STEPS))
        hist_mean = float(np.mean(values))
        # 退化检测：负值、零值尾段或末值偏离历史均值过大 → 弃用
        if (fc_lstm is None or np.any(fc_lstm < 0)
                or fc_lstm[-1] < hist_mean * 0.05 or fc_lstm[-1] > hist_mean * 3):
            raise ValueError("LSTM 输出退化")
        if n > LSTM_STEPS:
            # 混合预测：LSTM 前 24 步 + 趋势模型月度增量从 LSTM 末值平滑衔接
            fc_trend = _trend_season_forecast(values, n)
            tail = fc_lstm[-1] + np.cumsum(np.diff(fc_trend[LSTM_STEPS - 1:]))
            fc = np.concatenate([fc_lstm, tail])
        else:
            fc = fc_lstm
    except Exception as e:
        print("[algo] LSTM 不可用，降级季节趋势模型:", e)
        fc, method = _trend_season_forecast(values, n), "trend"

    # 蒙特卡洛：以同比月度差分标准差为波动尺度（对季节数据更稳定），路径非负截断
    if len(values) > 12:
        sigma = float(np.std(values[12:] - values[:-12]))
    else:
        sigma = float(np.std(values))
    sigma = max(sigma, float(np.mean(values)) * 0.005)
    rng = np.random.default_rng(42)
    paths = fc + np.cumsum(rng.normal(0, sigma, (300, n)), axis=1)
    paths = np.maximum(paths, 0)
    lower = np.percentile(paths, 2.5, axis=0)
    upper = np.percentile(paths, 97.5, axis=0)

    last_t = series[-1]["t"]
    ts = [last_t + i for i in range(1, n + 1)]
    peak_idx = int(np.argmax(fc))
    peaked = peak_idx < n - 1  # 峰值出现在未来（非最后一步）才视为达峰
    return {"code": 200, "data": {
        "t": ts,
        "values": [round(float(v), 2) for v in fc],
        "lower": [round(float(v), 2) for v in lower],
        "upper": [round(float(v), 2) for v in upper],
        "peak_t": ts[peak_idx] if peaked else None,
        "peak_value": round(float(fc[peak_idx]), 2) if peaked else None,
        "method": method,
    }}


# ============================================================
# 2. 情景仿真（历史增速校准 + 政策修正模型 + 蒙特卡洛）
#   基准增速从历史年度数据校准（近 3 年几何平均）；
#   政策参数（煤炭占比/工业占比/能效下降率）作为相对基准的修正项，
#   基准情景（58/42/1.5）下曲线与历史趋势外推一致，调整参数才产生偏离。
# ============================================================
BASE_TECH = 1.5  # 历史隐含的单位能耗年均下降率（%）


def _calibrate_growth(history, fallback_gdp):
    """历史增速校准：近 3 年几何平均年增速；数据不足回退参数估算"""
    vals = sorted([float(h["emission"]) for h in history])
    if len(vals) >= 4 and vals[-4] > 0:
        return (vals[-1] / vals[-4]) ** (1.0 / 3.0) - 1.0
    if len(vals) >= 2 and vals[0] > 0:
        return (vals[-1] / vals[0]) ** (1.0 / (len(vals) - 1)) - 1.0
    return (fallback_gdp - BASE_TECH) / 100.0


def _trajectory(base_emission, years, hist_growth, coal, ind, tech, base_coal, base_ind):
    """历史增速校准 + 政策修正（修正基准可省级化）：
    - 结构性调整（煤炭/工业占比）→ 排放水平的一次性修正，按 3 年渐进生效（幂弹性 0.5）
    - 能效下降率 → 对增速的持续性修正（弹性 2.0，1.5 为历史隐含水平）
    - 情景参数 = 基准参数（各省实际值）时所有修正为 1，曲线即该省历史趋势外推"""
    coal_level = (coal / base_coal) ** 0.5 if coal > 0 and base_coal > 0 else 0.0
    ind_level = (ind / base_ind) ** 0.5 if ind > 0 and base_ind > 0 else 0.0
    level_effect = coal_level * ind_level          # 一次性结构水平修正（基准=1）
    tech_effect = 1.0 - (tech - BASE_TECH) / 100.0 * 2.0   # 持续性能效修正（基准=1）
    growth = max((1.0 + hist_growth) * tech_effect, 0.90)
    out, e = [], base_emission
    for i, _ in enumerate(years):
        # 结构修正分 3 年渐进生效（避免"当年骤变"，达峰年份更自然）
        adj = level_effect ** (1.0 / 3.0) if i < 3 else 1.0
        e *= growth * adj
        out.append(e)
    return np.asarray(out)


@app.post("/api/alg/simulate")
def simulate(req: SimulateReq):
    years = [req.base_year + i for i in range(1, req.years + 1)]
    mc = min(max(req.mc_iters, 100), 1000)
    rng = np.random.default_rng(42)

    hist_growth = _calibrate_growth(req.history or [], req.gdp_growth)

    central = _trajectory(req.base_emission, years, hist_growth,
                          req.coal_ratio, req.industry_ratio, req.tech_efficiency,
                          req.base_coal, req.base_ind)
    paths = np.zeros((mc, len(years)))
    for i in range(mc):
        coal = min(max(rng.normal(req.coal_ratio, 0.5), 10.0), 90.0)
        ind = min(max(rng.normal(req.industry_ratio, 0.4), 10.0), 60.0)
        tech = max(rng.normal(req.tech_efficiency, 0.4), 0.0)
        growth = max(rng.normal(hist_growth, 0.01), 0.0)   # 增速不确定度 ±1%
        paths[i] = _trajectory(req.base_emission, years, growth, coal, ind, tech,
                               req.base_coal, req.base_ind)

    lower = np.percentile(paths, 2.5, axis=0)
    upper = np.percentile(paths, 97.5, axis=0)
    peak_idx = int(np.argmax(central))
    peaked = peak_idx < len(years) - 1
    return {"code": 200, "data": {
        "years": years,
        "values": [round(float(v), 2) for v in central],
        "lower": [round(float(v), 2) for v in lower],
        "upper": [round(float(v), 2) for v in upper],
        "peak_year": years[peak_idx] if peaked else None,
        "peak_value": round(float(central[peak_idx]), 2) if peaked else None,
    }}


# ============================================================
# 3. 孤立森林异常检测
# ============================================================
@app.post("/api/alg/anomaly")
def anomaly(req: AnomalyReq):
    """批量孤立森林检测：对每个序列独立建模，返回全部异常点（按分数降序）
    性能：使用轻量参数（50 棵树），建议调用方按「行业×能源」等共同规律分组，
    避免上千次独立建模；异常点会附带 points 中的全部原始字段（如 regionId）。
    分数：所有检出点【全局】min-max 归一化（0~1，越接近 1 越异常）——
    全局最高分唯一，各组间可比，避免"每组第一名恒为 1.0"的失真。"""
    from sklearn.ensemble import IsolationForest
    from sklearn.preprocessing import StandardScaler

    results = []
    for s in req.series:
        pts = s.get("points") or []
        if len(pts) < 10:
            continue
        X = np.asarray([[p["month"], p["value"]] for p in pts], dtype=np.float64)
        Xs = StandardScaler().fit_transform(X)
        model = IsolationForest(n_estimators=50, max_samples=64,
                                contamination=0.05, random_state=42)
        pred = model.fit_predict(Xs)
        raw = model.decision_function(Xs)  # 越负越异常
        for i, p in enumerate(pts):
            if pred[i] == -1:
                results.append({
                    **p, "key": s.get("key", ""),
                    "raw": float(raw[i]),   # 原始异常度，稍后全局归一
                })
    # 全局归一化：所有检出点统一映射到 0~1（唯一最高分）
    if results:
        raws = [r["raw"] for r in results]
        lo, hi = min(raws), max(raws)
        span = (hi - lo) if hi > lo else 1e-9
        for r in results:
            r["score"] = round((hi - r["raw"]) / span, 4)
            r.pop("raw", None)
    results.sort(key=lambda r: -r["score"])
    return {"code": 200, "data": results}


# ============================================================
# 4. 文本向量化
# ============================================================
@app.post("/api/alg/embed")
def embed(req: EmbedReq):
    model = get_embedder()
    if model is None:
        return {"code": 200, "data": _hash_embed(req.texts).tolist(), "method": "hash"}
    vecs = model.encode(req.texts, normalize_embeddings=True)
    return {"code": 200, "data": [v.tolist() for v in vecs], "method": "bge"}


# ============================================================
# 5. RAG 知识库问答（SSE 流式）
# ============================================================
# 缓存带 30 秒 TTL（新文档上传后 30 秒内自动生效），
# 也可由后端调用 /api/alg/kb/refresh 立即失效
_chunks_cache = {"data": None, "ts": 0.0}
CACHE_TTL_SECONDS = 30.0


@app.post("/api/alg/kb/refresh")
def kb_refresh():
    """知识库缓存刷新（后端在文档上传/停用/删除后调用）"""
    _chunks_cache["data"] = None
    print("[algo] 知识库缓存已刷新")
    return {"code": 200, "msg": "ok"}


def load_chunks(force: bool = False) -> list:
    """从 MySQL 加载启用状态知识库分块；缺向量的分块按需向量化并回写"""
    stale = time.time() - _chunks_cache["ts"] > CACHE_TTL_SECONDS
    if _chunks_cache["data"] is not None and not force and not stale:
        return _chunks_cache["data"]

    conn = pymysql.connect(**db_config())
    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT c.id, c.doc_id, c.chunk_index, c.content, c.vector, d.doc_name
                FROM kb_chunk c JOIN kb_document d ON c.doc_id = d.id
                WHERE d.status = 1
            """)
            rows = cur.fetchall()
    finally:
        conn.close()

    need = [r for r in rows if not r["vector"]]
    if need:
        try:
            model = get_embedder()
            if model is not None:
                vecs = model.encode([r["content"] for r in need], normalize_embeddings=True)
                conn = pymysql.connect(**db_config())
                try:
                    with conn.cursor() as cur:
                        for r, v in zip(need, vecs):
                            cur.execute("UPDATE kb_chunk SET vector=%s WHERE id=%s",
                                        (json.dumps(v.tolist()), r["id"]))
                    conn.commit()
                finally:
                    conn.close()
                for r, v in zip(need, vecs):
                    r["vector"] = json.dumps(v.tolist())
        except Exception as e:
            print("[algo] 分块向量化失败:", e)

    data = []
    for r in rows:
        # 无向量的分块也保留（哈希检索直接使用 content；BGE 检索时按需过滤）
        item = {
            "id": r["id"], "doc_name": r["doc_name"],
            "chunk_index": r["chunk_index"], "content": r["content"],
        }
        if r["vector"]:
            try:
                item["vec"] = np.asarray(json.loads(r["vector"]), dtype=np.float32)
            except Exception:
                pass
        data.append(item)
    _chunks_cache["data"] = data
    _chunks_cache["ts"] = time.time()
    return data


FALLBACK_FAQ = [
    ("碳达峰", "碳达峰是指区域二氧化碳排放量达到历史最高值后进入平台期并开始下降的拐点。我国承诺2030年前实现碳达峰。"),
    ("碳中和", "碳中和是指通过节能减排、植树造林、碳捕集利用与封存等方式，抵消自身产生的二氧化碳排放，实现净零排放。"),
    ("碳强度", "碳强度即单位GDP的二氧化碳排放量（tCO2/万元），是衡量经济发展与碳排放脱钩程度的核心指标。"),
    ("碳交易", "碳排放权交易是以市场机制控制温室气体排放的政策工具，纳入企业获得配额，富余配额可出售、不足需购买。"),
    ("碳核算", "碳排放核算一般依据《省级温室气体清单编制指南》，采用排放量=活动数据×排放因子×氧化率的公式计算。"),
]


def fallback_answer(question: str) -> str:
    for kw, ans in FALLBACK_FAQ:
        if kw in question:
            return f"（离线兜底模式：AI 服务暂不可用）{ans}"
    return "（离线兜底模式：AI 服务暂不可用）当前无法回答该问题，请稍后再试。"


SYSTEM_PROMPT = (
    "你是'绿碳智衡'区域碳排放监测与仿真决策平台的 AI 碳管家，"
    "依据给定的政策知识片段回答用户问题。要求：\n"
    "1. 仅依据片段内容作答，不得编造事实；\n"
    "2. 回答末尾标注引用来源（格式：【来源：《文件名》分块N】）；\n"
    "3. 若片段无法回答，直接说明'知识库中暂未收录该内容'；\n"
    "4. 语言简洁专业，适合政府工作人员阅读。"
)


@app.post("/api/alg/rag-chat")
def rag_chat(req: ChatReq):
    return StreamingResponse(
        _chat_stream(req),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


def _chat_stream(req: ChatReq):
    question = (req.question or "").strip()
    if not question:
        yield _sse({"content": "请输入问题"})
        yield _sse_done()
        return
    if not DEEPSEEK_API_KEY:
        yield _sse({"content": fallback_answer(question), "sources": []})
        yield _sse_done()
        return

    # ---- 检索：问题向量 × 知识库余弦相似度，Top-3 且相似度 ≥ 0.3 ----
    # BGE 模型可用时用库内向量；不可用时现场计算哈希向量（保证向量空间一致）
    chunks, hits = [], []
    print("[algo] 开始知识库检索…")
    try:
        model = get_embedder()
        print("[algo] 检索模式:", "bge" if model is not None else "hash")
        chunks = load_chunks()
        if chunks:
            if model is not None:
                usable = [c for c in chunks if "vec" in c]
                if usable:
                    qv = model.encode([question], normalize_embeddings=True)[0]
                    M = np.stack([c["vec"] for c in usable])
                    sims = M @ qv
                    top = np.argsort(-sims)[:3]
                    hits = [(usable[int(i)], float(sims[i])) for i in top if sims[i] >= 0.3]
            else:
                qv = _hash_embed([question])[0]
                M = _hash_embed([c["content"] for c in chunks])
                sims = M @ qv
                top = np.argsort(-sims)[:3]
                hits = [(chunks[int(i)], float(sims[i])) for i in top if sims[i] >= 0.3]
    except Exception as e:
        print("[algo] 知识库检索失败:", e)
    print(f"[algo] 检索命中 {len(hits)} 个片段，开始调用 DeepSeek…")

    context_parts, sources = [], []
    for j, (c, sim) in enumerate(hits, start=1):
        sources.append({"doc_name": c["doc_name"], "chunk_index": c["chunk_index"],
                        "similarity": round(sim, 4)})
        context_parts.append(f"[{j}]《{c['doc_name']}》分块{c['chunk_index']}：{c['content'][:600]}")
    context = "\n".join(context_parts)

    if context:
        user_msg = f"【政策知识片段】\n{context}\n\n【用户问题】\n{question}"
    else:
        # 无检索命中：明确要求告知未收录，防止模型编造引用来源
        user_msg = (f"（本次未在知识库中检索到相关内容。请直接告知用户"
                    f"「知识库中暂未收录该问题的相关材料」，不要编造任何引用来源。）\n\n"
                    f"【用户问题】\n{question}")
    messages = ([{"role": "system", "content": SYSTEM_PROMPT}]
                + list(req.history[-8:])
                + [{"role": "user", "content": user_msg}])

    # 首个 SSE 事件携带引用来源
    yield _sse({"sources": sources})
    try:
        import httpx
        with httpx.Client(timeout=60) as client:
            with client.stream(
                "POST", f"{DEEPSEEK_BASE_URL}/chat/completions",
                headers={"Authorization": f"Bearer {DEEPSEEK_API_KEY}"},
                json={"model": "deepseek-chat", "messages": messages,
                      "stream": True, "temperature": 0.3},
            ) as resp:
                if resp.status_code != 200:
                    detail = resp.text[:200]
                    print("[algo] DeepSeek 返回非 200:", resp.status_code, detail)
                    yield _sse({"content": f"（AI 服务调用失败：HTTP {resp.status_code}，{detail}）"})
                else:
                    for line in resp.iter_lines():
                        if not line or not line.startswith("data:"):
                            continue
                        payload = line[5:].strip()
                        if payload == "[DONE]":
                            break
                        delta = json.loads(payload)["choices"][0]["delta"].get("content", "")
                        if delta:
                            yield _sse({"content": delta})
    except Exception as e:
        print("[algo] DeepSeek 调用失败:", e)
        yield _sse({"content": f"（AI 服务调用异常：{type(e).__name__}）{fallback_answer(question)}"})
    yield _sse_done()


# ============================================================
# 6. AIGC 监测报告生成（DeepSeek，非流式）
# ============================================================
REPORT_SYSTEM_PROMPT = (
    "你是'绿碳智衡'区域碳排放监测平台的碳排放分析专家，请根据给定的数据摘要撰写专业监测报告。要求：\n"
    "1. 使用 Markdown 格式输出（# 一级标题、## 小节标题、- 要点列表）；\n"
    "2. 章节结构：一、总体情况 二、行业结构分析 三、能源结构分析 四、预警动态 五、结论与建议；\n"
    "3. 严格基于摘要数据解读分析，不得编造摘要以外的数据或出处；\n"
    "4. 结论与建议需给出 2-3 条可落地的减排建议；\n"
    "5. 语言精炼专业，全文 600-900 字，适合呈报政府部门。"
)


@app.post("/api/alg/report")
def generate_report(req: ReportReq):
    if not DEEPSEEK_API_KEY:
        return {"code": 400, "msg": "AI 服务未配置（缺少 DEEPSEEK_API_KEY）"}
    import httpx
    summary_text = json.dumps(req.summary, ensure_ascii=False, indent=1)
    try:
        with httpx.Client(timeout=120) as client:
            resp = client.post(
                f"{DEEPSEEK_BASE_URL}/chat/completions",
                headers={"Authorization": f"Bearer {DEEPSEEK_API_KEY}"},
                json={"model": "deepseek-chat",
                      "messages": [
                          {"role": "system", "content": REPORT_SYSTEM_PROMPT},
                          {"role": "user", "content": f"【数据摘要】\n{summary_text}"},
                      ],
                      "stream": False, "temperature": 0.4},
            )
            if resp.status_code != 200:
                return {"code": 500, "msg": f"AI 调用失败（HTTP {resp.status_code}）"}
            content = resp.json()["choices"][0]["message"]["content"]
            return {"code": 200, "data": {"content": content}}
    except Exception as e:
        print("[algo] 报告生成失败:", e)
        return {"code": 500, "msg": f"AI 调用异常: {type(e).__name__}"}


# ---------------- 健康检查 ----------------
@app.get("/health")
def health():
    return {"code": 200, "msg": "ok", "service": "algo",
            "api_key_configured": bool(DEEPSEEK_API_KEY)}
