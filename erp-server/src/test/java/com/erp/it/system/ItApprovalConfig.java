package com.erp.it.system;

import com.erp.common.exception.BizException;
import com.erp.common.exception.ErrorCode;
import com.erp.module.system.api.workflow.ApprovalBizDefinition;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 测试用的可审批单据类型与“业务模块”监听器 */
@Configuration
class ItApprovalConfig {

    static final String BIZ = "IT_ORDER";
    /** 收到的审批结果事件 */
    static final List<ApprovalCompletedEvent> EVENTS = new CopyOnWriteArrayList<>();
    /** 这些单据审核时模拟业务校验失败（T11 库存不足） */
    static final Set<Long> FAIL_ON_APPROVE = ConcurrentHashMap.newKeySet();

    @Bean
    ApprovalBizDefinition itOrderApproval() {
        return ApprovalBizDefinition.of(BIZ, "测试订单", "system", "/it/order/{id}")
                .numberField("amount", "金额")
                .enumField("level", "客户等级", List.of(new ApprovalBizDefinition.Option("A", "A"), new ApprovalBizDefinition.Option("D", "D")))
                .deptField("deptId", "业务部门")
                .boolField("urgent", "加急")
                .userField("salespersonId", "业务员");
    }

    @EventListener
    public void onCompleted(ApprovalCompletedEvent e) {
        if (!BIZ.equals(e.getBizType())) return;
        if (e.getResult() == ApprovalCompletedEvent.Result.APPROVED && FAIL_ON_APPROVE.contains(e.getBizId())) {
            throw new BizException(new ErrorCode(1_001_999_000, "库存不足"));
        }
        EVENTS.add(e);
    }
}
