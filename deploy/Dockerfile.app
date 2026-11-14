# 多阶段：Temurin 25 编译 → JRE 运行 my-chat-server
# 构建上下文为仓库根目录：docker build -f deploy/Dockerfile.app .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
ENV MAVEN_OPTS=-Xmx512m
COPY my-chat-server/mvnw my-chat-server/pom.xml ./
COPY my-chat-server/.mvn ./.mvn
RUN chmod +x mvnw
# 先拉依赖，改源码时少打依赖层
RUN ./mvnw -B -q -DskipTests dependency:go-offline || true
COPY my-chat-server/src ./src
RUN ./mvnw -B -DskipTests package \
    && cp target/my-chat-server-*.jar /tmp/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN mkdir -p /data/workspace /data/ingest
COPY --from=build /tmp/app.jar /app/app.jar
EXPOSE 8100
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
