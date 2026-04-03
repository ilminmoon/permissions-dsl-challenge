# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode -Dmaven.repo.local=/root/.m2/repository dependency:go-offline

COPY src/ src/
COPY examples/ examples/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode -Dmaven.repo.local=/root/.m2/repository test package

FROM eclipse-temurin:21-jre

WORKDIR /opt/authz

COPY --from=build /workspace/target/authz-policy-engine-0.0.1-SNAPSHOT.jar /opt/authz/authz-policy-engine.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/authz/authz-policy-engine.jar"]
