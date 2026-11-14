# 多阶段：官方 Maven+Temurin25 编译（避免 CentOS7 上 mvnw 解压 Maven 报 Function not implemented）
# 构建上下文为仓库根目录：docker build -f deploy/Dockerfile.app .

FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /src
ENV MAVEN_OPTS=-Xmx512m
COPY my-chat-server/pom.xml ./
# 先拉依赖，改源码时少打依赖层
RUN mvn -B -q -DskipTests dependency:go-offline || true
COPY my-chat-server/src ./src
RUN mvn -B -DskipTests package \
    && cp target/my-chat-server-*.jar /tmp/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN mkdir -p /data/workspace /data/ingest
COPY --from=build /tmp/app.jar /app/app.jar
EXPOSE 8100
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
