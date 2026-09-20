FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S medcore && adduser -S medcore -G medcore
WORKDIR /app
COPY --from=build /build/target/hospital-1.0.0.war app.war
USER medcore
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.war"]
