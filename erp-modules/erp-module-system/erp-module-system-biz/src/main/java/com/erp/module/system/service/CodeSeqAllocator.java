package com.erp.module.system.service;

import com.erp.module.system.dal.mapper.CodeSeqMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流水号分配。使用独立事务（REQUIRES_NEW）：
 * <ul>
 *   <li>行锁只在分配期间持有，不会被调用方的长事务拖住，避免编号生成成为全局瓶颈；</li>
 *   <li>调用方事务回滚时流水号不回退，只会产生跳号，不会重号。</li>
 * </ul>
 */
@Component
public class CodeSeqAllocator {

    private final CodeSeqMapper codeSeqMapper;

    public CodeSeqAllocator(CodeSeqMapper codeSeqMapper) {
        this.codeSeqMapper = codeSeqMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long next(String bizCode, String resetKey) {
        if (codeSeqMapper.increment(bizCode, resetKey) == 0) {
            try {
                codeSeqMapper.insertFirst(bizCode, resetKey);
                return 1L;
            } catch (DuplicateKeyException e) {
                // 并发下另一个事务已插入首条记录，改为自增
                codeSeqMapper.increment(bizCode, resetKey);
            }
        }
        return codeSeqMapper.selectCurrent(bizCode, resetKey);
    }
}
