# syntax=docker/dockerfile:1
# 后端镜像：Maven 构建 erp-server（跳过测试）→ JRE 21 运行
FROM maven:3.9-eclipse-temurin-21 AS build
# 可选：Maven 镜像仓库（如 https://maven.aliyun.com/repository/public）；
# 需要经代理访问外网时传入标准的 HTTPS_PROXY 构建参数（http://[user:pass@]host:port），自动写入 Maven 代理配置
ARG MAVEN_MIRROR=""
ARG HTTPS_PROXY=""
WORKDIR /src
# 可选：企业代理 / 内网仓库的 CA 证书（deploy/certs/*.crt，一个文件可含多张证书）
COPY deploy/certs/ /tmp/certs/
RUN set -e; \
    for c in /tmp/certs/*.crt; do [ -f "$c" ] || continue; \
      awk -v p="/tmp/split-$(basename "$c" .crt)-" '/BEGIN CERTIFICATE/{n++} n{print > (p n ".pem")}' "$c"; done; \
    for p in /tmp/split-*.pem; do [ -f "$p" ] || continue; \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias "extra-$(basename "$p" .pem)" -file "$p"; done; \
    mirrors=""; proxies=""; \
    if [ -n "$MAVEN_MIRROR" ]; then \
      mirrors="<mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>$MAVEN_MIRROR</url></mirror></mirrors>"; fi; \
    if [ -n "$HTTPS_PROXY" ]; then \
      hp="${HTTPS_PROXY#*://}"; hp="${hp%%/*}"; auth=""; \
      case "$hp" in *@*) auth="${hp%@*}"; hp="${hp##*@}";; esac; \
      cred=""; [ -n "$auth" ] && cred="<username>${auth%%:*}</username><password>${auth#*:}</password>"; \
      proxies="<proxies><proxy><id>proxy</id><active>true</active><protocol>https</protocol><host>${hp%:*}</host><port>${hp##*:}</port>$cred<nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy></proxies>"; fi; \
    mkdir -p /root/.m2 && echo "<settings>$mirrors$proxies</settings>" > /root/.m2/settings.xml
COPY pom.xml ./
COPY erp-framework erp-framework
COPY erp-modules erp-modules
COPY erp-server erp-server
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q -DskipTests package -pl erp-server -am

FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"
RUN useradd --system --uid 1001 --home-dir /app erp && mkdir -p /app/data/files && chown -R erp /app
WORKDIR /app
COPY --from=build --chown=erp /src/erp-server/target/erp-server.jar app.jar
USER erp
# 附件等运行数据（ERP_FILE_PATH 默认 ./data/files）
VOLUME /app/data
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q "\"UP\"" <&3'
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
