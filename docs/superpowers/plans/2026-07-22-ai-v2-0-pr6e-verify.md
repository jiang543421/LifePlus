### Task 9.5b: PR 6 提 PR

PR 标题：`feat(ai): add frontend foundation (types/api/store/AiChipItem/AiInsightCard)`

PR 描述：
```
## 改了什么
- frontend/src/types/ai.ts: TS 类型与 chip 常量
- frontend/src/api/ai.ts: getTodayInsight / refreshInsight
- frontend/src/stores/ai.ts: Pinia store（带 30s 防抖 + error 状态）
- AiChipItem.vue: 单 chip 显示（含空态/趋势色）
- AiInsightCard.vue: 卡片视图（loading/error/loaded + 刷新）

## 测试覆盖
- aiStore: 4 用例（load/refresh/error/防抖）
- AiChipItem: 4 用例
- AiInsightCard: 4 用例
- 总计 12 用例，全绿

## 影响面
- 仅新增文件
- 无新依赖
```

合并到 `feat/ai-v2.0`，进入 PR 7。

---
