package com.erp.module.system.api.param;

import java.math.BigDecimal;
import java.util.List;

/** 系统参数读取（带缓存，保存后立即清除，SYS-PAR-R03）。参数未声明时抛出异常。 */
public interface ParamApi {

    String getString(String key);

    int getInt(String key);

    BigDecimal getDecimal(String key);

    boolean getBool(String key);

    /** USER_LIST 类型：用户 ID 列表 */
    List<Long> getUserIds(String key);
}
