### Task 9.7b: PR 7 提 PR

PR 标题：`feat(ai): add AiInsightDrawer + HomeView integration`

PR 描述：
```
## 改了什么
- AiInsightDrawer.vue: 详情抽屉（chip 列表 + 时间戳 + 刷新按钮）
- HomeView.vue: 在 6 卡网格前插入 AiInsightCard（跨满整行）
- 4 + 1 = 5 个 Vitest 用例全绿

## 测试覆盖
- AiInsightDrawer: 4 用例（开/关/详情/刷新）
- HomeView: 1 用例（卡片渲染）

## 影响面
- 修改 HomeView 既有文件
- 无新依赖
```

合并到 `feat/ai-v2.0`，进入 PR 8（E2E）。

---
