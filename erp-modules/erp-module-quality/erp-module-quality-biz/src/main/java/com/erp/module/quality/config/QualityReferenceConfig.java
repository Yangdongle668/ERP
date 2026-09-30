package com.erp.module.quality.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.crm.api.customer.CustomerReferenceChecker;
import com.erp.module.engineering.api.material.MaterialReferenceChecker;
import com.erp.module.engineering.api.material.MaterialUsage;
import com.erp.module.quality.dal.dataobject.QcCapaDO;
import com.erp.module.quality.dal.dataobject.QcComplaintDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcNcrDO;
import com.erp.module.quality.dal.dataobject.QcScarDO;
import com.erp.module.quality.dal.dataobject.QcStandardDO;
import com.erp.module.quality.dal.mapper.QcCapaMapper;
import com.erp.module.quality.dal.mapper.QcComplaintMapper;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.dal.mapper.QcNcrMapper;
import com.erp.module.quality.dal.mapper.QcScarMapper;
import com.erp.module.quality.dal.mapper.QcStandardMapper;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/** 品质对其他模块数据的引用检查：客户（删除）、物料（停用 / 删除）、组织（删除） */
@Configuration
public class QualityReferenceConfig {

    static final List<DocStatus> FINISHED = List.of(DocStatus.COMPLETED, DocStatus.CLOSED, DocStatus.VOIDED);

    /** CRM R09：有客诉、NCR、CAPA、检验记录的客户不能删除 */
    @Bean
    public CustomerReferenceChecker qualityCustomerChecker(QcComplaintMapper complaintMapper, QcNcrMapper ncrMapper, QcCapaMapper capaMapper,
                                                           QcInspectionMapper inspectionMapper) {
        return customerId -> complaintMapper.exists(new LambdaQueryWrapper<QcComplaintDO>().eq(QcComplaintDO::getCustomerId, customerId))
                || ncrMapper.exists(new LambdaQueryWrapper<QcNcrDO>().eq(QcNcrDO::getCustomerId, customerId))
                || capaMapper.exists(new LambdaQueryWrapper<QcCapaDO>().eq(QcCapaDO::getCustomerId, customerId))
                || inspectionMapper.exists(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getCustomerId, customerId));
    }

    /** ENG-MAT-R08 / R09：有检验标准或品质单据的物料；未完成的检验单计入未完成单据 */
    @Bean
    public MaterialReferenceChecker qualityMaterialChecker(QcInspectionMapper inspectionMapper, QcStandardMapper standardMapper, QcNcrMapper ncrMapper,
                                                           QcComplaintMapper complaintMapper, QcScarMapper scarMapper, QcCapaMapper capaMapper) {
        return materialId -> {
            int open = Math.toIntExact(inspectionMapper.selectCount(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getMaterialId, materialId)
                    .notIn(QcInspectionDO::getStatus, FINISHED)));
            boolean used = open > 0
                    || inspectionMapper.exists(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getMaterialId, materialId))
                    || standardMapper.exists(new LambdaQueryWrapper<QcStandardDO>().eq(QcStandardDO::getMaterialId, materialId))
                    || ncrMapper.exists(new LambdaQueryWrapper<QcNcrDO>().eq(QcNcrDO::getMaterialId, materialId))
                    || complaintMapper.exists(new LambdaQueryWrapper<QcComplaintDO>().eq(QcComplaintDO::getMaterialId, materialId))
                    || scarMapper.exists(new LambdaQueryWrapper<QcScarDO>().eq(QcScarDO::getMaterialId, materialId))
                    || capaMapper.exists(new LambdaQueryWrapper<QcCapaDO>().eq(QcCapaDO::getMaterialId, materialId));
            return used ? new MaterialUsage(BigDecimal.ZERO, open, true, false) : MaterialUsage.NONE;
        };
    }

    /** SYS-ORG-R07：品质单据的公司 / 部门 */
    @Bean
    public OrgReferenceChecker qualityOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.quality.")::isReferenced;
    }
}
