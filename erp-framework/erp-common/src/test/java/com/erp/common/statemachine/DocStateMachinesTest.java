package com.erp.common.statemachine;

import com.erp.common.enums.DocAction;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocStateMachinesTest {

    private final StateMachine<DocStatus, DocAction> sm = DocStateMachines.STANDARD;

    @Test
    void normalLifecycle() {
        DocStatus s = DocStatus.DRAFT;
        s = sm.fire(s, DocAction.SUBMIT);
        assertThat(s).isEqualTo(DocStatus.PENDING_APPROVAL);
        s = sm.fire(s, DocAction.APPROVE);
        assertThat(s).isEqualTo(DocStatus.APPROVED);
        s = sm.fire(s, DocAction.START);
        s = sm.fire(s, DocAction.COMPLETE);
        assertThat(s).isEqualTo(DocStatus.COMPLETED);
    }

    @Test
    void illegalTransitionThrowsWithChineseLabels() {
        assertThatThrownBy(() -> sm.fire(DocStatus.VOIDED, DocAction.APPROVE))
                .isInstanceOf(BizException.class)
                .hasMessage("当前状态【已作废】不允许执行【审核】操作");
    }

    @Test
    void allowedActionsForDraft() {
        assertThat(sm.allowedActions(DocStatus.DRAFT)).containsExactlyInAnyOrder(DocAction.SUBMIT, DocAction.VOID);
        assertThat(sm.allowedActions(DocStatus.COMPLETED)).isEmpty();
    }

    @Test
    void conflictingDefinitionRejected() {
        var builder = StateMachine.builder(DocStatus.class, DocAction.class)
                .transition(DocStatus.DRAFT, DocAction.SUBMIT, DocStatus.APPROVED);
        assertThatThrownBy(() -> builder.transition(DocStatus.DRAFT, DocAction.SUBMIT, DocStatus.CLOSED))
                .isInstanceOf(IllegalStateException.class);
    }
}
