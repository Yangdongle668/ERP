package com.erp.module.fx.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fx_quote")
public class FxQuoteDO extends BaseDO {

    private String pair;
    private BigDecimal rate;
    private LocalDate quoteDate;
    private LocalDateTime publishTime;
    private LocalDateTime fetchedAt;
    private String source;
}
