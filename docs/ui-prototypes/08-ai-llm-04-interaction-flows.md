## 4. 交互流程

### 4.1 状态机（智能卡 + 抽屉）

```
                   ┌─────────────┐
       mount ─────▶│   LOADING   │──── 200 ───▶ ┌─────────────┐
                   │ (skeleton)  │               │    READY    │
                   └──────┬──────┘               └──────┬──────┘
                          │ 5xx / 1501                │
                          ▼                           │ click ⓘ / 整卡
                   ┌─────────────┐                    ▼
                   │    ERROR    │            ┌─────────────┐
                   │ (fallback)  │            │  DRAWER     │
                   └──────┬──────┘            │  (open)     │
                          │ 自动重试 1 次     └──────┬──────┘
                          ▼                          │ click "查看完整分析 →"
                   ┌─────────────┐                  ▼
                   │    READY    │          ┌─────────────┐
                   └─────────────┘          │  ROUTE      │
                                             │ /ai-analysis│
                                             └─────────────┘
```

### 4.2 状态机（独立分析页）

```
                   ┌─────────────┐
       enter ─────▶│   LOADING   │──── 200 ───▶ ┌─────────────┐
                   │ (skeleton)  │              │    READY    │
                   └──────┬──────┘              └──────┬──────┘
                          │ 5xx / 1501                │
                          ▼                           │ click 刷新
                   ┌─────────────┐                    ▼
                   │    ERROR    │◀──────  失败 ── ┌─────────────┐
                   │ (fallback)  │                │  REFRESHING │
                   └──────┬──────┘                └─────────────┘
                          │
                          ▼
                       READY (auto retry 1x)
```

### 4.3 时序：用户进入首页 → 智能卡 → 抽屉 → 独立分析页

```
[1] 用户登录 → 进入 /home
        │
        ▼
[2] HomeView mounted
        │  ├─ 触发 AiInsightStore.load()
        ▼
[3] GET /api/v1/ai/insight/today  (Authorization: Bearer)
        │
        │  ├─ 后端：缓存命中 (lp:ai:insight:<userId>) → 直接返 (含 source)
        │  └─ 后端：缓存 MISS → 计算 → 写缓存 → 返
        ▼
[4] 响应到达 → 更新 aiInsight store
        │
        ▼
[5] 智能卡渲染 (READAY 态)
        │  ├─ headline + 3 chip + source 标签
        │  └─ [ⓘ] 按钮可点击
        ▼
[6] 用户点击 ⓘ / 整卡 → 抽屉打开
        │  ├─ AiDrawerVisible = true
        │  ├─ 复用 store.aiInsight (不重新请求)
        │  └─ 渲染 headline + 3 指标 + 来源说明 + 底部"查看完整分析 →"
        ▼
[7] 用户点击 "查看完整分析 →"
        │  ├─ router.push('/ai-analysis')
        │  └─ AiDrawerVisible = false (关闭抽屉)
        ▼
[8] AiAnalysisView mounted
        │  ├─ 检查 store.aiInsight 是否新鲜 (<6h)
        │  │  ├─ 是 → 直接用
        │  │  └─ 否 → 触发 GET /api/v1/ai/insight/analysis
        ▼
[9] 独立分析页渲染 (4 段内容)
        │  ├─ Headline 大字
        │  ├─ Advice 卡片 (LLM 才有)
        │  ├─ Highlight 卡片 (LLM 才有)
        │  ├─ 4 chip 关键指标
        │  └─ 底部 "v2.2 将加入..." 提示
        ▼
[10] 用户点击 ← 返回 → router.push('/home')
        │  └─ 智能卡仍可看到（缓存命中）
```

### 4.4 时序：用户点击智能卡刷新按钮

```
[1] 用户点击 [刷新]
        │
        ▼
[2] aiLoading = true (整卡 skeleton)
        │
        ▼
[3] POST /api/v1/ai/insight/refresh  (Authorization: Bearer)
        │  ├─ 后端：删缓存 + 重算
        │  │  ├─ 成功 → 200 + 新数据
        │  │  ├─ 1006 → 429 (限流，3/min/user)
        │  │  └─ 1501 → 503 (LLM + 模板双失败)
        ▼
[4] 响应处理
        │  ├─ 200 → 更新 aiInsight + 重渲染 (source 可能变化)
        │  ├─ 1006 → ElMessage.error('操作过于频繁，请稍后再试')
        │  └─ 5xx → ElMessage.error('数据异常，请稍后重试')
        ▼
[5] aiLoading = false
```

按钮在 `aiLoading=true` 时 `loading` 旋转 + disabled。

### 4.5 API 调用矩阵

| 用户动作 | 触发 API | 限流 | 错误处理 |
|---|---|---|---|
| 进入首页 | GET `/today` | 30/min/user (1006) | 5xx → fallback.headline + Toast |
| 智能卡刷新 | POST `/refresh` | **3/min/user** (1006) | 同上 |
| 点击 ⓘ 打开抽屉 | ❌ 无（复用 store）| — | — |
| 点击"查看完整分析 →" | router.push + GET `/analysis`（如缓存过期）| 30/min/user (1006) | 同上 |
| 独立分析页刷新 | POST `/refresh` | **3/min/user** (1006) | 同上 |
| 点击 ← 返回 | router.push('/home') | — | — |

> **`/analysis` 与 `/today` 共享缓存**（同一 Redis 键 `lp:ai:insight:<userId>`），独立分析页只在缓存过期时才发请求。

---
