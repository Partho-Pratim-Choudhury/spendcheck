# Build stage — compile and package (tests run in CI, skipped here).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Cache dependencies first: copy the POM, resolve, then copy sources.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Run stage — slim JRE with just the jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
# The platform (Fly) sets PORT; the app reads SERVER_PORT.
ENV SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
