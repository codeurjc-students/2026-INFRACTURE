# Infracture backend

Spring Boot backend for the Infracture platform.

## Local development

The backend requires Eclipse Temurin Java 21 and the development PostgreSQL
service defined in the repository root `compose.yaml`.

Start PostgreSQL from the repository root:

```bash
docker compose up -d --wait
```

Verify and start the backend:

```bash
cd backend
./mvnw verify
./mvnw spring-boot:run
```

Check the application health from another terminal:

```bash
curl http://localhost:8080/actuator/health
```

The local defaults connect to the `infracture` database at `localhost:5432`
with the `infracture` user. They can be replaced without editing tracked files:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/infracture
export SPRING_DATASOURCE_USERNAME=infracture
export SPRING_DATASOURCE_PASSWORD=infracture_dev
```

Stop the development service without deleting its persistent data:

```bash
docker compose down
```
