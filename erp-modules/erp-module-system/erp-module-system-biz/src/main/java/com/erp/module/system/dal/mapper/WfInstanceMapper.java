package com.erp.module.system.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.WfInstanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WfInstanceMapper extends BaseMapperX<WfInstanceDO> {

    /**
     * 锁定实例行直到事务结束：同一实例上的审批动作串行执行
     * （避免会签节点两人同时通过时都以为对方未处理、流程停住）。
     */
    @Update("UPDATE wf_instance SET updated_at = updated_at WHERE id = #{id}")
    int lock(@Param("id") Long id);
}
