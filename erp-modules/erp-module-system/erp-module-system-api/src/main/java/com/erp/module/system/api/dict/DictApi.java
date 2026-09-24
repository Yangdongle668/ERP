package com.erp.module.system.api.dict;

import java.util.List;

/** 数据字典（带缓存）。 */
public interface DictApi {

    /** 某类型的启用项，按排序 */
    List<DictItemDTO> getItems(String typeCode);

    /** 值存在且启用 */
    boolean isValid(String typeCode, String value);

    /**
     * 业务保存时校验字典值（SYS-DIC-R07）：值为空直接通过；值不存在或已停用时抛出“{字段名}的值「x」无效”。
     * 修改单据且值未变化时，调用方可跳过校验（允许保留已停用的值）。
     */
    void validate(String typeCode, String value, String fieldName);

    /** 标签；值不存在时返回值本身，值为空返回空字符串 */
    String label(String typeCode, String value);
}
