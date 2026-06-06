FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S finguide && adduser -S finguide -G finguide
COPY --from=build /app/target/finguide-be-*.jar /app/finguide-be.jar
USER finguide
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/finguide-be.jar"]
