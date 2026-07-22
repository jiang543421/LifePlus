# 技术架构设计 · AI 分析 v2.0

> 版本：v0.1 · 日期：2026-07-22 · 模块：`ai`（v2.0）
> 输入：[ai-v2-design.md §5-§7](../superpowers/specs/2026-07-21-ai-v2-design.md) · [01-architecture](../specs/01-architecture.md)
> 索引：[ai-business-architecture](./ai-business-architecture.md) · [ai-data-model](./ai-data-model.md)

---

## 1. 系统分层架构

| 层 | 包路径 | 主要类 | 职责 |
|---|---|---|---|
| Web | `com.lifepulse.ai.web` | `AiInsightController` | 单端点 + `@PreAuthorize` + 限流 |
| Service | `com.lifepulse.ai.service` | `AiInsightService` | 编排 / 缓存 / 降级 |
| Provider | `com.lifepulse.ai.provider` | `MetricProvider` (interface) + 5 impls | 各自领域单指标采集 |
| Engine | `com.lifepulse.ai.service` | `AiTemplateEngine` | 模板加载 + 渲染 |
| DTO | `com.lifepulse.ai.web.dto` | `AiInsightResponse`, `AiChipDto` | 响应序列化 |
| Domain | `com.lifepulse.ai.model` | `AiInsightPayload`, `MetricValue`, `Trend` | 内部值对象 |
| Config | `com.lifepulse.ai.config` | `AiInsightProperties` | `@ConfigurationProperties` |

调用链：`Controller → Service → (Provider[] ∥ Engine) → Redis`

约束：Service 不依赖 Controller；Provider 不引用 Service；Engine 不引用任何业务类（仅 `AiConstants`）。

---

## 2. 关键类签名

```java
public interface MetricProvider {
    String key();                       // "taskCompletion" | "weeklyExpense" | ...
    default boolean enabled() { return true; }
    MetricValue collect(AiCollectContext ctx);  // 异常 → MetricValue.none()
}

public record AiCollectContext(long userId, LocalDate today, LocalDate weekStart) {}

public record MetricValue(BigDecimal value, Trend trend, Object deltaRaw) {
    public static MetricValue none() { return new MetricValue(null, Trend.NONE, null); }
}

public enum Trend { UP, DOWN, FLAT, NONE }

@Service
public class AiInsightService {
    public AiInsightResponse getInsight(long userId);
}

@Component
public class AiTemplateEngine {
    public String formatHeadline(String key, Object... args);
    public String formatChipDelta(String chipKey, String trend, Object value);
}
```

---

## 3. 存储方案

### 3.1 数据库

- **零新增表**（设计原则 §4.1）。AI 模块只读 4-5 张已存在业务表
- 不修改 `t_task` / `t_plan` / `t_expense` / `t_diet` / `t_daily_report`
- Flyway 无新迁移
- 全部读路径按 `user_id` 过滤（继承各模块 CLAUDE.md §7.2）

### 3.2 Redis 缓存结构

```
键:    lp:ai:insight:<userId>
类型:  String (JSON)
TTL:   1800 s (30 min)
值:    AiInsightResponse 序列化 JSON
失败:  读失败 → 跳过缓存继续算；写失败 → 仅 WARN 不抛
```

仅 1 个键；不存历史快照；不预热；TTL 到期下次请求重算。

---

## 4. 安全架构

### 4.1 鉴权

- 继承 MVP1 `JwtAuthFilter`：解析 `Authorization: Bearer <access>` → 写入 `UserContext.current()`
- Controller 无需手动鉴权；由 `SecurityConfig` 配置 `/api/v1/ai/**` → `authenticated()`
- Provider 内部所有 Mapper 调用必须传 `UserContext.current()` 取出的 `userId`，**禁止从请求参数取**

### 4.2 限流

- 复用 `common.security.RateLimiter`（Redis 计数）
- Key: `lp:rl:ai:<userId>`
- 阈值：**30 次 / 分钟 / 用户**（与 home page 同步刷新节奏匹配）
- 命中 → `BusinessException(1006)` → HTTP 429（由 `GlobalExceptionHandler` 转统一信封）
- 无 IP 维度限流（端点只对已登录用户开放）

### 4.3 输入输出

- 入参：仅 `userId`（来自 JWT），**不接受 query 参数**
- 出参：所有 chip 数字 `BigDecimal` 序列化；不在响应中暴露 userId / email
- 日志：Provider 失败日志仅含 `userId`，不打印原始数值

### 4.4 错误信封

| code | 含义 | 触发 |
|---|---|---|
| 1003 | 跨用户越权 | 当前端点不接受 `{id}`，无路径触发 |
| 1006 | 操作过于频繁 | RateLimiter 命中 |
| 1500 | 系统异常 | Service 顶层未捕获（实际不应触发） |

---

## 5. 部署架构

AI 模块**不引入新进程 / 新镜像**，与现有后端共用 fat jar：

| 资产 | 位置 | 备注 |
|---|---|---|
| 后端 fat jar | `backend/target/lifepulse-backend-*.jar` | 包含 `com.lifepulse.ai.*` |
| 前端构建 | `frontend/dist/` | 复用现有 nginx 镜像 |
| docker-compose | 复用 `docker-compose.yml` | 无 service 新增 |
| 启动 banner | `LifePulseApplication.java` 继承 | 显示 `git commit-hash` + `build time` |

环境变量（沿用 CLAUDE.md §7.1，全部从 `LP_*` 注入）：
- `LP_AI_DAILY_ENABLED` → `lp.ai.daily-enabled`（默认 `false`）
- 其余 provider 开关来自 `application.yml` 默认 `true`，无需环境变量

依赖项：无新 Maven 依赖（MessageFormat / Properties / Spring Cache 均为 JDK / Spring 内置）。
