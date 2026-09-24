package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.paymentterm.BaseEvent;
import com.erp.module.system.api.paymentterm.DueNode;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
import com.erp.module.system.api.paymentterm.PaymentTermDTO;
import com.erp.module.system.api.paymentterm.PaymentTermReferenceChecker;
import com.erp.module.system.controller.vo.PaymentTermVOs.NodeVO;
import com.erp.module.system.controller.vo.PaymentTermVOs.TermResp;
import com.erp.module.system.controller.vo.PaymentTermVOs.TermSave;
import com.erp.module.system.controller.vo.PaymentTermVOs.TermSimple;
import com.erp.module.system.dal.dataobject.PaymentTermDO;
import com.erp.module.system.dal.dataobject.PaymentTermNodeDO;
import com.erp.module.system.dal.mapper.PaymentTermMapper;
import com.erp.module.system.dal.mapper.PaymentTermNodeMapper;
import com.erp.module.system.enums.TermUsage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 付款条件（01-14），同时是 {@link PaymentTermApi} 的实现（到期日计算全系统统一）。 */
@Service
public class PaymentTermService implements PaymentTermApi {

    private static final Map<BaseEvent, String> EVENT_LABELS = Map.of(
            BaseEvent.ORDER_DATE, "下单日", BaseEvent.BEFORE_SHIPMENT, "出货前", BaseEvent.SHIPMENT, "出货日",
            BaseEvent.BL_DATE, "提单日", BaseEvent.INVOICE_DATE, "开票日", BaseEvent.RECEIPT_DATE, "到货日", BaseEvent.MONTH_END, "月结");

    private final PaymentTermMapper termMapper;
    private final PaymentTermNodeMapper nodeMapper;
    private final DictApi dictApi;
    private final List<PaymentTermReferenceChecker> referenceCheckers;

    public PaymentTermService(PaymentTermMapper termMapper, PaymentTermNodeMapper nodeMapper, DictApi dictApi,
                              List<PaymentTermReferenceChecker> referenceCheckers) {
        this.termMapper = termMapper;
        this.nodeMapper = nodeMapper;
        this.dictApi = dictApi;
        this.referenceCheckers = referenceCheckers;
    }

    public List<TermResp> list(String keyword, String usage, String status) {
        LambdaQueryWrapper<PaymentTermDO> w = new LambdaQueryWrapper<PaymentTermDO>()
                .eq(StringUtils.hasText(usage), PaymentTermDO::getUsage, StringUtils.hasText(usage) ? TermUsage.valueOf(usage) : null)
                .eq(StringUtils.hasText(status), PaymentTermDO::getStatus, StringUtils.hasText(status) ? EnableStatus.valueOf(status) : null)
                .and(StringUtils.hasText(keyword), x -> x.like(PaymentTermDO::getCode, keyword.trim()).or().like(PaymentTermDO::getName, keyword.trim()))
                .orderByAsc(PaymentTermDO::getCode);
        List<PaymentTermDO> terms = termMapper.selectList(w);
        Map<Long, List<PaymentTermNodeDO>> nodes = terms.isEmpty() ? Map.of() : nodeMapper.selectList(new LambdaQueryWrapper<PaymentTermNodeDO>()
                        .in(PaymentTermNodeDO::getTermId, terms.stream().map(PaymentTermDO::getId).toList()))
                .stream().collect(Collectors.groupingBy(PaymentTermNodeDO::getTermId));
        return terms.stream().map(t -> toResp(t, nodes.getOrDefault(t.getId(), List.of()))).toList();
    }

    public TermResp detail(Long id) {
        PaymentTermDO t = getTerm(id);
        return toResp(t, nodes(id));
    }

    /** 启用的付款条件；usage 为 SALES / PURCHASE 时只列适用的（含 BOTH） */
    public List<TermSimple> simple(String usage) {
        return termMapper.selectList(new LambdaQueryWrapper<PaymentTermDO>().eq(PaymentTermDO::getStatus, EnableStatus.ENABLED)
                        .orderByAsc(PaymentTermDO::getCode)).stream()
                .filter(t -> !StringUtils.hasText(usage) || t.getUsage().accepts(usage))
                .map(t -> new TermSimple(t.getId(), t.getCode(), t.getName(), t.getNameEn(), t.getUsage().name())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(TermSave req) {
        String code = req.code().trim().toUpperCase();
        if (termMapper.selectByCode(code) != null) throw BizException.of(SystemErrorCodes.PAYMENT_TERM_CODE_DUPLICATE, code);
        PaymentTermDO t = new PaymentTermDO();
        t.setCode(code);
        fill(t, req);
        t.setStatus(EnableStatus.ENABLED);
        try {
            termMapper.insert(t);
        } catch (DuplicateKeyException e) {
            throw BizException.of(SystemErrorCodes.PAYMENT_TERM_CODE_DUPLICATE, code);
        }
        saveNodes(t.getId(), req.nodes());
        return t.getId();
    }

    /** 修改：节点变化只影响之后新建的单据（单据保存付款条件快照，R03） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TermSave req) {
        PaymentTermDO t = getTerm(id);
        String code = req.code().trim().toUpperCase();
        PaymentTermDO other = termMapper.selectByCode(code);
        if (other != null && !other.getId().equals(id)) throw BizException.of(SystemErrorCodes.PAYMENT_TERM_CODE_DUPLICATE, code);
        t.setCode(code);
        fill(t, req);
        t.setVersion(req.version());
        termMapper.updateByIdOrFail(t);
        saveNodes(id, req.nodes());
    }

    private void fill(PaymentTermDO t, TermSave req) {
        dictApi.validate("sys_settlement_method", req.settlementMethod(), "结算方式");
        t.setName(req.name().trim());
        t.setNameEn(StringUtils.hasText(req.nameEn()) ? req.nameEn().trim() : null);
        t.setSettlementMethod(req.settlementMethod());
        t.setUsage(TermUsage.valueOf(req.usage()));
        t.setRemark(req.remark());
    }

    /** R02：至少一个节点，比例合计 100% */
    private void saveNodes(Long termId, List<NodeVO> nodes) {
        BigDecimal sum = nodes.stream().map(NodeVO::percent).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (nodes.isEmpty() || sum.compareTo(BigDecimal.ONE) != 0) throw new BizException(SystemErrorCodes.PAYMENT_TERM_PERCENT_SUM);
        nodeMapper.delete(new LambdaQueryWrapper<PaymentTermNodeDO>().eq(PaymentTermNodeDO::getTermId, termId));
        int seq = 1;
        for (NodeVO n : nodes) {
            PaymentTermNodeDO d = new PaymentTermNodeDO();
            d.setTermId(termId);
            d.setSeq(seq++);
            d.setName(n.name().trim());
            d.setPercent(n.percent());
            BaseEvent event = BaseEvent.valueOf(n.baseEvent());
            d.setBaseEvent(event);
            d.setDays(event == BaseEvent.BEFORE_SHIPMENT ? 0 : n.days());
            nodeMapper.insert(d);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, EnableStatus status) {
        PaymentTermDO t = getTerm(id);
        if (t.getStatus() == status) return;
        t.setStatus(status);
        termMapper.updateByIdOrFail(t);
    }

    /** R03：被客户、供应商或单据引用后不能删除，只能停用 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getTerm(id);
        if (referenceCheckers.stream().anyMatch(c -> c.isReferenced(id))) throw new BizException(SystemErrorCodes.PAYMENT_TERM_REFERENCED);
        nodeMapper.delete(new LambdaQueryWrapper<PaymentTermNodeDO>().eq(PaymentTermNodeDO::getTermId, id));
        termMapper.deleteById(id);
    }

    private List<PaymentTermNodeDO> nodes(Long termId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<PaymentTermNodeDO>().eq(PaymentTermNodeDO::getTermId, termId)
                .orderByAsc(PaymentTermNodeDO::getSeq));
    }

    private PaymentTermDO getTerm(Long id) {
        PaymentTermDO t = id == null ? null : termMapper.selectById(id);
        if (t == null) throw new BizException(SystemErrorCodes.PAYMENT_TERM_NOT_EXISTS);
        return t;
    }

    private TermResp toResp(PaymentTermDO t, List<PaymentTermNodeDO> nodes) {
        List<PaymentTermNodeDO> sorted = nodes.stream().sorted(Comparator.comparingInt(PaymentTermNodeDO::getSeq)).toList();
        List<NodeVO> vos = sorted.stream().map(n -> new NodeVO(n.getName(), n.getPercent().stripTrailingZeros(), n.getBaseEvent().name(), n.getDays())).toList();
        String summary = sorted.stream().map(n -> n.getPercent().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "% "
                + EVENT_LABELS.get(n.getBaseEvent()) + (n.getDays() > 0 ? " " + n.getDays() + " 天" : "")).collect(Collectors.joining(" / "));
        return new TermResp(t.getId(), t.getCode(), t.getName(), t.getNameEn(), t.getSettlementMethod(), t.getUsage().name(),
                t.getStatus().name(), t.getRemark(), vos, summary, t.getVersion());
    }

    // ==================== PaymentTermApi ====================

    @Override
    public Optional<PaymentTermDTO> get(Long id) {
        PaymentTermDO t = id == null ? null : termMapper.selectById(id);
        if (t == null) return Optional.empty();
        List<PaymentTermDTO.Node> nodes = nodes(id).stream()
                .map(n -> new PaymentTermDTO.Node(n.getSeq(), n.getName(), n.getPercent(), n.getBaseEvent(), n.getDays())).toList();
        return Optional.of(new PaymentTermDTO(t.getId(), t.getCode(), t.getName(), t.getNameEn(), t.getSettlementMethod(),
                t.getUsage().name(), t.getStatus() == EnableStatus.ENABLED, nodes));
    }

    @Override
    public PaymentTermDTO validate(Long id, String usage) {
        PaymentTermDTO t = get(id).filter(PaymentTermDTO::enabled).orElseThrow(() -> new BizException(SystemErrorCodes.PAYMENT_TERM_NOT_EXISTS));
        if (usage != null && !TermUsage.valueOf(t.usage()).accepts(usage)) {
            throw BizException.of(SystemErrorCodes.PAYMENT_TERM_USAGE_MISMATCH, t.name(), "SALES".equals(usage) ? "销售" : "采购");
        }
        return t;
    }

    /** 01-14 第 3 节：节点金额按 2 位小数舍入，最后一个节点消除尾差；事件未发生时到期日为空 */
    @Override
    public List<DueNode> calcDueDates(Long termId, BigDecimal amount, Map<BaseEvent, LocalDate> events) {
        PaymentTermDTO t = get(termId).orElseThrow(() -> new BizException(SystemErrorCodes.PAYMENT_TERM_NOT_EXISTS));
        List<DueNode> result = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < t.nodes().size(); i++) {
            PaymentTermDTO.Node n = t.nodes().get(i);
            boolean last = i == t.nodes().size() - 1;
            BigDecimal nodeAmount = last ? amount.subtract(allocated) : amount.multiply(n.percent()).setScale(2, RoundingMode.HALF_UP);
            allocated = allocated.add(nodeAmount);
            result.add(new DueNode(n.seq(), n.name(), n.percent(), nodeAmount, n.baseEvent(), n.days(), dueDate(n, events)));
        }
        return result;
    }

    static LocalDate dueDate(PaymentTermDTO.Node n, Map<BaseEvent, LocalDate> events) {
        if (events == null) return null;
        return switch (n.baseEvent()) {
            case MONTH_END -> {
                LocalDate base = events.getOrDefault(BaseEvent.SHIPMENT, events.get(BaseEvent.RECEIPT_DATE));
                yield base == null ? null : YearMonth.from(base).atEndOfMonth().plusDays(n.days());
            }
            case BEFORE_SHIPMENT -> events.get(BaseEvent.SHIPMENT);
            default -> {
                LocalDate base = events.get(n.baseEvent());
                yield base == null ? null : base.plusDays(n.days());
            }
        };
    }
}
