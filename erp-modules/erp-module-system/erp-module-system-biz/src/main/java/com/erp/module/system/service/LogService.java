package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.module.ErpModule;
import com.erp.framework.operlog.OperLogRecord;
import com.erp.framework.operlog.OperLogRecorder;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.controller.vo.LogVOs.DocLogResp;
import com.erp.module.system.controller.vo.LogVOs.LoginLogQuery;
import com.erp.module.system.controller.vo.LogVOs.LoginLogResp;
import com.erp.module.system.controller.vo.LogVOs.OperLogQuery;
import com.erp.module.system.controller.vo.LogVOs.OperLogResp;
import com.erp.module.system.dal.dataobject.DocLogDO;
import com.erp.module.system.dal.dataobject.LoginLogDO;
import com.erp.module.system.dal.dataobject.OperLogDO;
import com.erp.module.system.dal.mapper.DocLogMapper;
import com.erp.module.system.dal.mapper.LoginLogMapper;
import com.erp.module.system.dal.mapper.OperLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日志审计（01-11）：操作日志保存（{@link OperLogRecorder}）、单据操作日志（{@link DocLogApi}）、查询、按保留期清理。
 * 日志只增不改，界面上不能修改或删除。
 */
@Slf4j
@Service
public class LogService implements OperLogRecorder, DocLogApi {

    private static final int CLEANUP_BATCH = 5000;

    private final LoginLogMapper loginLogMapper;
    private final OperLogMapper operLogMapper;
    private final DocLogMapper docLogMapper;
    private final ParamApi paramApi;
    private final Map<String, String> moduleNames;

    public LogService(LoginLogMapper loginLogMapper, OperLogMapper operLogMapper, DocLogMapper docLogMapper, ParamApi paramApi,
                      List<ErpModule> modules) {
        this.loginLogMapper = loginLogMapper;
        this.operLogMapper = operLogMapper;
        this.docLogMapper = docLogMapper;
        this.paramApi = paramApi;
        this.moduleNames = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::name, (a, b) -> a));
    }

    // ==================== 写入 ====================

    /** 由框架在异步线程中调用 */
    @Override
    public void record(OperLogRecord r) {
        OperLogDO d = new OperLogDO();
        d.setTraceId(r.traceId());
        d.setModuleCode(r.moduleCode());
        d.setAction(r.action());
        d.setMethod(r.method());
        d.setPath(r.path());
        d.setParams(r.params());
        d.setResult(r.result());
        d.setErrorCode(r.errorCode());
        d.setErrorMsg(r.errorMsg());
        d.setDurationMs(r.durationMs());
        d.setUserId(r.userId());
        d.setUsername(r.username());
        d.setRealName(r.realName());
        d.setIp(r.ip());
        d.setCreatedAt(r.createdAt());
        operLogMapper.insert(d);
    }

    /** 与业务操作同一事务写入（调用方的事务） */
    @Override
    public void record(String bizType, Long bizId, String bizNo, String action, String actionName, String fromStatus,
                       String toStatus, String reason) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        DocLogDO d = new DocLogDO();
        d.setBizType(bizType);
        d.setBizId(bizId);
        d.setBizNo(bizNo);
        d.setAction(action);
        d.setActionName(actionName);
        d.setFromStatus(fromStatus);
        d.setToStatus(toStatus);
        d.setReason(reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500));
        d.setOperatorId(user == null ? null : user.id());
        d.setOperatorName(user == null ? "系统" : user.realName());
        d.setCreatedAt(LocalDateTime.now());
        docLogMapper.insert(d);
    }

    // ==================== 查询 ====================

    /** R04：查询时间范围最大 3 个月；未指定时默认今天 */
    private static LocalDateTime[] range(LocalDateTime from, LocalDateTime to) {
        LocalDateTime f = from == null ? LocalDateTime.now().toLocalDate().atStartOfDay() : from;
        LocalDateTime t = to == null ? LocalDateTime.now() : to;
        if (f.plusMonths(3).isBefore(t)) throw new BizException(SystemErrorCodes.LOG_RANGE_TOO_LARGE);
        return new LocalDateTime[]{f, t};
    }

    public PageResult<LoginLogResp> loginLogs(LoginLogQuery q) {
        LocalDateTime[] r = range(q.getTimeFrom(), q.getTimeTo());
        LambdaQueryWrapper<LoginLogDO> w = new LambdaQueryWrapper<LoginLogDO>()
                .like(StringUtils.hasText(q.getUsername()), LoginLogDO::getUsername, q.getUsername() == null ? null : q.getUsername().trim())
                .eq(StringUtils.hasText(q.getResult()), LoginLogDO::getResult, q.getResult())
                .likeRight(StringUtils.hasText(q.getIp()), LoginLogDO::getIp, q.getIp() == null ? null : q.getIp().trim())
                .ge(LoginLogDO::getCreatedAt, r[0]).le(LoginLogDO::getCreatedAt, r[1])
                .orderByDesc(LoginLogDO::getCreatedAt).orderByDesc(LoginLogDO::getId);
        return loginLogMapper.selectPage(q, w).map(l -> new LoginLogResp(l.getId(), l.getCreatedAt(), l.getUsername(), l.getRealName(),
                l.getLogType(), l.getResult(), l.getIp(), l.getBrowser(), l.getOs()));
    }

    public PageResult<OperLogResp> operLogs(OperLogQuery q) {
        LambdaQueryWrapper<OperLogDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getTraceId())) {
            // 按追踪号精确查询时不限制时间范围
            w.eq(OperLogDO::getTraceId, q.getTraceId().trim());
        } else {
            LocalDateTime[] r = range(q.getTimeFrom(), q.getTimeTo());
            w.ge(OperLogDO::getCreatedAt, r[0]).le(OperLogDO::getCreatedAt, r[1]);
        }
        w.eq(q.getUserId() != null, OperLogDO::getUserId, q.getUserId())
                .eq(StringUtils.hasText(q.getModuleCode()), OperLogDO::getModuleCode, q.getModuleCode())
                .like(StringUtils.hasText(q.getAction()), OperLogDO::getAction, q.getAction() == null ? null : q.getAction().trim())
                .eq(StringUtils.hasText(q.getResult()), OperLogDO::getResult, q.getResult())
                .orderByDesc(OperLogDO::getCreatedAt).orderByDesc(OperLogDO::getId);
        return operLogMapper.selectPage(q, w).map(l -> toResp(l, false));
    }

    public OperLogResp operLog(Long id) {
        OperLogDO l = operLogMapper.selectById(id);
        if (l == null) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.DATA_NOT_EXISTS, "操作日志");
        return toResp(l, true);
    }

    private OperLogResp toResp(OperLogDO l, boolean withParams) {
        return new OperLogResp(l.getId(), l.getCreatedAt(), l.getUserId(), l.getUsername(), l.getRealName(), l.getModuleCode(),
                moduleNames.getOrDefault(l.getModuleCode(), l.getModuleCode()), l.getAction(), l.getMethod(), l.getPath(), l.getResult(),
                l.getErrorCode(), l.getErrorMsg(), l.getDurationMs(), l.getIp(), l.getTraceId(), withParams ? l.getParams() : null);
    }

    public List<DocLogResp> docLogs(String bizType, Long bizId) {
        return docLogMapper.selectList(new LambdaQueryWrapper<DocLogDO>().eq(DocLogDO::getBizType, bizType).eq(DocLogDO::getBizId, bizId)
                        .orderByAsc(DocLogDO::getCreatedAt).orderByAsc(DocLogDO::getId))
                .stream().map(d -> new DocLogResp(d.getId(), d.getAction(), d.getActionName(), d.getFromStatus(), d.getToStatus(),
                        d.getReason(), d.getOperatorName(), d.getCreatedAt())).toList();
    }

    // ==================== 清理（R01：每天 02:00；单据操作日志永久保留） ====================

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanup() {
        LocalDateTime operBefore = LocalDateTime.now().minusDays(paramApi.getInt("sys.log.oper-retention-days"));
        LocalDateTime loginBefore = LocalDateTime.now().minusDays(paramApi.getInt("sys.log.login-retention-days"));
        int oper = deleteInBatches(() -> operLogMapper.selectList(new LambdaQueryWrapper<OperLogDO>().select(OperLogDO::getId)
                .lt(OperLogDO::getCreatedAt, operBefore).last("LIMIT " + CLEANUP_BATCH)).stream().map(OperLogDO::getId).toList(), operLogMapper::deleteBatchIds);
        int login = deleteInBatches(() -> loginLogMapper.selectList(new LambdaQueryWrapper<LoginLogDO>().select(LoginLogDO::getId)
                .lt(LoginLogDO::getCreatedAt, loginBefore).last("LIMIT " + CLEANUP_BATCH)).stream().map(LoginLogDO::getId).toList(), loginLogMapper::deleteBatchIds);
        log.info("[日志清理] 删除操作日志 {} 条、登录日志 {} 条", oper, login);
    }

    private static int deleteInBatches(java.util.function.Supplier<List<Long>> nextBatch, java.util.function.Function<List<Long>, Integer> delete) {
        int total = 0;
        while (true) {
            List<Long> ids = nextBatch.get();
            if (ids.isEmpty()) return total;
            total += delete.apply(ids);
            if (ids.size() < CLEANUP_BATCH) return total;
        }
    }
}
