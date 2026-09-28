# 多阶段构建：Maven 镜像编译，jre-alpine 运行（体积小且自带 busybox wget 供健康检查）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY docker-settings.xml /root/.m2/settings.xml
COPY src ./src
RUN mvn -B -e package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S patchme && adduser -S -D -H -u 100432 -G patchme patchme
WORKDIR /app
# 命名卷首次挂载会继承镜像内目录属主：预先建好归 app 用户的日志目录，
# 否则 root 属主的匿名卷会让 logback 写 /app/logs 失败、应用在日志初始化时崩溃。
RUN mkdir -p /app/logs && chown -R patchme:patchme /app
COPY --from=build /workspace/target/patchme-backend-*.jar app.jar
USER patchme
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
