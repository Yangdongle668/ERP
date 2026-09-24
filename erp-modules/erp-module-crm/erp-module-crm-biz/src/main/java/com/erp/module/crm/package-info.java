/**
 * CRM模块实现。需求见 docs/requirements/03-CRM.md。
 *
 * <p>包结构：
 * <ul>
 *   <li>{@code controller}：REST 接口，路径前缀 /api/crm/，VO 放在 controller.vo</li>
 *   <li>{@code service}：业务逻辑与事务边界</li>
 *   <li>{@code dal.dataobject} / {@code dal.mapper}：表实体与 MyBatis Mapper，只能被本模块使用</li>
 *   <li>{@code apiimpl}：本模块 api 接口的实现（XxxApiImpl）</li>
 *   <li>{@code listener}：监听其他模块的领域事件</li>
 * </ul>
 */
package com.erp.module.crm;
