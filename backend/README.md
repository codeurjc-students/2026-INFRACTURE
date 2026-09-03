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

The live OpenAPI contract and its interactive Swagger UI are available at:

- `http://localhost:8080/v3/api-docs.yaml`
- `http://localhost:8080/swagger-ui.html`

To reproduce the versioned YAML and standalone HTML documentation, keep the
development PostgreSQL service running and execute:

```bash
./mvnw verify -Popenapi
```

The command updates `docs/api/api-docs.yaml` and `docs/api/api-docs.html` from
the running application. Both files document the actual controller contract;
they are not maintained by hand.

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
