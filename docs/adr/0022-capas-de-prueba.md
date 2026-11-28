---
status: accepted
date: 2026-08-07
---

# Capas de prueba: unitarias, integración y sistema

## Contexto

El TFG exige pruebas unitarias, de integración y de sistema, con una cobertura mínima del 70 %. El riesgo de Infracture está sobre todo en los bordes. Las migraciones y restricciones de PostgreSQL deciden reglas de concurrencia ([ADR 0019](0019-postgresql-y-flyway-para-la-plataforma.md)), la sesión depende de cookies y CSRF ([ADR 0002](0002-proteccion-csrf-con-jwt-en-cookies.md)) y el motor habla con Docker real ([ADR 0013](0013-motor-de-ejecucion-con-docker-compose.md)). Una prueba que simule esos bordes puede pasar aunque la aplicación real falle.

Las herramientas se cerraron al terminar la Fase 1 (entrada AI-2026-08-07-009 del [registro de uso de IA](../AI_USAGE.md)), el tutor ratificó Playwright (AI-2026-08-09-010) y se instalaron capa a capa entre el 12 y el 20 de septiembre de 2026.

## Decisión

| Capa | Herramientas | Qué sustituye y qué no |
| --- | --- | --- |
| Unitarias del servidor (`*Tests`) | JUnit 5, Mockito, AssertJ | Dominio y servicios sin Spring, o con dobles de los repositorios y del `RuntimeEngine`. |
| Integración de persistencia (`*IntegrationTests`, `*RepositoryTests`) | Spring Boot Test, Flyway, Testcontainers | PostgreSQL real y desechable, con las migraciones reales. |
| Sistema de API (`*SystemTests`) | Spring Boot en un puerto aleatorio, REST Assured, Testcontainers | Peticiones HTTP reales con cookies y CSRF. |
| Unitarias y de rutas del cliente | Vitest, Testing Library, jsdom | Solo se simula `fetch`. Las rutas se montan con su `clientLoader` en un router en memoria. |
| Integración cliente-servidor | Vitest contra el backend real ([`test-client-server-integration.sh`](../../scripts/test-client-server-integration.sh)) | El cliente HTTP real habla con Spring y con PostgreSQL de Testcontainers; Docker se sustituye por un `RuntimeEngine` que espera a `Stop`. |
| Sistema en navegador | Playwright con Chromium | La SPA real contra el backend real. Para los experimentos, toda la plataforma en contenedores con Docker real ([`test-execution-stack.sh`](../../scripts/test-execution-stack.sh)). |

- **Cobertura por separado.** JaCoCo exige el 70 % de líneas al backend en `./mvnw verify`, y Vitest exige el 70 % al frontend en `npm run test:coverage`. Uno no compensa al otro.
- **CI en dos niveles.** Cada push a una rama de trabajo pasa lint, tipos, pruebas unitarias y build. Cada PR a `main`, y `main` tras integrarla, pasa todas las suites y SonarQube Cloud.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| H2 en memoria para las pruebas de persistencia | No tiene los índices únicos parciales, `jsonb` ni los bloqueos de PostgreSQL. Las pruebas pasarían sobre un esquema distinto del que se instala. |
| Una base de datos compartida para todas las pruebas | Las pruebas dependerían del orden y de datos que han quedado de otras. Con Testcontainers cada suite tiene su PostgreSQL y lo borra al terminar. |
| Jest en el frontend | Vitest usa la misma configuración y transformaciones que Vite, que ya construye el frontend, y no necesita Babel aparte. |
| Cypress o Selenium para el navegador | Playwright espera solo a que los elementos estén listos, busca por rol accesible y arranca y para los servidores de prueba desde su configuración (`webServer`). El tutor lo aceptó. |
| Simular Docker en todas las pruebas del motor | El adaptador de Compose solo se puede comprobar contra Docker. Las pruebas unitarias del reconciler sí usan un `RuntimeEngine` simulado; [`ComposeRuntimeEngineIntegrationTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ComposeRuntimeEngineIntegrationTests.java) usa Docker real. |
| Un único umbral de cobertura conjunto | Un backend muy probado podría tapar un frontend sin pruebas. |

Se acepta que las suites de integración y de sistema necesitan Docker, tardan más y no pueden correr a la vez cuando comparten puertos (8081, 5174 y 8080), como explica la [guía de desarrollo](../development-guide.md#ejecución-de-pruebas). A cambio, los fallos que solo aparecen con PostgreSQL, el navegador o Docker reales se detectan antes de integrar el cambio.

## Aplicación

Las herramientas y los comandos están en la [guía de desarrollo](../development-guide.md#control-de-calidad). Las pruebas del servidor están en [`backend/src/test/java`](../../backend/src/test/java/es/codeurjc/infracture), con el PostgreSQL de Testcontainers en [`TestInfractureBackendApplication`](../../backend/src/test/java/es/codeurjc/infracture/TestInfractureBackendApplication.java). Las del cliente están en [`frontend/test`](../../frontend/test), con la configuración en [`vitest.config.ts`](../../frontend/vitest.config.ts), [`vitest.integration.config.ts`](../../frontend/vitest.integration.config.ts), [`playwright.config.ts`](../../frontend/playwright.config.ts) y [`playwright.execution.config.ts`](../../frontend/playwright.execution.config.ts). Los umbrales están en el plugin de JaCoCo de [`backend/pom.xml`](../../backend/pom.xml) y en `coverage.thresholds` de Vitest. El CI está en [`.github/workflows`](../../.github/workflows).

## Referencias

- [Martin Fowler: The Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html).
- [Testcontainers para Java](https://java.testcontainers.org/).
- [Spring Boot: Testcontainers y `@ServiceConnection`](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html).
- [REST Assured](https://rest-assured.io/).
- [Vitest: guía](https://vitest.dev/guide/).
- [Playwright: introducción](https://playwright.dev/docs/intro).
- [JaCoCo: comprobación de cobertura](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html).
