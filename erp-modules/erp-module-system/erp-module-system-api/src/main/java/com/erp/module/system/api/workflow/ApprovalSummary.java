package com.erp.module.system.api.workflow;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单据当前审批状态（详情页页头显示“审批中：财务审核（李四）”）。
 *
 * @param assigneeNames 当前节点待处理人姓名
 */
public record ApprovalSummary(Long instanceId, String nodeName, List<String> assigneeNames, Long initiatorId, LocalDateTime startedAt) {
}
