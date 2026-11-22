---
status: accepted
date: 2026-08-09
---

# PostgreSQL y Flyway para la plataforma

## Contexto

La propuesta de la Fase 1 usaba MySQL, tanto para guardar los datos de la plataforma como para el componente relacional de los escenarios. Antes de la Fase 2, el tutor apoyó cambiar a PostgreSQL. Esto se recoge en la entrada AI-2026-08-09-010 del [registro de uso de IA](../AI_USAGE.md), y la documentación cambió el 13 de agosto (commit `b943dfe`).

El modelo de datos es relacional: usuarios, proyectos, escenarios, nodos, conexiones y ejecuciones, con propiedad y claves que hay que proteger. Algunas partes, en cambio, son documentos que no se consultan campo a campo, como los parámetros de un nodo o el plan y el snapshot de una ejecución. Además, varias reglas tienen que cumplirse también con peticiones simultáneas. Por ejemplo, solo puede haber un administrador y una ejecución activa.

Faltaba decidir también cómo evoluciona el esquema sin perder los datos ya guardados.

## Decisión

- **PostgreSQL** guarda los datos de la plataforma, y también es el componente relacional de los escenarios. Las dos instancias están separadas aunque usen la misma tecnología.
- **Modelo relacional y normalizado.** `jsonb` se usa solo para documentos controlados: la configuración de un nodo, los parámetros de la carga y el plan de una ejecución. Siempre lleva un `CHECK` que exige un objeto y la validación del contrato en Java.
- **La base de datos garantiza las reglas de concurrencia** con índices únicos parciales: `uk_users_single_admin` y `ux_executions_single_active` ([ADR 0015](0015-una-ejecucion-por-instancia-en-local.md)).
- **Flyway** aplica migraciones SQL versionadas (`V<n>__descripcion.sql`) al arrancar. Una migración ya aplicada nunca se cambia.
- **Hibernate solo comprueba el esquema** (`spring.jpa.hibernate.ddl-auto=validate`). Si una entidad no coincide con la tabla, el backend no arranca.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| MySQL (propuesta de la Fase 1) | No tiene índices parciales con `WHERE`; habría que imitarlos con columnas generadas. Son justo lo que hace falta para «un solo administrador» y «una sola ejecución sin liberar». PostgreSQL ya estaba respaldado por el tutor. |
| Una base de datos documental (MongoDB) | El dominio es relacional: propiedad, claves foráneas y borrados en cascada. Perderíamos las transacciones y restricciones que protegen la integridad. |
| `ddl-auto=update` de Hibernate | Cambia el esquema sin dejar historial y no sabe migrar datos. Las migraciones de Flyway se revisan como código y se pueden reproducir. |
| Liquibase | Hace lo mismo que Flyway, pero con más ceremonia (cambios en XML o YAML). Flyway con SQL simple basta para una sola base de datos PostgreSQL. |

Se acepta escribir cada cambio de esquema a mano en SQL, y que un error en una migración ya aplicada se corrige con otra migración nueva. A cambio, el esquema que se prueba (con Testcontainers, [ADR 0022](0022-capas-de-prueba.md)) es el mismo que se instala, y las reglas de concurrencia no dependen de que Java compruebe antes de guardar.

## Aplicación

Concreta ID01 e ID04 de la [spec](../PHASE_3_SPEC.md#arquitectura-y-vocabulario). Se aplicó en el commit `d827b93` (#35) con [`V1__initialize_platform_database.sql`](../../backend/src/main/resources/db/migration/V1__initialize_platform_database.sql). Las migraciones están en [`db/migration`](../../backend/src/main/resources/db/migration); algunos ejemplos de reglas en la base de datos son [`V5__enforce_single_administrator.sql`](../../backend/src/main/resources/db/migration/V5__enforce_single_administrator.sql) y [`V10__create_executions.sql`](../../backend/src/main/resources/db/migration/V10__create_executions.sql). La configuración está en [`application.properties`](../../backend/src/main/resources/application.properties) y la instancia de desarrollo en [`compose.yaml`](../../compose.yaml). Lo comprueban [`ComponentTemplateMigrationIntegrationTests`](../../backend/src/test/java/es/codeurjc/infracture/catalog/persistence/ComponentTemplateMigrationIntegrationTests.java), que migra con datos previos, y [`ExecutionPersistenceIntegrationTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/persistence/ExecutionPersistenceIntegrationTests.java) (`allowsOnlyOneUnreleasedExecutionAcrossAllUsers`).

## Referencias

- [PostgreSQL: índices parciales](https://www.postgresql.org/docs/current/indexes-partial.html).
- [PostgreSQL: tipos JSON](https://www.postgresql.org/docs/current/datatype-json.html).
- [MySQL: CREATE INDEX](https://dev.mysql.com/doc/refman/8.4/en/create-index.html).
- [Flyway: documentación](https://documentation.red-gate.com/fd).
- [Spring Boot: inicializar la base de datos con Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway).
