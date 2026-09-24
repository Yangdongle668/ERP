package com.erp.it.system;

import com.erp.it.AbstractIntegrationTest;

import com.erp.module.system.api.coderule.CodeRuleApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class CodeRuleConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private CodeRuleApi codeRuleApi;

    @Test
    void concurrentGenerationNeverDuplicates() throws Exception {
        int threads = 8;
        int perThread = 25;
        Set<String> codes = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        codes.add(codeRuleApi.nextCode("MATERIAL"));
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(codes).hasSize(threads * perThread);
    }
}
