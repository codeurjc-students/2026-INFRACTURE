# Image of the Infracture application. It is built from the repository root:
#   docker build -f docker/Dockerfile .
# For now it holds the backend; P10 adds the React build and HTTPS here.

# Build stage: compile the jar with the project's own Maven wrapper.
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -q package -DskipTests -Djacoco.skip=true

# Runtime stage: the JRE, the jar and the docker CLI with Compose, which the
# execution engine runs. The CLI reaches Docker only through DOCKER_HOST.
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache docker-cli docker-cli-compose \
    && addgroup -S infracture && adduser -S infracture -G infracture
USER infracture
WORKDIR /app
COPY --from=build /src/target/infracture-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
