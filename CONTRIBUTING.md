# Contributing to LifePulse

## 1. 行为准则

- **简洁优先**：方案能简单就别复杂；读代码 > 写代码。
- **测试先行**：所有新功能与缺陷修复按 TDD Red → Green → Refactor 落地。
- **跨用户隔离是底线**：任何 `{id}` 端点必须经 `UserContext.current()` 过滤，越权即 1003。

## 2. 分支策略

| 类型 | 命名 | 用途 |
|---|---|---|
| 主分支 | `main` | production-ready；只允许 PR merge |
| 功能 | `feat/<scope>-<short>` | 新功能 |
| 修复 | `fix/<scope>-<short>` | Bug 修复 |
| 重构 | `refactor/<scope>-<short>` | 不改行为的重构 |
| 文档 | `docs/<scope>-<short>` | 仅文档变更 |
| 测试 | `test/<scope>-<short>` | 仅测试补全 |
| 性能 | `perf/<scope>-<short>` | 性能优化 |
| 杂项 | `chore/<scope>-<short>` | build / ci / 工具链 |

`<scope>` 取模块名（`auth` / `task` / `plan` / `home` / `infra` / `frontend` / ...），全小写英文短横线分隔。

## 3. 提交消息（commit message）

格式：**`<type>(<scope>): <subject>`**

- subject 用**英文**，首字母不大写，不超过 72 字符，不加句号
- 允许的 type：`feat` / `fix` / `refactor` / `docs` / `test` / `chore` / `perf` / `ci`
- 正文可选；遇以下情况**必填**：
  - BREAKING CHANGE（开头写 `BREAKING CHANGE:` 引导段）
  - 解释"为什么"的非平凡修改
  - 关联 issue：`Refs: #123` / `Closes: #456`

示例：

```text
feat(auth): rotate refresh token on every refresh
fix(task): enforce user_id filter on get-by-id
test(plan): assert cross-user reject returns 1003
chore(infra): add docker compose healthchecks
docs(readme): document seed account and env vars
```

## 4. 开发流程

1. **拉分支**（从 `main`）：`git switch -c feat/<scope>-<short>`
2. **编码 + 测试**（TDD）：
   - 先写失败测试（RED）
   - 实现让其通过（GREEN）
   - 重构（IMPROVE）
   - 验证覆盖：后端 `mvn verify`（JaCoCo ≥ 80%），前端 `pnpm test`
3. **自审代码**：
   - 跑 `git diff main...HEAD` 过一遍变更
   - 确认未引入硬编码密钥、`.env`、大文件、二进制产物
4. **commit**：本地多次小步 commit；commit message 符合上面规范
5. **建 PR**：标题同 commit message；正文含改了什么 / 为什么 / 测试覆盖 / 影响面 / 是否 break change
6. **CI 绿 + 至少一个 reviewer 通过 → squash merge**

## 5. 禁止事项（红线）

- `git push --force` / `git reset --hard` 在共享分支
- `git rebase -i` 重写已 push 分支
- `--no-verify` 跳过钩子
- 任何 `.env` / 真实 token / 真实数据库密码提交入库
- 删测试来变绿
- `@Disabled` / `xit()` 跳过失败的测试而不留 issue

## 6. 测试要求摘要

- 后端 Service 行覆盖 **≥ 80%**（JaCoCo 在 `mvn verify` 强制闸门）
- 鉴权 / 校验 / 越权路径 100% 覆盖
- 后端集成测试**使用 Testcontainers**（不许依赖外部 DB / Redis）
- 前端 store 关键 getters 100%；组件关键交互 100%
- E2E 用 Playwright：登录流、任务流、日历流、跨用户 1003、refresh 重放 1401、登录限流 1006 必须存在

## 7. 风格速查

| 维度 | 规则 |
|---|---|
| Java 缩进 / 引号 | 4 空格 / 双引号 |
| TS 缩进 / 引号 | 2 空格 / 双引号 |
| 函数最大 | 50 行 |
| 文件最大 | 800 行 |
| 嵌套最大 | 4 层（优先早返） |
| 魔法数字 | 抽出命名常量 |
| Boolean 命名 | `is*` / `has*` / `should*` / `can*` |
| 不变性 | 不 mutate；集合返回不可变视图 |

完整规范见 `CLAUDE.md`。
