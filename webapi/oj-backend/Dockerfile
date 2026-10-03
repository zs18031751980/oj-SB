# syntax=docker/dockerfile:1
# 统一构建 oj-api 与 oj-worker，运行时通过 SERVICE 选择。
FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml ./
COPY oj-common/pom.xml oj-common/
COPY oj-core/pom.xml oj-core/
COPY oj-infrastructure/pom.xml oj-infrastructure/
COPY oj-api/pom.xml oj-api/
COPY oj-worker/pom.xml oj-worker/
RUN mvn -B -q -DskipTests dependency:go-offline
COPY . .
RUN mvn -B -q -DskipTests -pl oj-api,oj-worker -am package

FROM eclipse-temurin:25-jre
ARG SERVICE=oj-api
ENV SERVICE=${SERVICE} JAVA_OPTS=""
WORKDIR /app
COPY --from=build /src/oj-api/target/oj-api-0.1.0-SNAPSHOT.jar /app/oj-api.jar
COPY --from=build /src/oj-worker/target/oj-worker-0.1.0-SNAPSHOT.jar /app/oj-worker.jar
EXPOSE 6173
ENTRYPOINT ["sh", "-c", "java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 $JAVA_OPTS -jar /app/${SERVICE}.jar"]
