# CLAUDE.md

本仓库是 Spring Boot + Vue 的模块化单体 ERP。多个开发窗口会**并行开发不同模块**，请严格遵守模块边界。

## 必读文档

- `docs/并行开发指南.md`：每个窗口能改哪些文件、契约怎么变更
- `docs/architecture/后端架构设计.md`：分层、依赖规则、健壮性约定、接口约定
- `docs/requirements/README.md`（需求编写约定）、`docs/requirements/00-总体需求与通用规范.md`
- 对应模块的需求目录 `docs/requirements/<编号>-<模块>/`：先读 `README.md`，再读要实现的功能点文件
- `docs/ui/UI设计规范.md`：Design System（token、字号、颜色、图标）、页面模板 T1～T8、公共组件、交互与显示格式（前端必须遵守）

## 常用命令

```bash
mvn -B verify                                  # 后端：编译 + 全部测试 + ArchUnit 模块边界检查
mvn -B verify -pl erp-server -am -Dtest='*Material*' -Dsurefire.failIfNoSpecifiedTests=false   # 只跑部分测试
ERP_PROFILE=h2 java -jar erp-server/target/erp-server.jar   # 本地无 MySQL 启动（admin / admin123）
cd erp-ui && npm install && npm run dev        # 前端开发服务器 http://localhost:5173（/api 代理到 8080）
cd erp-ui && npm run build                     # 前端类型检查 + 构建
```

## 硬性规则

1. `erp-module-<a>-biz` 只能依赖其他模块的 `-api`，禁止依赖其他模块的 `-biz`，禁止访问其他模块的表。
2. `-api` 模块只放接口、DTO（record）、枚举、事件、错误码，只依赖 `erp-common`。
3. 数据库变更只能新增 Flyway 脚本：`erp-module-<code>-biz/src/main/resources/db/migration/<code>/V<n>__xxx.sql`，已合并脚本禁止修改。表名使用模块前缀。SQL 需兼容 MySQL 8 和 H2（MySQL 模式）。
4. 所有实体继承 `BaseDO`（单据头继承 `BaseDocDO`）；Mapper 继承 `BaseMapperX` 并加 `@Mapper`；更新用 `updateByIdOrFail` 做乐观锁校验。
5. 状态变更必须通过 `StateMachine.fire()`，不要直接 set 状态。
6. 业务错误抛 `BizException`，错误码定义在本模块 `XxxErrorCodes`，使用本模块号段。
7. 数量、金额使用 `BigDecimal` + `Decimals` 工具，禁止 double/float。
8. Controller 方法必须声明 `@PreAuthorize("@ss.has('...')")`，权限标识与需求文档一致。
9. 前端页面放在 `erp-ui/src/modules/<code>/`，在该目录的 `index.ts` 注册菜单，不改公共路由。
10. 参考实现：后端 `erp-modules/erp-module-engineering`（物料），前端 `erp-ui/src/modules/engineering`。
11. 前端页面以 `ErpPage` 为根、内容用 `ErpPanel` + 公共组件；样式只用 `--erp-*` token，不写颜色/字号/阴影字面量，图标只用 `components/icons.ts` 中的 Lucide 图标（`npm run build` 自动执行 `lint:style` 检查）。
12. 提交前 `mvn -B verify` 与 `npm run build` 必须通过。
