# Build stage: compile the jar with the project's own Maven wrapper.
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

# Runtime stage: only the JRE and the jar, run by an unprivileged user.
# No HEALTHCHECK here: the execution plan defines it (wget comes with Alpine).
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S demo && adduser -S demo -G demo
USER demo
WORKDIR /app
COPY --from=build /src/target/infracture-demo-app-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
