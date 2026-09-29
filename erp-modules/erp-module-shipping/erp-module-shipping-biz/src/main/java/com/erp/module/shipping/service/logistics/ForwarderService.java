package com.erp.module.shipping.service.logistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.shipping.api.ShippingErrorCodes;
import com.erp.module.shipping.controller.vo.ForwarderVOs.ForwarderOption;
import com.erp.module.shipping.controller.vo.ForwarderVOs.ForwarderQuery;
import com.erp.module.shipping.controller.vo.ForwarderVOs.ForwarderRow;
import com.erp.module.shipping.controller.vo.ForwarderVOs.ForwarderSave;
import com.erp.module.shipping.dal.dataobject.ShpForwarderDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.mapper.ShpForwarderMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.shipping.service.ShpSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/** 货代资料（11-05） */
@Service
public class ForwarderService {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private final ShpForwarderMapper mapper;
    private final ShpNoticeMapper noticeMapper;
    private final ShpShipmentMapper shipmentMapper;

    public ForwarderService(ShpForwarderMapper mapper, ShpNoticeMapper noticeMapper, ShpShipmentMapper shipmentMapper) {
        this.mapper = mapper;
        this.noticeMapper = noticeMapper;
        this.shipmentMapper = shipmentMapper;
    }

    public PageResult<ForwarderRow> page(ForwarderQuery q) {
        String kw = ShpSupport.trim(q.getKeyword());
        IPage<ShpForwarderDO> p = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), new LambdaQueryWrapper<ShpForwarderDO>()
                .and(kw != null, w -> w.like(ShpForwarderDO::getCode, kw).or().like(ShpForwarderDO::getName, kw).or().like(ShpForwarderDO::getContact, kw))
                .eq(StringUtils.hasText(q.getStatus()), ShpForwarderDO::getForwarderStatus, q.getStatus())
                .like(StringUtils.hasText(q.getService()), ShpForwarderDO::getServices, q.getService())
                .orderByAsc(ShpForwarderDO::getCode));
        return new PageResult<>(p.getRecords().stream().map(ForwarderService::row).toList(), p.getTotal());
    }

    static ForwarderRow row(ShpForwarderDO f) {
        List<String> services = StringUtils.hasText(f.getServices()) ? Arrays.stream(f.getServices().split(",")).map(String::trim).filter(StringUtils::hasText).toList()
                : List.of();
        return new ForwarderRow(f.getId(), f.getCode(), f.getName(), f.getContact(), f.getPhone(), f.getEmail(), services, f.getForwarderStatus(), f.getRemark(),
                f.getCreatedAt());
    }

    public ForwarderRow get(Long id) {
        return row(getDO(id));
    }

    private ShpForwarderDO getDO(Long id) {
        ShpForwarderDO f = id == null ? null : mapper.selectById(id);
        if (f == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "货代");
        return f;
    }

    /** 启用的货代（单据下拉） */
    public List<ForwarderOption> options() {
        return mapper.selectList(new LambdaQueryWrapper<ShpForwarderDO>().eq(ShpForwarderDO::getForwarderStatus, ENABLED).orderByAsc(ShpForwarderDO::getCode))
                .stream().map(f -> new ForwarderOption(f.getId(), f.getCode(), f.getName())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ForwarderSave req) {
        ShpForwarderDO f = new ShpForwarderDO();
        fill(f, req);
        mapper.insert(f);
        return f.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ForwarderSave req) {
        ShpForwarderDO f = getDO(id);
        fill(f, req);
        mapper.updateByIdOrFail(f);
    }

    private void fill(ShpForwarderDO f, ForwarderSave req) {
        String code = req.code().trim();
        if (mapper.selectCount(new LambdaQueryWrapper<ShpForwarderDO>().eq(ShpForwarderDO::getCode, code)
                .ne(f.getId() != null, ShpForwarderDO::getId, f.getId())) > 0) {
            throw BizException.of(ShippingErrorCodes.CODE_DUPLICATE, code);
        }
        f.setCode(ShpSupport.limit(code, 32));
        f.setName(ShpSupport.limit(req.name(), 128));
        f.setContact(ShpSupport.limit(req.contact(), 64));
        f.setPhone(ShpSupport.limit(req.phone(), 32));
        f.setEmail(ShpSupport.limit(req.email(), 128));
        f.setServices(req.services() == null || req.services().isEmpty() ? null : String.join(",", req.services()));
        f.setForwarderStatus(DISABLED.equals(req.status()) ? DISABLED : ENABLED);
        f.setRemark(ShpSupport.limit(req.remark(), 256));
    }

    /** 已被出货通知 / 出货单引用的货代只能停用 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ShpForwarderDO f = getDO(id);
        boolean used = noticeMapper.selectCount(new LambdaQueryWrapper<ShpNoticeDO>().eq(ShpNoticeDO::getForwarderId, id)) > 0
                || shipmentMapper.selectCount(new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getForwarderId, id)) > 0;
        if (used) {
            f.setForwarderStatus(DISABLED);
            mapper.updateByIdOrFail(f);
            return;
        }
        mapper.deleteById(id);
    }
}
