package com.erp.module.system.service.print;

import com.erp.common.enums.EnableStatus;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.controller.vo.PrintVOs.PrintHeader;
import com.erp.module.system.dal.dataobject.FileDO;
import com.erp.module.system.dal.dataobject.OrgDO;
import com.erp.module.system.dal.mapper.FileMapper;
import com.erp.module.system.dal.mapper.OrgMapper;
import com.erp.module.system.enums.OrgType;
import com.erp.module.system.service.OrgService;
import com.erp.module.system.service.file.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Base64;
import java.util.Comparator;
import java.util.Objects;

/**
 * 打印抬头（需求 01-09 第 4.3 节）：单据所属公司的中英文名称、地址、电话、税号、Logo。
 * 公司：参数 orgId（单据的 org_id，可为部门）向上找到的公司 → 当前用户所属公司 → 第一个启用的顶级公司。
 * Logo 取组织架构中该公司上传的 Logo（未上传时用系统 Logo），转成 data URI（打印窗口是新开的空白页，不能带登录凭证取文件）。
 */
@Slf4j
@Service
public class PrintHeaderService {

    /** Logo 超过该大小不嵌入（避免打印页过大） */
    private static final int MAX_LOGO_BYTES = 512 * 1024;

    private final OrgService orgService;
    private final OrgMapper orgMapper;
    private final FileMapper fileMapper;
    private final FileService fileService;

    public PrintHeaderService(OrgService orgService, OrgMapper orgMapper, FileMapper fileMapper, FileService fileService) {
        this.orgService = orgService;
        this.orgMapper = orgMapper;
        this.fileMapper = fileMapper;
        this.fileService = fileService;
    }

    public PrintHeader header(Long orgId) {
        LoginUser me = SecurityUtils.getLoginUserOrNull();
        Long companyId = companyId(orgId);
        if (companyId == null && me != null) companyId = companyId(me.orgId() != null ? me.orgId() : me.deptId());
        OrgDO c = companyId == null ? null : orgMapper.selectById(companyId);
        if (c == null) {
            c = orgMapper.selectList(null).stream()
                    .filter(o -> o.getOrgType() == OrgType.COMPANY && o.getStatus() == EnableStatus.ENABLED)
                    .min(Comparator.comparing((OrgDO o) -> o.getLevel() == null ? 99 : o.getLevel())
                            .thenComparing(o -> o.getSort() == null ? 0 : o.getSort()).thenComparing(OrgDO::getId))
                    .orElse(null);
        }
        if (c == null) return new PrintHeader(null, "", "", "", "", "", "", "", null);
        return new PrintHeader(c.getId(), c.getName(), nz(c.getNameEn()), nz(c.getShortName()), nz(c.getAddress()), nz(c.getAddressEn()),
                nz(c.getPhone()), nz(c.getTaxNo()), logo(c.getLogoFileId() != null ? c.getLogoFileId() : orgService.systemLogoFileId()));
    }

    private Long companyId(Long orgId) {
        if (orgId == null) return null;
        return orgService.getCompanyOf(orgId).map(o -> o.id()).orElse(null);
    }

    private String logo(Long fileId) {
        if (fileId == null) return null;
        FileDO f = fileMapper.selectById(fileId);
        if (f == null || f.getContentType() == null || !f.getContentType().startsWith("image/")) return null;
        try (InputStream in = fileService.open(f)) {
            byte[] bytes = in.readNBytes(MAX_LOGO_BYTES + 1);
            if (bytes.length > MAX_LOGO_BYTES) {
                log.warn("[打印抬头] 公司 Logo 超过 512KB，不打印 fileId={}", fileId);
                return null;
            }
            return "data:" + f.getContentType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("[打印抬头] 读取公司 Logo 失败 fileId={}", fileId, e);
            return null;
        }
    }

    private static String nz(String s) {
        return Objects.toString(s, "");
    }
}
