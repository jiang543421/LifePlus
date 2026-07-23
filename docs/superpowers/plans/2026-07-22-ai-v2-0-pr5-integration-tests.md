## PR 5 — 集成测试（Testcontainers）

涵盖 spec §13 T8.1 - T8.2。完成此 PR 后，关键闭环（缓存 hit/miss、daily 降级、cross-user 隔离）在真实 MySQL+Redis 上验证。

### Task 8.1: AiAnalysisIT 框架

**Files:**
- Create: `backend/src/test/java/com/lifepulse/ai/it/AiAnalysisIT.java`

- [ ] **Step 1: 创建 `AiAnalysisIT.java`**

```java
package com.lifepulse.ai.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifepulse.LifePulseApplication;
import com.lifepulse.ai.service.AiInsightService;
import com.lifepulse.ai.web.AiInsightController;
import com.lifepulse.auth.entity.User;
import com.lifepulse.auth.repository.UserMapper;
import com.lifepulse.security.UserContext;
import com.lifepulse.task.entity.Task;
import com.lifepulse.task.repository.TaskMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RedisContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = LifePulseApplication.class)
@Testcontainers
@TestPropertySource(properties = {
    "lp.ai.daily-enabled=true"  // 本 IT 启用 daily
})
class AiAnalysisIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8"))
        .withDatabaseName("lifepulse")
        .withUsername("test")
        .withPassword("test");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Autowired private WebApplicationContext wac;
    @Autowired private AiInsightService service;
    @Autowired private TaskMapper taskMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private RedisTemplate<String, ?> redisTemplate;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // 清理 Redis
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // === 测试用例见 Task 8.2 ===
}
```

> ⚠️ 此处 MySQL/Redis Testcontainers 配置需通过 `@DynamicPropertySource` 注入 spring.datasource.* 与 spring.data.redis.*。
> 简化：项目已有 `AbstractIntegrationTest` 基类，**优先复用**；本计划假设已存在 `AbstractIntegrationTest`，新 IT 继承之。

- [ ] **Step 2: 实际继承项目既有 IT 基类**

替换为：

```java
class AiAnalysisIT extends AbstractIntegrationTest {
    @Autowired private AiInsightService service;
    // ...
}
```

> 若无 `AbstractIntegrationTest`，先在 `it/AbstractIntegrationTest.java` 创建（参考项目既有 test 模式）。

- [ ] **Step 3: 编译验证**

```powershell
cd backend; mvn -q test-compile
```

预期：`BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/lifepulse/ai/it/
git commit -m "test(ai): add AiAnalysisIT base with Testcontainers"
```

---

### Task 8.2: 5 个集成用例

**Files:**
- Modify: `backend/src/test/java/com/lifepulse/ai/it/AiAnalysisIT.java`

- [ ] **Step 1: 追加 5 个用例**

```java
@Test
void endToEnd_firstCallMiss_secondCallHit() {
    UserContext.set(1L);
    // 准备：插入用户 + 任务数据
    var user = new User();
    user.setEmail("test1@example.com");
    user.setPasswordHash("hash");
    userMapper.insert(user);
    insertTask(user.getId(), "task1", "DONE");

    // 第一次：cache miss
    var first = service.getOrCompute(user.getId());
    assertThat(first.headline()).isNotEqualTo("还没有数据");

    // 第二次：cache hit
    var second = service.getOrCompute(user.getId());
    assertThat(second).isSameAs(first);  // 同一对象
}

@Test
void endToEnd_realDataWithSeededTasks_producesNonEmptyHeadline() {
    // 5 个任务，3 个完成 → 60% 完成率
    UserContext.set(2L);
    var user = createUser("u2@example.com");
    for (int i = 0; i < 5; i++) {
        insertTask(user.getId(), "task" + i, i < 3 ? "DONE" : "TODO");
    }

    var result = service.getOrCompute(user.getId());

    assertThat(result.headline()).contains("60%");
}

@Test
void endToEnd_realDataWithZeroTasks_producesEmptyHeadline() {
    UserContext.set(3L);
    createUser("u3@example.com");

    var result = service.getOrCompute(3L);

    assertThat(result.headline()).isEqualTo("还没有数据");
}

@Test
void endToEnd_dailyDisabled_doesNotQueryDailyTables() {
    // daily-enabled 默认 false；验证不抛 SQL 错误
    UserContext.set(4L);
    createUser("u4@example.com");

    var result = service.getOrCompute(4L);

    assertThat(result.headline()).isNotNull();
    // 不含 dailyStreak chip
}

@Test
void endToEnd_crossUserIsolation_userACannotSeeUserBInsight() {
    var userA = createUser("a@example.com");
    var userB = createUser("b@example.com");
    insertTask(userA.getId(), "a-task", "DONE");
    insertTask(userB.getId(), "b-task", "TODO");

    UserContext.set(userA.getId());
    var aResult = service.getOrCompute(userA.getId());

    UserContext.set(userB.getId());
    var bResult = service.getOrCompute(userB.getId());

    // 缓存键不同，结果应不同
    assertThat(aResult).isNotSameAs(bResult);
    assertThat(aResult.headline()).isNotEqualTo(bResult.headline());
}

// === helpers ===

private User createUser(String email) {
    var user = new User();
    user.setEmail(email);
    user.setPasswordHash("hash");
    userMapper.insert(user);
    return user;
}

private void insertTask(Long userId, String title, String status) {
    var task = new Task();
    task.setUserId(userId);
    task.setTitle(title);
    task.setStatus(com.lifepulse.task.enums.TaskStatus.valueOf(status));
    task.setDueAt(LocalDateTime.now());
    taskMapper.insert(task);
}
```

- [ ] **Step 2: 运行 IT**

```powershell
cd backend; mvn -q test -Dtest=AiAnalysisIT
```

预期：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`（首次跑会下载 mysql:8 + redis:7 镜像，约 1-2 分钟）。

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/lifepulse/ai/it/AiAnalysisIT.java
git commit -m "test(ai): add 5 integration tests with Testcontainers"
```

---

### Task 8.3: PR 5 提 PR

PR 标题：`test(ai): add AiAnalysisIT with Testcontainers (5 cases)`

PR 描述：
```
## 改了什么
- AiAnalysisIT 框架（Testcontainers MySQL + Redis）
- 5 个集成用例：缓存 hit/miss、零数据、daily 降级、cross-user 隔离

## 测试覆盖
- 5 用例全绿
- 真实 MySQL + Redis 闭环

## 影响面
- 仅新增测试文件
- 不影响生产代码
```

合并到 `feat/ai-v2.0`，进入 PR 6（前端）。

---
