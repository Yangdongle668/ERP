package com.erp.module.system.api.dict;

/**
 * 扩展点：删除字典项前检查是否被业务数据使用（SYS-DIC-R05）。由声明该字典的模块实现；
 * 没有任何实现声明支持某个字典类型时，该字典的项视为已被使用，只能停用。
 */
public interface DictReferenceChecker {

    /** 本检查器负责的字典类型 */
    boolean supports(String typeCode);

    boolean isReferenced(String typeCode, String value);
}
