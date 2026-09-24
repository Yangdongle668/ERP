package com.erp.module.system.api.uom;

/** 扩展点：删除单位或修改类别前检查是否被引用（SYS-UOM-R03、R04），由研发工程等模块实现。 */
public interface UomReferenceChecker {

    boolean isReferenced(String uomCode);
}
