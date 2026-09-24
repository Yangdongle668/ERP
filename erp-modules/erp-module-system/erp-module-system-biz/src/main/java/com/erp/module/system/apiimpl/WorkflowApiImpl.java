package com.erp.module.system.apiimpl;

import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审批流引擎上线前的实现：所有单据视为“未配置审批流”，提交即审核（需求 01-系统管理/README 第 7 节）。
 * TODO(system): 实现 08-审批流 后替换。
 */
@Service
public class WorkflowApiImpl implements WorkflowApi {

    @Override
    public StartResult start(String bizType, Long bizId, String bizNo, String title,
                             Map<String, Object> variables, Map<String, Long> bizUsers, Long initiatorId) {
        return StartResult.notRequired();
    }

    @Override
    public void withdraw(String bizType, Long bizId, Long operatorId) {
        // 没有进行中的审批，无需处理
    }

    @Override
    public boolean isRunning(String bizType, Long bizId) {
        return false;
    }
}
