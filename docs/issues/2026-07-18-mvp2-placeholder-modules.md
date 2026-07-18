# [Phase 2+] 日报 / 消费 / 饮食 / AI 分析四个占位卡落地

**描述**：首页 6 卡中 4 个（日报/消费/饮食/AI 分析）当前点击弹「即将上线」。MVP2 候选范围，需要先做用户调研/优先级/数据模型设计，再分 Phase 增量实现。

**Acceptance Criteria**：
- [ ] 新增 docs/specs/06-mvp2-modules-scoping.md：4 卡片各写 1 段「用户故事 + 主要字段 + 数据源 + 风险」
- [ ] 与各方确认后选 1-2 个先做；剩余继续占位（保留「即将上线」即可）
- [ ] 选中的模块进入 Phase 2.x 计划（task / plan / scan / AI 等子模块分立）
- [ ] 后端按子模块新增表 + Flyway migration + Service/Controller/IT；前端新增 view/store/types
- [ ] 100% 单测 + 关键流 E2E；vue-tsc 0 error
- [ ] 首页卡 `placeholder: false`，直接路由跳到对应模块入口

**Refs**：RELEASES/v1.0.0-mvp.md §1 / §5.1
