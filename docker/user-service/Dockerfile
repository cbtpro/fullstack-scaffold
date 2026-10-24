# 多阶段构建 user-service：build context 为仓库根，COPY backend/ 才能拿到父 pom 与 common/auth-starter
# Maven 入口清理源码上下文，BuildKit cache 保留依赖，失败有限重试。

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build/backend
# 注入阿里云镜像 settings.xml，规避中央仓库网络抖动与失败缓存
COPY docker/maven-settings.xml /tmp/maven-settings.xml
COPY backend/ ./
# 父 reactor 也声明前端部署模块，保持与仓库一致的相对目录。
COPY docker/web-react/pom.xml /build/docker/web-react/pom.xml
COPY docker/web-vue/pom.xml /build/docker/web-vue/pom.xml
# -pl user-service -am：仅构建 user-service 及其依赖模块（common + auth-starter）
# -U 强制刷新插件元数据，绕过上次失败的本地缓存
RUN --mount=type=cache,target=/root/.m2 \
    for attempt in 1 2 3; do \
      mvn -B -s /tmp/maven-settings.xml clean package -DskipTests -U -pl user-service -am && exit 0; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      sleep 5; \
    done

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/backend/user-service/target/user-service.jar app.jar
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
