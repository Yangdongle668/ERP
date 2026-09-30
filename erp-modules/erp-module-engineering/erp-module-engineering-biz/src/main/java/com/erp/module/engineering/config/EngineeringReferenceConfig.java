package com.erp.module.engineering.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.crm.api.customer.CustomerReferenceChecker;
import com.erp.module.engineering.dal.dataobject.EcnDO;
import com.erp.module.engineering.dal.dataobject.ProjectDO;
import com.erp.module.engineering.dal.dataobject.SampleDO;
import com.erp.module.engineering.dal.dataobject.ToolingDO;
import com.erp.module.engineering.dal.dataobject.WorkCenterDO;
import com.erp.module.engineering.dal.mapper.EcnMapper;
import com.erp.module.engineering.dal.mapper.ProjectMapper;
import com.erp.module.engineering.dal.mapper.SampleMapper;
import com.erp.module.engineering.dal.mapper.ToolingMapper;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 研发工程对其他模块数据的引用检查：客户（删除）、组织（删除） */
@Configuration
public class EngineeringReferenceConfig {

    /** CRM R09：有样品、研发项目、客供工装、ECN 的客户不能删除 */
    @Bean
    public CustomerReferenceChecker engineeringCustomerChecker(SampleMapper sampleMapper, ProjectMapper projectMapper, ToolingMapper toolingMapper,
                                                               EcnMapper ecnMapper) {
        return customerId -> sampleMapper.exists(new LambdaQueryWrapper<SampleDO>().eq(SampleDO::getCustomerId, customerId))
                || projectMapper.exists(new LambdaQueryWrapper<ProjectDO>().eq(ProjectDO::getCustomerId, customerId))
                || toolingMapper.exists(new LambdaQueryWrapper<ToolingDO>().eq(ToolingDO::getCustomerId, customerId))
                || ecnMapper.exists(new LambdaQueryWrapper<EcnDO>().eq(EcnDO::getCustomerId, customerId));
    }

    /** SYS-ORG-R07：研发单据与工作中心（所属车间）的公司 / 部门 */
    @Bean
    public OrgReferenceChecker engineeringOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.engineering.", WorkCenterDO.class)::isReferenced;
    }
}
