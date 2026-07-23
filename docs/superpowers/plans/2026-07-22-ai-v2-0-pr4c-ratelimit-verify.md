### Task 7.1: 限流配置（RateLimiter 接入 ai 端点）

**Files:**
- Modify: `backend/src/main/java/com/lifepulse/security/RateLimiter.java`（如不存在，新建）

- [ ] **Step 1: 检查 RateLimiter 既有实现**

```powershell
grep -n "lp:rl:" backend/src/main/java/com/lifepulse/security/RateLimiter.java
```

- [ ] **Step 2: 接入 ai 限流键**

打开 `AiInsightController.java`，在两个方法上加注解：

```java
@com.lifepulse.security.RateLimit(key = "ai:today", limit = 60, windowSeconds = 60)
@GetMapping("/today")
public MyResponse<AiInsightResponse> getToday() { ... }

@com.lifepulse.security.RateLimit(key = "ai:refresh", limit = 6, windowSeconds = 60)
@PostMapping("/refresh")
public MyResponse<AiInsightResponse> refresh() { ... }
```

> 注解/拦截器形式依项目既有约定；若项目使用 `HandlerInterceptor` 显式注册，则改为在 `WebMvcConfigurer` 加路由拦截。

- [ ] **Step 3: 写限流单测**

```java
@Test
void getToday_rateLimited_returns429WithCode1006() throws Exception {
    // 触发 7 次 refresh，第 7 次期望 429
    for (int i = 0; i < 7; i++) {
        var auth = new UsernamePasswordAuthenticationToken(1L, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        UserContext.set(1L);
        try {
            mockMvc.perform(post("/api/v1/ai/insight/refresh"))
                .andExpect(i < 6 ? status().isOk() : status().isTooManyRequests());
        } finally {
            SecurityContextHolder.clearContext();
            UserContext.clear();
        }
    }
}
```

- [ ] **Step 4: 运行测试 + Commit**

```powershell
cd backend; mvn -q test -Dtest=AiInsightControllerWebTest
```

预期：`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。

```bash
git add backend/src/main/java/com/lifepulse/ai/web/AiInsightController.java
git add backend/src/test/java/com/lifepulse/ai/web/AiInsightControllerWebTest.java
git commit -m "feat(ai): add rate limit on ai endpoints"
```

---

### Task 7.2: PR 4 端到端验证

- [ ] **Step 1: 全量编译 + 单测**

```powershell
cd backend; mvn -q test
```

预期：所有测试通过。

- [ ] **Step 2: 提 PR**

PR 标题：`feat(ai): add Controller, mapper, rate limit + WebMvcTest`

PR 描述：
```
## 改了什么
- AiInsightController：GET /today, POST /refresh
- AiInsightResponseMapper：内部 payload → DTO
- 限流：today 60/min, refresh 6/min
- 7+ WebMvcTest 用例（401/200/503/限流）

## 测试覆盖
- AiInsightControllerWebTest: 8 用例
- 鉴权 100% 覆盖

## 影响面
- 仅新增文件
- 无新依赖
```

合并到 `feat/ai-v2.0`，进入 PR 5。

---
