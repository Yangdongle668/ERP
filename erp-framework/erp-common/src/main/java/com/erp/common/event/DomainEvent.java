package com.erp.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类。
 *
 * <p>模块间“反向”通知（下层通知上层、同层之间）一律使用领域事件，避免模块循环依赖。
 * 事件类定义在<b>发布方</b>模块的 {@code api} 包中，监听方依赖发布方的 api 模块即可。
 *
 * <p>约定：
 * <ul>
 *   <li>事件是不可变的，只携带 ID 和必要的快照字段，不携带实体对象。</li>
 *   <li>需要与发布方同事务（如数量回写）的监听器使用 {@code @EventListener}；</li>
 *   <li>不要求同事务的（如通知、统计）使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}。</li>
 *   <li>监听器必须幂等：同一事件（eventId）处理多次结果不变。</li>
 * </ul>
 */
public abstract class DomainEvent {

    private final String eventId = UUID.randomUUID().toString();
    private final Instant occurredAt = Instant.now();

    public String getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
