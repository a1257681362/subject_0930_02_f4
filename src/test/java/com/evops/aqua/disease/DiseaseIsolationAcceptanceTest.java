package com.evops.aqua.disease;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.entity.DisChangeEvidence;
import com.evops.aqua.disease.entity.DisIsolation;
import com.evops.aqua.disease.entity.DisObservation;
import com.evops.aqua.disease.entity.DisPoolMovement;
import com.evops.aqua.disease.entity.DisWatchCheck;
import com.evops.aqua.disease.job.DiseaseQuartzConfig;
import com.evops.aqua.disease.mapper.DisChangeEvidenceMapper;
import com.evops.aqua.disease.mapper.DisIsolationMapper;
import com.evops.aqua.disease.mapper.DisObservationMapper;
import com.evops.aqua.disease.mapper.DisPoolMovementMapper;
import com.evops.aqua.disease.mapper.DisWatchCheckMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 养殖疫病观察与隔离池调度验收（aqua-4）。
 * 主线：开观察单（操作者必填、requestId 幂等）→ 按风险等级推荐隔离池
 * → 调度前校验池等级/权限/消毒/容量，失败说明冲突原因 → 调入隔离池（数量流水逐笔）
 * → 隔离中禁止混养、禁止出池/换池 → 兽医复核解除（数量返回原池）
 * → 定时巡查检查：重启重跑不多写记录、不丢操作者 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiseaseIsolationAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private Scheduler scheduler;
    @Autowired
    private DisObservationMapper observationMapper;
    @Autowired
    private DisIsolationMapper isolationMapper;
    @Autowired
    private DisPoolMovementMapper movementMapper;
    @Autowired
    private DisWatchCheckMapper watchCheckMapper;
    @Autowired
    private DisChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondMain;      // 原池
    private Long pondH1;        // 高级隔离池（消毒、权限开、容量6000）
    private Long pondL1;        // 低级隔离池
    private Long pondH2;        // 高级隔离池（未消毒）
    private Long pondH3;        // 高级隔离池（关闭调度权限）
    private Long pondH4;        // 高级隔离池（容量仅4000）
    private Long batchCroaker;  // 大黄鱼批次 50000
    private Long batchGrouper;  // 石斑鱼批次 30000
    private Long poolH1;
    private Long poolL1;
    private Long poolH2;
    private Long poolH3;
    private Long poolH4;
    private Long obsCroaker;    // 高风险观察单
    private Long obsGrouper;    // 低风险观察单
    private Long isolationId;

    @Test
    void acceptanceDiseaseObservationAndIsolation() {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "建原池与五个隔离池用养殖池", () -> {
            pondMain = createPond("DS-P01", "疫病1号养成池");
            pondH1 = createPond("DS-ISO-H1", "高级隔离池1");
            pondL1 = createPond("DS-ISO-L1", "低级隔离池1");
            pondH2 = createPond("DS-ISO-H2", "高级隔离池2");
            pondH3 = createPond("DS-ISO-H3", "高级隔离池3");
            pondH4 = createPond("DS-ISO-H4", "高级隔离池4");
        });

        checkpoint("CP2", "投苗：大黄鱼50000尾、石斑鱼30000尾入原池", () -> {
            batchCroaker = stockBatch("DS-C01", pondMain, "LARGE_YELLOW_CROAKER", "大黄鱼苗", 50000);
            batchGrouper = stockBatch("DS-G01", pondMain, "GROUPER", "石斑鱼苗", 30000);
        });

        checkpoint("CP3", "登记隔离池档案：等级/容量各异，按需消毒、H3关闭调度权限", () -> {
            poolH1 = createIsolationPool(pondH1, "HIGH", 6000);
            poolL1 = createIsolationPool(pondL1, "LOW", 30000);
            poolH2 = createIsolationPool(pondH2, "HIGH", 50000);
            poolH3 = createIsolationPool(pondH3, "HIGH", 50000);
            poolH4 = createIsolationPool(pondH4, "HIGH", 4000);
            // H2 不消毒；其余消毒有效期至2026-12-31
            disinfect(poolH1, "2026-10-03", "2026-12-31", "消毒员赵一");
            disinfect(poolL1, "2026-10-03", "2026-12-31", "消毒员赵一");
            disinfect(poolH3, "2026-10-03", "2026-12-31", "消毒员赵一");
            disinfect(poolH4, "2026-10-03", "2026-12-31", "消毒员赵一");
            JsonNode perm = json(call(HttpMethod.POST,
                    "/api/aqua/disease/isolation-pools/" + poolH3 + "/dispatch-permission",
                    permissionBody(false, "池体维修暂停使用", "管理员钱五")));
            assertSuccess(perm, "关闭H3调度权限");
            check(!perm.path("data").path("dispatchEnabled").asBoolean(true), "H3调度权限应已关闭");
        });

        checkpoint("CP4", "发现异常症状开高风险观察单：操作者必填落库，同requestId重复开单幂等", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/disease/observations",
                    observationBody(pondMain, batchCroaker, "体表溃疡、游动迟缓", "HIGH",
                            "2026-10-05", "巡塘员孙甲", "obs-1")));
            assertSuccess(resp, "开立观察单");
            obsCroaker = resp.path("data").path("id").asLong();
            check("OPEN".equals(resp.path("data").path("status").asText()), "新观察单应为观察中OPEN");
            check("巡塘员孙甲".equals(resp.path("data").path("reportedBy").asText()),
                    "开单操作者必须落库");

            JsonNode again = json(call(HttpMethod.POST, "/api/aqua/disease/observations",
                    observationBody(pondMain, batchCroaker, "体表溃疡、游动迟缓", "HIGH",
                            "2026-10-05", "巡塘员孙甲", "obs-1")));
            assertSuccess(again, "重复开单请求");
            check(again.path("data").path("id").asLong() == obsCroaker, "同requestId应返回同一观察单");
            Long count = observationMapper.selectCount(new QueryWrapper<DisObservation>()
                    .eq("batch_id", batchCroaker));
            check(count != null && count == 1, "重复开单不得产生第二条，实际=" + count);

            JsonNode noOperator = json(call(HttpMethod.POST, "/api/aqua/disease/observations",
                    observationBody(pondMain, batchCroaker, "趴边", "LOW",
                            "2026-10-05", null, "obs-x")));
            check(!noOperator.path("success").asBoolean(true), "缺操作者开单应被拒绝");
        });

        checkpoint("CP5", "按风险等级推荐隔离池：仅H1可承接高风险，其余各带冲突原因", () -> {
            JsonNode resp = json(call(HttpMethod.GET,
                    "/api/aqua/disease/observations/" + obsCroaker + "/recommend-pools?quantity=5000", null));
            assertSuccess(resp, "推荐隔离池");
            JsonNode pools = resp.path("data").path("pools");
            check(pools.size() == 5, "应列出全部5个隔离池，实际=" + pools.size());
            JsonNode first = pools.get(0);
            check(first.path("isolationPoolId").asLong() == poolH1
                            && first.path("eligible").asBoolean(false),
                    "H1应为唯一可承接池且排在最前");
            for (JsonNode pool : pools) {
                long pid = pool.path("isolationPoolId").asLong();
                boolean eligible = pool.path("eligible").asBoolean(true);
                if (pid == poolH1) {
                    check(eligible, "H1应可承接");
                } else {
                    check(!eligible && pool.path("conflictReasons").size() > 0,
                            "池" + pid + "不可承接且必须给出冲突原因");
                }
            }
        });

        checkpoint("CP6", "调度失败必须说明冲突原因：等级/权限/消毒/容量逐条拦截且不落记录", () -> {
            JsonNode low = json(call(HttpMethod.POST,
                    "/api/aqua/disease/observations/" + obsCroaker + "/dispatch",
                    dispatchBody(poolL1, 5000, "2026-10-06", "调度员吴乙", "disp-low")));
            check(!low.path("success").asBoolean(true)
                            && low.path("message").asText().contains("等级")
                            && low.path("message").asText().contains("不能承接"),
                    "低级池应提示等级不足：" + low.path("message").asText());

            JsonNode perm = json(call(HttpMethod.POST,
                    "/api/aqua/disease/observations/" + obsCroaker + "/dispatch",
                    dispatchBody(poolH3, 5000, "2026-10-06", "调度员吴乙", "disp-perm")));
            check(!perm.path("success").asBoolean(true)
                            && perm.path("message").asText().contains("调度权限")
                            && perm.path("message").asText().contains("池体维修"),
                    "关闭权限池应提示权限冲突与原因：" + perm.path("message").asText());

            JsonNode disin = json(call(HttpMethod.POST,
                    "/api/aqua/disease/observations/" + obsCroaker + "/dispatch",
                    dispatchBody(poolH2, 5000, "2026-10-06", "调度员吴乙", "disp-disin")));
            check(!disin.path("success").asBoolean(true)
                            && disin.path("message").asText().contains("未消毒"),
                    "未消毒池应提示消毒冲突：" + disin.path("message").asText());

            JsonNode cap = json(call(HttpMethod.POST,
                    "/api/aqua/disease/observations/" + obsCroaker + "/dispatch",
                    dispatchBody(poolH4, 5000, "2026-10-06", "调度员吴乙", "disp-cap")));
            check(!cap.path("success").asBoolean(true)
                            && cap.path("message").asText().contains("容量不足")
                            && cap.path("message").asText().contains("4000"),
                    "小容量池应提示容量不足及剩余量：" + cap.path("message").asText());

            Long isoCount = isolationMapper.selectCount(new QueryWrapper<>());
            Long movCount = movementMapper.selectCount(new QueryWrapper<>());
            check(isoCount != null && isoCount == 0 && movCount != null && movCount == 0,
                    "调度失败不得落任何隔离单与流水，实际隔离单=" + isoCount + "流水=" + movCount);
        });

        checkpoint("CP7", "调度成功调入H1：数量5000逐笔记录，同requestId重复调度幂等", () -> {
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/disease/observations/" + obsCroaker + "/dispatch",
                    dispatchBody(poolH1, 5000, "2026-10-06", "调度员吴乙", "disp-1")));
            assertSuccess(resp, "调度调入H1");
            isolationId = resp.path("data").path("id").asLong();
            check("ISOLATING".equals(resp.path("data").path("status").asText()), "调度单应隔离中");
            check("调度员吴乙".equals(resp.path("data").path("operatorName").asText()),
                    "调度操作者必须落库");
            DisObservation obs = observationMapper.selectById(obsCroaker);
            check(DisObservation.STATUS_ISOLATED.equals(obs.getStatus()), "观察单应转为已隔离");

            List<DisPoolMovement> movements = movementMapper.selectList(
                    new QueryWrapper<DisPoolMovement>().eq("isolation_id", isolationId));
            check(movements.size() == 1, "应恰有1笔转入流水，实际=" + movements.size());
            DisPoolMovement in = movements.get(0);
            check(DisPoolMovement.TYPE_ISOLATE_IN.equals(in.getMoveType())
                            && in.getFromPondId().equals(pondMain) && in.getToPondId().equals(pondH1)
                            && in.getQuantity() == 5000 && "调度员吴乙".equals(in.getOperatorName()),
                    "转入流水应记录原池→隔离池5000尾与操作者");

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/disease/observations/" + obsCroaker + "/dispatch",
                    dispatchBody(poolH1, 5000, "2026-10-06", "调度员吴乙", "disp-1")));
            assertSuccess(again, "重复调度请求");
            check(again.path("data").path("id").asLong() == isolationId, "同requestId应返回同一调度单");
            Long movCount = movementMapper.selectCount(new QueryWrapper<>());
            check(movCount != null && movCount == 1, "重复调度不得重复写流水，实际=" + movCount);
        });

        checkpoint("CP8", "禁止混养：H1已有隔离中批次，第二张观察单调入被拒并说明原因", () -> {
            JsonNode obs2 = json(call(HttpMethod.POST, "/api/aqua/disease/observations",
                    observationBody(pondMain, batchGrouper, "摄食下降", "LOW",
                            "2026-10-06", "巡塘员李丙", "obs-2")));
            assertSuccess(obs2, "开立第二张观察单");
            obsGrouper = obs2.path("data").path("id").asLong();

            // H1剩余容量1000，调入500本可装下——唯一冲突是已有隔离中批次
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/disease/observations/" + obsGrouper + "/dispatch",
                    dispatchBody(poolH1, 500, "2026-10-06", "调度员吴乙", "disp-mix")));
            check(!resp.path("success").asBoolean(true)
                            && resp.path("message").asText().contains("禁止混养"),
                    "已有隔离中批次的池应禁止混养：" + resp.path("message").asText());
        });

        checkpoint("CP9", "隔离中的批次禁止出池和换池", () -> {
            Map<String, Object> harvest = new HashMap<>();
            harvest.put("harvestDate", "2026-10-07");
            harvest.put("quantity", 45000);
            JsonNode h = json(call(HttpMethod.POST,
                    "/api/aqua/batches/" + batchCroaker + "/harvest", harvest));
            check(!h.path("success").asBoolean(true)
                            && h.path("message").asText().contains("隔离中"),
                    "隔离中批次出池应被拦截：" + h.path("message").asText());

            Map<String, Object> transfer = new HashMap<>();
            transfer.put("toPondId", pondL1);
            transfer.put("transferDate", "2026-10-07");
            transfer.put("reason", "试图换池");
            JsonNode t = json(call(HttpMethod.POST,
                    "/api/aqua/batches/" + batchCroaker + "/transfer", transfer));
            check(!t.path("success").asBoolean(true)
                            && t.path("message").asText().contains("隔离中"),
                    "隔离中批次换池应被拦截：" + t.path("message").asText());
        });

        checkpoint("CP10", "定时巡查检查：未完结观察单各落一行；重启重跑不多写、不丢操作者", () -> {
            check(scheduler.getJobDetail(
                    JobKey.jobKey(DiseaseQuartzConfig.JOB_NAME, DiseaseQuartzConfig.JOB_GROUP)) != null,
                    "疫病巡查定时任务应已注册到调度器");

            JsonNode first = json(call(HttpMethod.POST,
                    "/api/aqua/disease/watch/run-check?checkDate=2026-10-06", null));
            assertSuccess(first, "首次巡查检查");
            check(first.path("data").path("created").asInt() == 2,
                    "两张未完结观察单应各生成1行，实际=" + first.path("data").path("created").asInt());

            List<DisWatchCheck> checks = watchCheckMapper.selectList(new QueryWrapper<>());
            check(checks.size() == 2, "检查记录应为2行，实际=" + checks.size());
            for (DisWatchCheck c : checks) {
                if (c.getObservationId().equals(obsCroaker)) {
                    check("巡塘员孙甲".equals(c.getOperatorName()), "检查记录必须沿用开单操作者");
                    check(DisObservation.STATUS_ISOLATED.equals(c.getObservationStatus())
                                    && c.getIsolationId() != null && c.getIsolationId().equals(isolationId),
                            "已隔离观察单的检查应带隔离单快照");
                    check(c.getDetail().contains("隔离中") && c.getDetail().contains("DS-ISO-H1"),
                            "检查详情应说明隔离池：" + c.getDetail());
                } else {
                    check("巡塘员李丙".equals(c.getOperatorName()), "第二张观察单检查记录操作者应为李丙");
                }
            }

            // 模拟定时任务重启后重跑同日检查：不得多写记录，也不得覆盖丢失操作者
            JsonNode rerun = json(call(HttpMethod.POST,
                    "/api/aqua/disease/watch/run-check?checkDate=2026-10-06", null));
            assertSuccess(rerun, "重启后重跑巡查");
            check(rerun.path("data").path("created").asInt() == 0,
                    "重跑同日检查不得新增记录，实际新增=" + rerun.path("data").path("created").asInt());
            List<DisWatchCheck> after = watchCheckMapper.selectList(new QueryWrapper<>());
            check(after.size() == 2, "重跑后检查记录不得变多，实际=" + after.size());
            for (DisWatchCheck c : after) {
                check(c.getOperatorName() != null && !c.getOperatorName().trim().isEmpty(),
                        "重跑后操作者不得丢失");
            }
        });

        checkpoint("CP11", "解除隔离必须由兽医复核：缺兽医/意见被拒；合法解除数量返回原池，重复解除幂等", () -> {
            JsonNode noVet = json(call(HttpMethod.POST,
                    "/api/aqua/disease/isolations/" + isolationId + "/lift",
                    liftBody(null, "恢复正常", "2026-10-09", "操作员郑丁", "lift-x")));
            check(!noVet.path("success").asBoolean(true), "缺兽医姓名的解除应被拒绝");
            JsonNode noOpinion = json(call(HttpMethod.POST,
                    "/api/aqua/disease/isolations/" + isolationId + "/lift",
                    liftBody("兽医周医生", null, "2026-10-09", "操作员郑丁", "lift-y")));
            check(!noOpinion.path("success").asBoolean(true), "缺复核意见的解除应被拒绝");
            check(movementMapper.selectCount(new QueryWrapper<>()) == 1L,
                    "非法解除不得写流水");

            JsonNode ok = json(call(HttpMethod.POST,
                    "/api/aqua/disease/isolations/" + isolationId + "/lift",
                    liftBody("兽医周医生", "症状消失，复检阴性，同意解除", "2026-10-09", "操作员郑丁", "lift-1")));
            assertSuccess(ok, "兽医复核解除隔离");
            check("LIFTED".equals(ok.path("data").path("status").asText()), "调度单应已解除");
            check("兽医周医生".equals(ok.path("data").path("vetName").asText())
                            && ok.path("data").path("vetOpinion").asText().contains("复检阴性"),
                    "兽医与复核意见必须落库");
            check(DisObservation.STATUS_LIFTED.equals(
                    observationMapper.selectById(obsCroaker).getStatus()), "观察单应转为已解除");

            List<DisPoolMovement> movements = movementMapper.selectList(
                    new QueryWrapper<DisPoolMovement>().eq("isolation_id", isolationId).orderByAsc("id"));
            check(movements.size() == 2, "解除后应恰有2笔流水，实际=" + movements.size());
            DisPoolMovement back = movements.get(1);
            check(DisPoolMovement.TYPE_RETURN_BACK.equals(back.getMoveType())
                            && back.getFromPondId().equals(pondH1) && back.getToPondId().equals(pondMain)
                            && back.getQuantity() == 5000 && "操作员郑丁".equals(back.getOperatorName()),
                    "返回流水应记录隔离池→原池5000尾与操作者");

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/disease/isolations/" + isolationId + "/lift",
                    liftBody("兽医周医生", "症状消失，复检阴性，同意解除", "2026-10-09", "操作员郑丁", "lift-1")));
            assertSuccess(again, "重复解除请求");
            check(movementMapper.selectCount(new QueryWrapper<>()) == 2L,
                    "重复解除不得重复写流水");
        });

        checkpoint("CP12", "解除后台账恢复：批次可正常出池；批次数量流水逐笔可查", () -> {
            Map<String, Object> harvest = new HashMap<>();
            harvest.put("harvestDate", "2026-10-10");
            harvest.put("quantity", 45000);
            JsonNode h = json(call(HttpMethod.POST,
                    "/api/aqua/batches/" + batchCroaker + "/harvest", harvest));
            assertSuccess(h, "解除隔离后出池");

            JsonNode mv = json(call(HttpMethod.GET,
                    "/api/aqua/disease/movements?batchId=" + batchCroaker, null));
            assertSuccess(mv, "查询批次数量流水");
            check(mv.path("data").size() == 2, "批次流水应恰有2笔，实际=" + mv.path("data").size());
            check(mv.path("data").get(0).path("quantity").asInt() == 5000
                            && mv.path("data").get(1).path("quantity").asInt() == 5000,
                    "转出与返回数量应一致（5000）");
        });

        checkpoint("CP13", "次日巡查：已解除观察单不再检查，重跑仍不多写", () -> {
            JsonNode day2 = json(call(HttpMethod.POST,
                    "/api/aqua/disease/watch/run-check?checkDate=2026-10-07", null));
            assertSuccess(day2, "次日巡查");
            check(day2.path("data").path("created").asInt() == 1,
                    "次日仅未完结的第二张观察单生成检查，实际=" + day2.path("data").path("created").asInt());
            Long total = watchCheckMapper.selectCount(new QueryWrapper<>());
            check(total != null && total == 3, "检查记录累计应为3行，实际=" + total);
            JsonNode rerun = json(call(HttpMethod.POST,
                    "/api/aqua/disease/watch/run-check?checkDate=2026-10-07", null));
            check(rerun.path("data").path("created").asInt() == 0, "次日重跑不得新增");
            List<DisWatchCheck> day2Checks = watchCheckMapper.selectList(
                    new QueryWrapper<DisWatchCheck>().eq("check_date", "2026-10-07"));
            check(day2Checks.size() == 1
                            && day2Checks.get(0).getObservationId().equals(obsGrouper)
                            && "巡塘员李丙".equals(day2Checks.get(0).getOperatorName()),
                    "次日检查应属于第二张观察单且操作者为李丙");
        });

        checkpoint("CP14", "全程留证：开单/调度/解除/消毒/权限调整证据齐全且含操作者", () -> {
            check(evidenceCount(DisChangeEvidence.ENTITY_OBSERVATION, DisChangeEvidence.ACTION_CREATE) == 2,
                    "观察单开立证据应为2条");
            check(evidenceCount(DisChangeEvidence.ENTITY_ISOLATION, DisChangeEvidence.ACTION_DISPATCH) == 1,
                    "调度证据应为1条");
            check(evidenceCount(DisChangeEvidence.ENTITY_ISOLATION, DisChangeEvidence.ACTION_LIFT) == 1,
                    "解除证据应为1条");
            check(evidenceCount(DisChangeEvidence.ENTITY_ISOLATION_POOL, DisChangeEvidence.ACTION_DISINFECT) == 4,
                    "消毒证据应为4条");
            check(evidenceCount(DisChangeEvidence.ENTITY_ISOLATION_POOL, DisChangeEvidence.ACTION_PERMISSION) == 1,
                    "权限调整证据应为1条");
            List<DisChangeEvidence> liftEv = evidenceMapper.selectList(
                    new QueryWrapper<DisChangeEvidence>()
                            .eq("entity_type", DisChangeEvidence.ENTITY_ISOLATION)
                            .eq("action", DisChangeEvidence.ACTION_LIFT));
            check(liftEv.size() == 1 && liftEv.get(0).getDetail().contains("兽医周医生")
                            && liftEv.get(0).getDetail().contains("操作员郑丁"),
                    "解除证据必须保留兽医与操作者");
            List<DisChangeEvidence> dispatchEv = evidenceMapper.selectList(
                    new QueryWrapper<DisChangeEvidence>()
                            .eq("entity_type", DisChangeEvidence.ENTITY_ISOLATION)
                            .eq("action", DisChangeEvidence.ACTION_DISPATCH));
            check(dispatchEv.size() == 1 && dispatchEv.get(0).getDetail().contains("调度员吴乙"),
                    "调度证据必须保留调度操作者");
        });

        report.add("验收结论：全部检查点通过——观察单幂等开单且操作者落库、按风险等级推荐隔离池、"
                + "调度校验等级/权限/消毒/容量并说明冲突原因、数量变化逐笔记录、隔离中禁止混养和出池/换池、"
                + "兽医复核解除、定时巡查重启重跑不多写不丢操作者、全程留证");
        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private Long createPond(String code, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondCode", code);
        body.put("pondName", name);
        body.put("seaArea", "东山湾海区");
        body.put("waterDepthM", "1.80");
        body.put("salinityPpt", "28.5");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", body));
        assertSuccess(resp, "建池[" + code + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long stockBatch(String batchNo, Long pondId, String species, String source, int quantity)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("pondId", pondId);
        body.put("species", species);
        body.put("seedSource", source);
        body.put("quantity", quantity);
        body.put("stockDate", "2026-10-01");
        body.put("plannedHarvestDate", "2027-03-01");
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/batches", body));
        assertSuccess(resp, "投苗[" + batchNo + "]");
        return resp.path("data").path("id").asLong();
    }

    private Long createIsolationPool(Long pondId, String grade, int capacity) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pondId", pondId);
        body.put("riskGrade", grade);
        body.put("capacityQty", capacity);
        JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/disease/isolation-pools", body));
        assertSuccess(resp, "登记隔离池[" + pondId + "]");
        return resp.path("data").path("id").asLong();
    }

    private void disinfect(Long poolId, String date, String validUntil, String operator) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("disinfectedDate", date);
        body.put("validUntil", validUntil);
        body.put("operator", operator);
        JsonNode resp = json(call(HttpMethod.POST,
                "/api/aqua/disease/isolation-pools/" + poolId + "/disinfect", body));
        assertSuccess(resp, "隔离池消毒[" + poolId + "]");
    }

    private Map<String, Object> permissionBody(boolean enabled, String reason, String operator) {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", enabled);
        m.put("reason", reason);
        m.put("operator", operator);
        return m;
    }

    private Map<String, Object> observationBody(Long pondId, Long batchId, String symptom, String risk,
                                                String date, String reportedBy, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("pondId", pondId);
        m.put("batchId", batchId);
        m.put("symptom", symptom);
        m.put("riskLevel", risk);
        m.put("observeDate", date);
        if (reportedBy != null) {
            m.put("reportedBy", reportedBy);
        }
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> dispatchBody(Long poolId, int quantity, String date,
                                             String operator, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("isolationPoolId", poolId);
        m.put("quantity", quantity);
        m.put("moveDate", date);
        m.put("operator", operator);
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> liftBody(String vetName, String opinion, String date,
                                         String operator, String requestId) {
        Map<String, Object> m = new HashMap<>();
        if (vetName != null) {
            m.put("vetName", vetName);
        }
        if (opinion != null) {
            m.put("vetOpinion", opinion);
        }
        m.put("moveDate", date);
        m.put("operator", operator);
        m.put("requestId", requestId);
        return m;
    }

    private long evidenceCount(String entityType, String action) {
        Long c = evidenceMapper.selectCount(new QueryWrapper<DisChangeEvidence>()
                .eq("entity_type", entityType).eq("action", action));
        return c == null ? 0L : c;
    }

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 疫病观察与隔离池调度验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("===================================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
