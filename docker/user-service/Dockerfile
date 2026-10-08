# 多阶段构建 user-service：build context 为仓库根，COPY backend/ 才能拿到父 pom 与 common/auth-starter
# 注：不用 BuildKit cache mount，兼容旧 builder 与 macOS 外置卷（避免 ._ AppleDouble 触发 xattr 失败）

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
# 注入阿里云镜像 settings.xml，规避中央仓库网络抖动与失败缓存
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY backend/ ./
# -pl user-service -am：仅构建 user-service 及其依赖模块（common + auth-starter）
# -U 强制刷新插件元数据，绕过上次失败的本地缓存
RUN mvn -B clean package -DskipTests -U -pl user-service -am

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/user-service/target/user-service.jar app.jar
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
