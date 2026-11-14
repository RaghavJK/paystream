# One Dockerfile for all three services:
#   docker build --build-arg MODULE=payment-service -t paystream/payment-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY payment-service payment-service
COPY fraud-service fraud-service
COPY agent-service agent-service
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/*.jar app.jar
USER 1000
# Heap as a share of the container limit, leaving room for metaspace, threads and buffers.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
