package com.erp.module.engineering.api.cert;

import java.util.List;

/** 认证查询（销售、出货）：只返回未撤销且未过期的证书（ENG-CRT-R03） */
public interface CertificationApi {

    List<CertificationDTO> listValid(Long materialId);
}
