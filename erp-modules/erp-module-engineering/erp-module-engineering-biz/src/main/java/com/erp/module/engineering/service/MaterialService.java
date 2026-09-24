package com.erp.module.engineering.service;

import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialStatusChangedEvent;
import com.erp.module.engineering.config.EngineeringModuleConfig;
import com.erp.module.engineering.controller.vo.MaterialPageReqVO;
import com.erp.module.engineering.controller.vo.MaterialRespVO;
import com.erp.module.engineering.controller.vo.MaterialSaveReqVO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.system.api.coderule.CodeRuleApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 物料服务。规则见需求文档 05 第 6.1 节：
 * <ul>
 *   <li>编码全局唯一，启用后不可修改；</li>
 *   <li>启用后基本单位、物料类型不可修改（已可能产生库存和单据）；</li>
 *   <li>只有草稿可删除，启用后只能停用。</li>
 * </ul>
 */
@Service
public class MaterialService {

    enum Action implements StateMachine.Labeled {
        ENABLE("启用"), DISABLE("停用");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    static final StateMachine<MaterialStatus, Action> STATE_MACHINE =
            StateMachine.builder(MaterialStatus.class, Action.class)
                    .transition(MaterialStatus.DRAFT, Action.ENABLE, MaterialStatus.ENABLED)
                    .transition(MaterialStatus.ENABLED, Action.DISABLE, MaterialStatus.DISABLED)
                    .transition(MaterialStatus.DISABLED, Action.ENABLE, MaterialStatus.ENABLED)
                    .build();

    private final MaterialMapper materialMapper;
    private final CodeRuleApi codeRuleApi;
    private final DomainEventPublisher eventPublisher;

    public MaterialService(MaterialMapper materialMapper, CodeRuleApi codeRuleApi, DomainEventPublisher eventPublisher) {
        this.materialMapper = materialMapper;
        this.codeRuleApi = codeRuleApi;
        this.eventPublisher = eventPublisher;
    }

    public PageResult<MaterialRespVO> page(MaterialPageReqVO req) {
        return materialMapper.selectPage(req).map(MaterialConvert::toResp);
    }

    /** 选择器远程搜索（登录即可） */
    public List<MaterialRespVO> search(String keyword, String types, MaterialStatus status, List<Long> ids, int limit) {
        return materialMapper.search(keyword, types, status, ids, limit).stream().map(MaterialConvert::toResp).toList();
    }

    /** 按编码精确查询（明细行输入编码回车）；不存在或未启用时返回 null */
    public MaterialRespVO getEnabledByCode(String code) {
        MaterialDO m = materialMapper.selectByCode(code == null ? null : code.trim().toUpperCase());
        return m == null || m.getStatus() != MaterialStatus.ENABLED ? null : MaterialConvert.toResp(m);
    }

    public MaterialRespVO get(Long id) {
        return MaterialConvert.toResp(getOrThrow(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MaterialSaveReqVO req) {
        String code = StringUtils.hasText(req.code())
                ? req.code().trim()
                : codeRuleApi.nextCode(EngineeringModuleConfig.CODE_RULE_MATERIAL);
        assertCodeUnique(code, null);

        MaterialDO material = new MaterialDO();
        material.setCode(code);
        fillEditableFields(material, req);
        material.setMaterialType(req.materialType());
        material.setBaseUom(req.baseUom().trim());
        material.setStatus(MaterialStatus.DRAFT);
        // 数据库唯一索引兜底并发重复（由全局异常处理器转换为友好提示）
        materialMapper.insert(material);
        return material.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MaterialSaveReqVO req) {
        MaterialDO material = getOrThrow(id);
        if (req.version() != null) {
            material.setVersion(req.version());
        }
        if (material.getStatus() == MaterialStatus.DRAFT) {
            if (StringUtils.hasText(req.code()) && !req.code().trim().equals(material.getCode())) {
                assertCodeUnique(req.code().trim(), id);
                material.setCode(req.code().trim());
            }
            material.setMaterialType(req.materialType());
            material.setBaseUom(req.baseUom().trim());
        } else if (changesLockedFields(material, req)) {
            throw BizException.of(EngineeringErrorCodes.MATERIAL_NOT_EDITABLE, material.getCode());
        }
        fillEditableFields(material, req);
        materialMapper.updateByIdOrFail(material);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        changeStatus(id, Action.ENABLE);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        changeStatus(id, Action.DISABLE);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MaterialDO material = getOrThrow(id);
        if (material.getStatus() != MaterialStatus.DRAFT) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_NOT_DELETABLE);
        }
        materialMapper.deleteById(id);
    }

    MaterialDO getOrThrow(Long id) {
        MaterialDO material = materialMapper.selectById(id);
        if (material == null) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_NOT_EXISTS);
        }
        return material;
    }

    private void changeStatus(Long id, Action action) {
        MaterialDO material = getOrThrow(id);
        MaterialStatus old = material.getStatus();
        material.setStatus(STATE_MACHINE.fire(old, action));
        materialMapper.updateByIdOrFail(material);
        eventPublisher.publish(new MaterialStatusChangedEvent(material.getId(), material.getCode(), old, material.getStatus()));
    }

    private void assertCodeUnique(String code, Long excludeId) {
        MaterialDO exists = materialMapper.selectByCode(code);
        if (exists != null && !exists.getId().equals(excludeId)) {
            throw BizException.of(EngineeringErrorCodes.MATERIAL_CODE_DUPLICATE, code);
        }
    }

    private static boolean changesLockedFields(MaterialDO material, MaterialSaveReqVO req) {
        return (StringUtils.hasText(req.code()) && !req.code().trim().equals(material.getCode()))
                || req.materialType() != material.getMaterialType()
                || !Objects.equals(req.baseUom().trim(), material.getBaseUom());
    }

    private static void fillEditableFields(MaterialDO material, MaterialSaveReqVO req) {
        material.setName(req.name().trim());
        material.setNameEn(req.nameEn());
        material.setSpec(req.spec());
        material.setCategoryId(req.categoryId());
        material.setRemark(req.remark());
    }
}
