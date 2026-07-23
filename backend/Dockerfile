# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml settings.xml ./
# -s settings.xml 强制走阿里云镜像，避开 docker build 阶段中央 repo 网络抽风
RUN mvn -s settings.xml -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -s settings.xml -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/lifepulse-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
