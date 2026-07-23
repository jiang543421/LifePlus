# Phase 2 — Task module

> **Sub-specs to load at start of Phase 2:** `02-database §2.2`, `03-api-auth §5.3`, `04-frontend` § for TaskListView / stores/task / api/task.

## Task T-T01: Flyway V2 — `t_task`

- [ ] **Step 1: Write `V2__init_task.sql`** with columns + `idx_user_status_due` + `idx_user_plan` per `02-database §3`.
- [ ] **Step 2: `mvn -q -B -DskipTests flyway:info`; commit** `feat(db): t_task`

---

## Task T-T02: `Task` entity + `TaskMapper`

- [ ] **Step 1: IT** insert + find by id + page by user + soft delete.
- [ ] **Step 2: Implement** `Task` with `TaskStatus` / `TaskPriority` enums; mapper methods `pageByUser`, `findByUserAndId`, `updateStatus`, `pageByPlan`.
- [ ] **Step 3: Run, commit** `feat(task): task entity and mapper`

---

## Task T-T03: `TaskService` CRUD + status + ownership check

- [ ] **Step 1: Unit tests** create sets userId, update rejects cross-user (1003), byPlan rejects other user, softDelete sets flag.
- [ ] **Step 2: Implement** reading userId from `UserContext.current()`; throw `BusinessException(1003)` on cross-user.
- [ ] **Step 3: Run, commit** `feat(task): task service`

---

## Task T-T04: `TaskController` 7 endpoints

**Files:** `backend/src/main/java/com/lifepulse/task/web/TaskController.java`, `.../dto/{TaskCreateReq,TaskUpdateReq,TaskStatusReq,TaskView}.java`, `.../common/web/PageResponse.java`

- [ ] **Step 1: Slice tests** for each endpoint (validation + auth + cross-user).
- [ ] **Step 2: Implement**
```java
@RestController @RequestMapping("/api/v1/tasks") @RequiredArgsConstructor
public class TaskController {
  private final TaskService svc;
  @GetMapping public ApiResponse<PageResponse<TaskView>> list(@RequestParam Map<String,String> q){...}
  @GetMapping("/{id}") public ApiResponse<TaskView> get(@PathVariable long id){...}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<Map<String,Long>> create(@Valid @RequestBody TaskCreateReq r){...}
  @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody TaskUpdateReq r){...}
  @PatchMapping("/{id}/status") public ApiResponse<Void> patchStatus(@PathVariable long id, @Valid @RequestBody TaskStatusReq r){...}
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id){...}
  @GetMapping("/by-plan/{planId}") public ApiResponse<List<TaskView>> byPlan(@PathVariable long planId){...}
}
```
- [ ] **Step 3: Run, commit** `feat(task): task controller`

---

## Task T-T05: Task integration test

`backend/src/test/java/com/lifepulse/task/it/TaskIT.java` (Testcontainers): register/login → create/list → patch status → soft delete → cross-user 1003.

- [ ] **Step 1: Test, run, commit** `test(task): e2e`

---

## Task F-T01: `stores/task.ts`

- [ ] **Step 1: Test** filter state syncs, paginated list cached.
- [ ] **Step 2: Implement** matching `04-frontend §5` schema.
- [ ] **Step 3: Run, commit** `feat(frontend): task store`

---

## Task F-T02: `api/task.ts`

- [ ] **Step 1: Test** with `axios-mock-adapter` for CRUD + status patch + by-plan.
- [ ] **Step 2: Implement** thin wrappers returning `response.data`.
- [ ] **Step 3: Run, commit** `feat(frontend): task api`

---

## Task F-T03: `TaskListView`

**Files:** `frontend/src/views/TaskListView.vue`, `frontend/src/components/{TaskItem,TaskFilters}.vue`

- [ ] **Step 1: Component tests** for TaskFilters (`update:filters`), TaskItem (renders title/status).
- [ ] **Step 2: Implement** TaskListView (filters + el-pagination).
- [ ] **Step 3: Run, commit** `feat(frontend): task list view`

---

## Task F-T04: `TaskDetailView`

- [ ] **Step 1: Test** edit form pre-fills, saves via PATCH.
- [ ] **Step 2: Implement** view/edit/toggle status/delete.
- [ ] **Step 3: Run, commit** `feat(frontend): task detail view`

---

## Task F-T05: Playwright e2e for task

`frontend/e2e/task.spec.ts`: register → create → list shows → toggle done → list removes from TODO.

- [ ] **Step 1: Write, run, commit** `test(frontend): task e2e`

---
