# EVOPS 基础工作区

这是 springboot的骨架项目。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

根目录的 `..\..\docs\schema\evops.sql` 是交付副本，应与工作区 `src/main/resources/schema.sql` 保持一致。

## 海水养殖台账（aqua-1）

模块包路径 `com.evops.aqua`，已打通：建池 → 投苗 → 日常记录 → 饲料/用药 → 抽检 → 换池 → 出池。成本核算留待后续版本。

- 养殖池档案：水深、盐度、所属海区（`POST /api/aqua/ponds`）
- 投苗批次：苗种来源、入池数量、计划出池日；批次编号全局唯一（`POST /api/aqua/batches`）
- 饲料、用药、日常、抽检：按池按批留痕（`/api/aqua/records/**`），批次不在该池或已出池会被拒绝
- 换池：`POST /api/aqua/batches/{id}/transfer` 自动生成转移记录并更新批次所在池
- 出池：`POST /api/aqua/batches/{id}/harvest` 生成出池记录，批次封存
- 原始投苗单不可删除：无 DELETE 接口，表上无删除标记
- 批次全链路追溯：`GET /api/aqua/batches/{id}/trace`

接口需先登录：`POST /api/auth/login`（bootstrap / bootstrap）。

验收：`mvn test -Dtest=AquaLedgerAcceptanceTest`，从养殖池档案入口追到投苗批次落库，逐检查点输出报告，失败即停并给出停点。

## 水质采样计划与缺测补录（aqua-2）

模块包路径 `com.evops.aqua.wq`，在台账之上打通：分阶段规则版本 → 采样计划 → 按池生成任务 → 草稿/提交 → 缺测补录 → 主管审核 → 池塘健康曲线，全程留证。

- 指标规则版本化（`POST /api/aqua/wq/rule-versions`）：不同养殖阶段配置采样频率（间隔天数）与指标正常范围；新版本发布后旧版本自动停用，**新版本只作用于未开始任务**——在执行计划的版本指针切换，从未录入过的草稿任务按新版本重建指标快照；已暂存/已提交/已审核任务冻结旧版本不变。
- 采样计划（`/api/aqua/wq/plans`）：绑定规则版本与养殖阶段、覆盖日期区间与池塘；`POST /{id}/tasks/generate` **按池按频率生成任务**，同计划同池同日幂等，重复请求不产生第二条结果。
- 采样执行（`/api/aqua/wq/tasks/{id}`）：`PUT /draft` 可只录部分指标暂存草稿；`POST /submit` 提交时每个指标必须有值，**缺测项必须说明原因**，否则拒绝；重复提交返回同一任务。
- 缺测/历史补录（`POST /api/aqua/wq/backfills`，requestId 强制幂等）：**只能追加一行，绝不覆盖原采样值**；重复补录请求返回首次记录。
- 主管审核（`POST /api/aqua/wq/tasks/{id}/review`）：驳回必须填意见且退回草稿；**审核通过后已测值才进入池塘健康曲线**（`GET /api/aqua/wq/ponds/{pondId}/health-curve`，缺测项不入曲线，取原值不取补录值）；重复审核不重复写曲线。
- 所有修改保留证据：生成/暂存/提交/补录/驳回/通过/版本切换均写 `t_wq_change_evidence`（含操作前后快照与幂等请求号），可经 `GET /api/aqua/wq/evidence` 按对象或 requestId 追溯。

验收：`mvn test -Dtest=WaterQualitySamplingAcceptanceTest`，14 个检查点逐段输出报告，失败即停并给出停点。

## 饲料批号与配方投喂（aqua-3）

模块包路径 `com.evops.aqua.feed`，在台账之上打通：饲料批号三量账 → 配方按苗种/日龄/水温版本化 → 投喂单准备即冻结配方快照 → 锁定占用批号 → 临时减量审批 → 执行转消耗，日汇总试喂与正式分列，全程留证。

- 饲料批号（`POST /api/aqua/feed/batches`）：一批一行三量账（入库 / 锁定占用 / 已消耗），可用量=入库−锁定−已消耗；批号不可删除。锁定时按 **FEFO 先到期先用** 从未过期批号占用，可跨批号拼配；**批号已过期或可用库存不足一律拦截，且不残留半占用**（同事务回滚）。
- 投喂配方版本（`POST /api/aqua/feed/formulas`）：按苗种发布，版本下以「日龄闭区间 + 水温闭区间」配置饲料与计划投喂量，命中多行取区间最窄（最具体）者。**配方调整发布新版本，同苗种旧版本自动停用**；投喂单建立即冻结当时版本指针与计划量快照，**历史投喂永远引用旧版本快照，不受新版本影响**。
- 投喂单（`/api/aqua/feed/orders`）：`POST /prepare` 按池按批按日，日龄由投苗日与投喂日推算（不由前端填写），区分 `FORMAL` 正式投喂 / `TRIAL` 试喂，同池同批同日同类型幂等；`POST /{id}/lock` 按 FEFO 占用批号；**`POST /{id}/execute` 只有锁定后才允许执行**（草稿态直接执行拒绝），执行把锁定占用转为批号实际消耗，重复执行幂等；`POST /{id}/unlock` 释放占用退回草稿。
- 临时减量（`POST /api/aqua/feed/orders/{id}/items/{itemId}/reduction`）：仅锁定态可登记，实际量必须小于锁定量，**必须登记减量原因与审批人**，登记后释放差额占用，执行按减量后实际量消耗。
- 日汇总（`GET /api/aqua/feed/orders/daily-summary`）：按池按日只统计已执行投喂，**试喂与正式投喂严格分列、绝不混算**。
- 海水养殖管理权限（`/api/aqua/feed/access`）：`POST /tighten` 收紧、`POST /relax` 放开（均须登记原因）。**收紧后饲料批号停止推进**——新批号入库、锁定占用、执行消耗一律拦截；**已确认的配方版本与历史投喂事实保留不变**。
- 全程留证：入库/锁定/解锁/减量/执行/版本停用/权限收紧放开均写 `t_feed_change_evidence`（含操作前后 JSON 快照与幂等请求号），可经 `GET /api/aqua/feed/evidence` 按对象或 requestId 追溯。

验收：`mvn test -Dtest=FeedFormulaFeedingAcceptanceTest`，17 个检查点逐段输出报告，失败即停并给出停点。

## 养殖疫病观察与隔离池调度（aqua-4）

模块包路径 `com.evops.aqua.disease`，在台账之上打通：发现异常症状开观察单 → 按风险等级推荐隔离池 → 调度前校验（等级/容量/消毒/权限）→ 调入隔离池 → 隔离中禁止混养和出池 → 兽医复核解除 → 数量逐笔返回原池，定时巡查检查全程留证。

- 疫病观察单（`POST /api/aqua/disease/observations`）：症状、风险等级（LOW/MEDIUM/HIGH）、观察日期、**开单操作者必填**；批次须在养且当前在该池；requestId 幂等，重复开单返回首次单据，不产生第二条。
- 隔离池档案（`/api/aqua/disease/isolation-pools`）：挂在养殖池上，登记可承接的最高风险等级与容量；`POST /{id}/disinfect` 登记消毒与有效期；`POST /{id}/dispatch-permission` 开关调度权限（须登记原因与操作者）。
- 按风险等级推荐隔离池（`GET /api/aqua/disease/observations/{id}/recommend-pools`）：池等级不低于观察单风险等级才可承接；每个池给出 eligible 标记、剩余容量与**冲突原因预览**。
- 隔离调度（`POST /api/aqua/disease/observations/{id}/dispatch`，requestId 幂等）：转移前逐条校验**池等级、调度权限、消毒状态、禁止混养、池容量**，**调度失败必须说明冲突原因**（如「容量不足：剩余容量4000，需要5000」），失败不落任何记录；成功则生成隔离调度单并写数量流水。
- 隔离中的批次**禁止混养和出池**：已有隔离中批次的隔离池拒绝再调入（专池专用）；台账侧出池、换池统一拦截，须先由兽医复核解除。
- 解除隔离（`POST /api/aqua/disease/isolations/{id}/lift`）：**必须由兽医复核**——兽医姓名与复核意见必填，缺一则拒；解除后数量按流水返回原池，重复解除幂等不重复写流水。
- 数量变化逐笔记录：原池→隔离池（ISOLATE_IN）、隔离池→原池（RETURN_BACK）每笔一行 `t_dis_pool_movement`（含数量、日期、操作者），可经 `GET /api/aqua/disease/isolations/{id}/movements` 或 `GET /api/aqua/disease/movements?batchId=` 逐笔查询。
- 定时巡查检查（Quartz 每日 06:30，`POST /api/aqua/disease/watch/run-check` 可手动触发同一逻辑）：对未完结观察单（观察中/已隔离）按日生成检查记录，**观察单+日期唯一——重启定时任务后重跑同日检查不多写记录；已有记录一律不更新，操作者（沿用开单人）不丢失**；已解除的观察单不再检查。
- 全程留证：开单/调度/解除/消毒/权限调整均写 `t_dis_change_evidence`（含操作前后 JSON 快照与幂等请求号），可经 `GET /api/aqua/disease/evidence` 按对象或 requestId 追溯。

验收：`mvn test -Dtest=DiseaseIsolationAcceptanceTest`，15 个检查点逐段输出报告，失败即停并给出停点。



