package com.erp.module.workbench.service;

import com.erp.module.system.api.job.ErpJob;
import com.erp.module.workbench.service.message.MessageService;
import com.erp.module.workbench.service.todo.TodoService;
import org.springframework.stereotype.Component;

/** 工作台定时任务 */
@Component
public class WorkbenchJobs {

    private final TodoService todoService;
    private final MessageService messageService;

    public WorkbenchJobs(TodoService todoService, MessageService messageService) {
        this.todoService = todoService;
        this.messageService = messageService;
    }

    /** WB-TODO-R04：审批待办与审批流对账（防事件丢失） */
    @ErpJob(code = "WB_TODO_RECONCILE", name = "待办与审批流对账", cron = "0 0 2 * * ?")
    public String reconcileTodos() {
        return "修正 " + todoService.reconcile() + " 条";
    }

    /** WB-TODO-R05 / WB-MSG-R03：清理超过保留天数的已处理待办和消息 */
    @ErpJob(code = "WB_CLEANUP", name = "清理过期待办与消息", cron = "0 30 2 * * ?")
    public String cleanup() {
        return "删除待办 " + todoService.cleanup() + " 条、消息 " + messageService.cleanup() + " 条";
    }
}
