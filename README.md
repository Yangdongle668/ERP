# ERP

制造企业 ERP 系统，覆盖 CRM、销售、研发工程、PMC、资材、仓库、生产、品质、出货、财务、BI/AI。

- 后端：Spring Boot 3.3 + MyBatis-Plus + Spring Security(JWT) + Flyway，Java 21，模块化单体
- 前端：Vue 3 + Vite + TypeScript + Element Plus + Pinia

## 快速开始

```bash
# 1. 后端（无需 MySQL，使用 H2 文件库）
mvn -B -DskipTests package
ERP_PROFILE=h2 java -jar erp-server/target/erp-server.jar

# 2. 前端
cd erp-ui
npm install
npm run dev
```

打开 http://localhost:5173 ，使用 `admin / admin123` 登录（上线前必须修改）。接口文档：http://localhost:8080/swagger-ui.html

### 使用 MySQL

```bash
export ERP_DB_HOST=localhost ERP_DB_PORT=3306 ERP_DB_NAME=erp ERP_DB_USER=erp ERP_DB_PASSWORD=******
export ERP_JWT_SECRET=<至少32位随机字符串>
java -jar erp-server/target/erp-server.jar
```

表结构由各模块的 Flyway 脚本在启动时自动创建。

## 项目结构

```
erp-framework/     框架层（erp-common 纯 Java 公共类型；erp-framework-core Spring 基础设施）
erp-modules/       13 个业务模块，每个 = xxx-api（对外契约）+ xxx-biz（实现）
erp-server/        启动模块、配置、集成测试、架构测试
erp-ui/            前端
docs/              需求分析、架构设计、并行开发指南
```

## 当前进度

骨架已完成：框架层、13 个模块的工程结构与菜单、登录鉴权、编码规则、物料管理（前后端完整参考实现）、CI。其他模块按 [并行开发指南](docs/并行开发指南.md) 分批开发。

## 文档

见 [docs/README.md](docs/README.md)。
