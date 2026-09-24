package com.erp.module.system.service.print;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.module.ErpModule;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.print.PrintBizDefinition;
import com.erp.module.system.controller.vo.PrintVOs.Available;
import com.erp.module.system.controller.vo.PrintVOs.BizResp;
import com.erp.module.system.controller.vo.PrintVOs.ForPrint;
import com.erp.module.system.controller.vo.PrintVOs.PrintCount;
import com.erp.module.system.controller.vo.PrintVOs.TemplateBrief;
import com.erp.module.system.controller.vo.PrintVOs.TemplateDetail;
import com.erp.module.system.controller.vo.PrintVOs.TemplateQuery;
import com.erp.module.system.controller.vo.PrintVOs.TemplateResp;
import com.erp.module.system.controller.vo.PrintVOs.TemplateSave;
import com.erp.module.system.dal.dataobject.PrintBizDO;
import com.erp.module.system.dal.dataobject.PrintLogDO;
import com.erp.module.system.dal.dataobject.PrintTemplateDO;
import com.erp.module.system.dal.mapper.PrintBizMapper;
import com.erp.module.system.dal.mapper.PrintLogMapper;
import com.erp.module.system.dal.mapper.PrintTemplateMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsError;
import com.github.jknack.handlebars.HandlebarsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 打印模板（需求 01-系统管理/09）：单据类型同步与内置模板导入（R05）、模板维护、默认模板（R01）、保存校验（R02）、打印记录。
 * 模板在浏览器端用 Handlebars 渲染（数据来自业务模块的打印数据接口），这里只做语法与安全校验。
 */
@Slf4j
@Service
public class PrintService {

    public static final List<String> LANGUAGES = List.of("zh-CN", "en");
    private static final int MAX_BYTES = 200 * 1024;
    /** 禁止脚本：script/iframe/object/embed 标签、on* 事件属性、javascript: 链接 */
    private static final Pattern SCRIPT = Pattern.compile(
            "(?i)<\\s*(script|iframe|object|embed)\\b|\\son[a-z]+\\s*=|javascript\\s*:");

    private final PrintBizMapper bizMapper;
    private final PrintTemplateMapper templateMapper;
    private final PrintLogMapper logMapper;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final Map<String, String> moduleNames;

    public PrintService(PrintBizMapper bizMapper, PrintTemplateMapper templateMapper, PrintLogMapper logMapper, ResourceLoader resourceLoader,
                        ObjectMapper objectMapper, List<ErpModule> modules) {
        this.bizMapper = bizMapper;
        this.templateMapper = templateMapper;
        this.logMapper = logMapper;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.moduleNames = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::name, (a, b) -> a));
    }

    // ==================== 声明同步（R05） ====================

    @Transactional
    public void sync(List<PrintBizDefinition> defs) {
        Map<String, PrintBizDefinition> byType = new LinkedHashMap<>();
        for (PrintBizDefinition d : defs) {
            PrintBizDefinition old = byType.put(d.bizType(), d);
            if (old != null) throw new IllegalStateException("打印单据类型重复声明: " + d.bizType() + "（" + old.moduleCode() + "、" + d.moduleCode() + "）");
        }
        Map<String, PrintBizDO> existing = bizMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(PrintBizDO::getBizType, b -> b));
        for (PrintBizDefinition d : byType.values()) {
            PrintBizDO b = existing.getOrDefault(d.bizType(), new PrintBizDO());
            b.setBizType(d.bizType());
            b.setName(d.name());
            b.setModuleCode(d.moduleCode());
            b.setDataApi(d.dataApi());
            b.setVariables(json(d.variables()));
            b.setSampleData(d.sampleData());
            b.setActive(true);
            if (b.getId() == null) bizMapper.insert(b);
            else bizMapper.updateByIdOrFail(b);
            for (String lang : LANGUAGES) importBuiltin(d, lang);
        }
        for (PrintBizDO b : existing.values()) {
            if (!byType.containsKey(b.getBizType()) && Boolean.TRUE.equals(b.getActive())) {
                b.setActive(false);
                bizMapper.updateByIdOrFail(b);
            }
        }
    }

    /** 模块资源 print-templates/<bizType>-<language>.html 存在且还没有该语言的内置模板时导入 */
    private void importBuiltin(PrintBizDefinition d, String lang) {
        Resource r = resourceLoader.getResource("classpath:print-templates/" + d.bizType() + "-" + lang + ".html");
        if (!r.exists()) return;
        boolean has = templateMapper.selectCount(new LambdaQueryWrapper<PrintTemplateDO>().eq(PrintTemplateDO::getBizType, d.bizType())
                .eq(PrintTemplateDO::getLanguage, lang).eq(PrintTemplateDO::getIsBuiltin, true)) > 0;
        if (has) return;
        try (InputStream in = r.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            boolean hasDefault = templateMapper.selectCount(new LambdaQueryWrapper<PrintTemplateDO>().eq(PrintTemplateDO::getBizType, d.bizType())
                    .eq(PrintTemplateDO::getLanguage, lang).eq(PrintTemplateDO::getIsDefault, true)) > 0;
            PrintTemplateDO t = new PrintTemplateDO();
            t.setBizType(d.bizType());
            t.setName("en".equals(lang) ? d.name() + " (Standard)" : d.name() + "（标准）");
            t.setLanguage(lang);
            t.setPaper("A4_P");
            t.setMargin("10mm 10mm 10mm 10mm");
            t.setContent(content);
            t.setIsDefault(!hasDefault);
            t.setIsBuiltin(true);
            t.setStatus(EnableStatus.ENABLED);
            templateMapper.insert(t);
            log.info("[打印模板] 导入内置模板 {}-{}", d.bizType(), lang);
        } catch (IOException e) {
            log.warn("[打印模板] 读取内置模板失败 {}", r, e);
        }
    }

    // ==================== 查询 ====================

    public PageResult<TemplateResp> page(TemplateQuery q) {
        Page<PrintTemplateDO> page = templateMapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), new LambdaQueryWrapper<PrintTemplateDO>()
                .select(PrintTemplateDO.class, f -> !"content".equals(f.getColumn()))
                .eq(StringUtils.hasText(q.getBizType()), PrintTemplateDO::getBizType, q.getBizType())
                .eq(StringUtils.hasText(q.getLanguage()), PrintTemplateDO::getLanguage, q.getLanguage())
                .eq(StringUtils.hasText(q.getStatus()), PrintTemplateDO::getStatus, StringUtils.hasText(q.getStatus()) ? EnableStatus.valueOf(q.getStatus()) : null)
                .orderByAsc(PrintTemplateDO::getBizType).orderByAsc(PrintTemplateDO::getLanguage)
                .orderByDesc(PrintTemplateDO::getIsDefault).orderByAsc(PrintTemplateDO::getId));
        Map<String, String> names = bizNames();
        return new PageResult<>(page.getRecords().stream().map(t -> new TemplateResp(t.getId(), t.getBizType(), names.getOrDefault(t.getBizType(), t.getBizType()),
                t.getName(), t.getLanguage(), t.getPaper(), t.getPaperWidth(), t.getPaperHeight(), t.getMargin(), Boolean.TRUE.equals(t.getIsDefault()),
                Boolean.TRUE.equals(t.getIsBuiltin()), t.getStatus().name(), t.getRemark(), t.getUpdatedAt(), t.getVersion())).toList(), page.getTotal());
    }

    public TemplateDetail get(Long id) {
        PrintTemplateDO t = getTemplate(id);
        return new TemplateDetail(t.getId(), t.getBizType(), bizNames().getOrDefault(t.getBizType(), t.getBizType()), t.getName(), t.getLanguage(),
                t.getPaper(), t.getPaperWidth(), t.getPaperHeight(), t.getMargin(), t.getContent(), Boolean.TRUE.equals(t.getIsDefault()),
                Boolean.TRUE.equals(t.getIsBuiltin()), t.getStatus().name(), t.getRemark(), t.getVersion());
    }

    public List<BizResp> bizList() {
        return bizMapper.selectList(new LambdaQueryWrapper<PrintBizDO>().eq(PrintBizDO::getActive, true)
                        .orderByAsc(PrintBizDO::getModuleCode).orderByAsc(PrintBizDO::getBizType))
                .stream().map(b -> toBiz(b, false)).toList();
    }

    public BizResp biz(String bizType) {
        return toBiz(getBiz(bizType), true);
    }

    /** 打印按钮：启用模板，默认模板在前（R03 在前端提示） */
    public Available available(String bizType) {
        PrintBizDO b = getBiz(bizType);
        List<TemplateBrief> list = templateMapper.selectList(new LambdaQueryWrapper<PrintTemplateDO>()
                        .select(PrintTemplateDO.class, f -> !"content".equals(f.getColumn()))
                        .eq(PrintTemplateDO::getBizType, bizType).eq(PrintTemplateDO::getStatus, EnableStatus.ENABLED))
                .stream().sorted(Comparator.comparing((PrintTemplateDO t) -> !Boolean.TRUE.equals(t.getIsDefault())).thenComparing(PrintTemplateDO::getLanguage)
                        .thenComparing(PrintTemplateDO::getId))
                .map(t -> new TemplateBrief(t.getId(), t.getName(), t.getLanguage(), Boolean.TRUE.equals(t.getIsDefault()), t.getPaper())).toList();
        return new Available(b.getBizType(), b.getName(), b.getDataApi(), list);
    }

    public ForPrint forPrint(Long id) {
        PrintTemplateDO t = getTemplate(id);
        if (t.getStatus() != EnableStatus.ENABLED) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_DISABLED);
        return new ForPrint(t.getId(), t.getName(), t.getLanguage(), t.getPaper(), t.getPaperWidth(), t.getPaperHeight(), t.getMargin(), t.getContent());
    }

    // ==================== 维护 ====================

    @Transactional
    public Long create(TemplateSave req) {
        getBiz(req.bizType());
        validate(req.content());
        PrintTemplateDO t = new PrintTemplateDO();
        apply(t, req);
        boolean hasDefault = templateMapper.selectCount(new LambdaQueryWrapper<PrintTemplateDO>().eq(PrintTemplateDO::getBizType, req.bizType())
                .eq(PrintTemplateDO::getLanguage, req.language()).eq(PrintTemplateDO::getIsDefault, true)) > 0;
        t.setIsDefault(!hasDefault);
        t.setIsBuiltin(false);
        t.setStatus(EnableStatus.ENABLED);
        templateMapper.insert(t);
        return t.getId();
    }

    @Transactional
    public void update(Long id, TemplateSave req) {
        PrintTemplateDO t = getTemplate(id);
        if (Boolean.TRUE.equals(t.getIsBuiltin())) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_BUILTIN);
        validate(req.content());
        boolean moved = !t.getBizType().equals(req.bizType()) || !t.getLanguage().equals(req.language());
        if (moved && Boolean.TRUE.equals(t.getIsDefault())) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_DEFAULT);
        getBiz(req.bizType());
        apply(t, req);
        t.setVersion(req.version() == null ? t.getVersion() : req.version());
        templateMapper.updateByIdOrFail(t);
    }

    /** 复制为新模板：名称加“-副本”，非内置、非默认 */
    @Transactional
    public Long copy(Long id) {
        PrintTemplateDO src = getTemplate(id);
        PrintTemplateDO t = new PrintTemplateDO();
        t.setBizType(src.getBizType());
        String name = src.getName() + "-副本";
        t.setName(name.length() > 64 ? name.substring(0, 64) : name);
        t.setLanguage(src.getLanguage());
        t.setPaper(src.getPaper());
        t.setPaperWidth(src.getPaperWidth());
        t.setPaperHeight(src.getPaperHeight());
        t.setMargin(src.getMargin());
        t.setContent(src.getContent());
        t.setIsDefault(false);
        t.setIsBuiltin(false);
        t.setStatus(EnableStatus.ENABLED);
        t.setRemark(src.getRemark());
        templateMapper.insert(t);
        return t.getId();
    }

    /** R01：同一单据类型 + 语言只有一个默认模板 */
    @Transactional
    public void setDefault(Long id) {
        PrintTemplateDO t = getTemplate(id);
        if (t.getStatus() != EnableStatus.ENABLED) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_DISABLED);
        templateMapper.update(null, new LambdaUpdateWrapper<PrintTemplateDO>().eq(PrintTemplateDO::getBizType, t.getBizType())
                .eq(PrintTemplateDO::getLanguage, t.getLanguage()).ne(PrintTemplateDO::getId, id)
                .set(PrintTemplateDO::getIsDefault, false).set(PrintTemplateDO::getUpdatedAt, LocalDateTime.now()));
        t.setIsDefault(true);
        templateMapper.updateByIdOrFail(t);
    }

    @Transactional
    public void setStatus(Long id, boolean enabled) {
        PrintTemplateDO t = getTemplate(id);
        if (!enabled && Boolean.TRUE.equals(t.getIsDefault())) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_DEFAULT);
        t.setStatus(enabled ? EnableStatus.ENABLED : EnableStatus.DISABLED);
        templateMapper.updateByIdOrFail(t);
    }

    @Transactional
    public void delete(Long id) {
        PrintTemplateDO t = getTemplate(id);
        if (Boolean.TRUE.equals(t.getIsBuiltin())) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_BUILTIN);
        if (Boolean.TRUE.equals(t.getIsDefault())) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_DEFAULT);
        templateMapper.deleteById(id);
    }

    /** R02：大小、脚本、模板语法（行号） */
    public void validate(String content) {
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_TOO_LARGE);
        if (SCRIPT.matcher(content).find()) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_SCRIPT);
        try {
            // 帮助函数（formatDate、barcode…）在前端注册；这里只校验语法，未知帮助函数一律视为存在
            new Handlebars().registerHelperMissing((ctx, options) -> "").compileInline(content);
        } catch (HandlebarsException e) {
            HandlebarsError err = e.getError();
            String reason = err == null ? e.getMessage() : "第 " + err.line + " 行：" + err.reason;
            throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_RENDER, reason);
        } catch (IOException | RuntimeException e) {
            throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_RENDER, e.getMessage());
        }
    }

    // ==================== 打印记录 ====================

    public void log(String bizType, List<Long> bizIds, Long templateId) {
        Long me = SecurityUtils.getLoginUserIdOrNull();
        LocalDateTime now = LocalDateTime.now();
        for (Long id : bizIds) {
            PrintLogDO l = new PrintLogDO();
            l.setBizType(bizType);
            l.setBizId(id);
            l.setTemplateId(templateId);
            l.setPrintedBy(me);
            l.setPrintedAt(now);
            logMapper.insert(l);
        }
    }

    /** 已打印次数（单据页显示“已打印 N 次”） */
    public List<PrintCount> counts(String bizType, List<Long> bizIds) {
        if (bizIds.isEmpty()) return List.of();
        Map<Long, Long> counts = logMapper.selectList(new LambdaQueryWrapper<PrintLogDO>().select(PrintLogDO::getBizId)
                        .eq(PrintLogDO::getBizType, bizType).in(PrintLogDO::getBizId, bizIds))
                .stream().collect(Collectors.groupingBy(PrintLogDO::getBizId, Collectors.counting()));
        return bizIds.stream().map(id -> new PrintCount(id, counts.getOrDefault(id, 0L))).toList();
    }

    // ==================== 工具 ====================

    private void apply(PrintTemplateDO t, TemplateSave req) {
        t.setBizType(req.bizType());
        t.setName(req.name().trim());
        t.setLanguage(req.language());
        t.setPaper(req.paper());
        boolean custom = "CUSTOM".equals(req.paper());
        t.setPaperWidth(custom ? req.paperWidth() : null);
        t.setPaperHeight(custom ? req.paperHeight() : null);
        t.setMargin(req.margin().trim());
        t.setContent(req.content());
        t.setRemark(StringUtils.hasText(req.remark()) ? req.remark().trim() : null);
    }

    private PrintTemplateDO getTemplate(Long id) {
        PrintTemplateDO t = id == null ? null : templateMapper.selectById(id);
        if (t == null) throw BizException.of(SystemErrorCodes.PRINT_TEMPLATE_NOT_EXISTS);
        return t;
    }

    private PrintBizDO getBiz(String bizType) {
        return Optional.ofNullable(bizMapper.selectOne(new LambdaQueryWrapper<PrintBizDO>().eq(PrintBizDO::getBizType, bizType)
                .eq(PrintBizDO::getActive, true))).orElseThrow(() -> BizException.of(SystemErrorCodes.PRINT_BIZ_NOT_EXISTS, bizType));
    }

    private Map<String, String> bizNames() {
        return bizMapper.selectList(new LambdaQueryWrapper<>()).stream().collect(Collectors.toMap(PrintBizDO::getBizType, PrintBizDO::getName));
    }

    private BizResp toBiz(PrintBizDO b, boolean withSample) {
        return new BizResp(b.getBizType(), b.getName(), b.getModuleCode(), moduleNames.getOrDefault(b.getModuleCode(), b.getModuleCode()),
                b.getDataApi(), tree(b.getVariables()), withSample ? tree(b.getSampleData()) : null);
    }

    private JsonNode tree(String json) {
        if (json == null || json.isBlank()) return JsonNodeFactory.instance.nullNode();
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return JsonNodeFactory.instance.nullNode();
        }
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
