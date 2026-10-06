package com.erp.module.workbench.config;

import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.workbench.dal.dataobject.WbNoticeDO;
import com.erp.module.workbench.dal.mapper.WbNoticeMapper;
import com.erp.module.workbench.service.notice.NoticeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 公告附件访问控制（SYS-FIL-R04）：已发布公告的附件登录即可查看；公告管理员可查看全部并上传、删除 */
@Configuration
public class WorkbenchFileAccessConfig {

    private static final String MANAGE = "wb:notice:manage";

    @Bean
    public FileAccessChecker workbenchFileAccessChecker(WbNoticeMapper noticeMapper) {
        return new FileAccessChecker() {
            @Override
            public boolean supports(String bizType) {
                return NoticeService.BIZ_TYPE.equals(bizType);
            }

            @Override
            public boolean canView(String bizType, Long bizId) {
                if (hasPermission(MANAGE)) return true;
                WbNoticeDO n = noticeMapper.selectById(bizId);
                return n != null && NoticeService.PUBLISHED.equals(n.getNoticeStatus());
            }

            @Override
            public boolean canEdit(String bizType, Long bizId) {
                return hasPermission(MANAGE);
            }
        };
    }

    private static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }
}
