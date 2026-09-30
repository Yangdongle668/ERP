package com.erp.module.finance;

import com.erp.module.finance.service.receipt.PayerNames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayerNamesTest {

    @Test
    void normalizeStripsSuffixAndNoise() {
        assertThat(PayerNames.normalize("深圳市华强电子有限公司")).isEqualTo("深圳市华强电子");
        assertThat(PayerNames.normalize("ACME Trading Co., Ltd.")).isEqualTo("acmetrading");
        assertThat(PayerNames.normalize("Acme  Trading  LIMITED")).isEqualTo("acmetrading");
        assertThat(PayerNames.normalize("Globex Corp.")).isEqualTo("globex");
        assertThat(PayerNames.normalize(null)).isEmpty();
    }

    @Test
    void similarMatchesSameCompanyButNotShortOrUnrelated() {
        assertThat(PayerNames.similar("ACME TRADING CO LTD", "Acme Trading Co., Ltd.")).isTrue();
        assertThat(PayerNames.similar("深圳市华强电子", "深圳市华强电子有限公司")).isTrue();
        assertThat(PayerNames.similar("华强电子（深圳）有限公司", "华强电子")).isTrue();
        // 太短不做包含匹配
        assertThat(PayerNames.similar("上海", "上海贸易有限公司")).isFalse();
        assertThat(PayerNames.similar("Acme Trading", "Globex Trading")).isFalse();
    }
}
