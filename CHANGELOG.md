# Changelog

Este fichero resume los cambios relevantes entregados en Infracture Local para quienes usan, instalan o mantienen la aplicación. Todavía no hay una versión publicada; los cambios incorporados permanecen en `Unreleased`.

El formato sigue [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Los criterios de selección y publicación de notas oficiales están en [Seguimiento](docs/tracking.md#changelog-y-notas-de-versión). Los planes y las decisiones de diseño se conservan en la spec y los ADR.

## [Unreleased]

### Added

- Backend inicial con Spring Boot, PostgreSQL para desarrollo mediante Docker Compose y migraciones de esquema con Flyway.
- Catálogo persistente de seis plantillas de componente —HTTP Service, Worker, Load Generator, PostgreSQL, Redis y RabbitMQ— y endpoint `GET /api/v1/component-templates` para consultar las plantillas habilitadas.
- Generación reproducible del contrato OpenAPI del backend en formatos YAML y HTML.
- Frontend SPA con React, TypeScript, React Router y Vite, junto con un cliente HTTP tipado y una vista del catálogo que representa los estados de carga, contenido, vacío y error.
- Script de desarrollo para coordinar PostgreSQL, backend y frontend en local.
- Pruebas del catálogo en backend, frontend, API HTTP e integración cliente-servidor, junto con pruebas de sistema en Chromium sobre la aplicación real.
- Informes de cobertura con JaCoCo y Vitest y un umbral independiente del 70 % de líneas para backend y frontend.
- Integración continua para ramas, pull requests y `main`, con validación del código, pruebas y análisis de SonarQube Cloud que exige superar el Quality Gate.
- Guía de desarrollo para reproducir el arranque, las pruebas y la generación de OpenAPI, y colección de Postman para consultar el catálogo.

### Changed

- El catálogo ordena alfabéticamente por nombre las plantillas habilitadas que muestra la API y la vista, en lugar de seguir el orden del enum.
- Verificación del catálogo ampliada para cubrir carga, vacío, errores HTTP o de red, campos visibles, recarga y navegación de retorno desde una página inexistente.
- Pruebas de navegador aisladas de los servidores y datos de desarrollo mediante un backend con PostgreSQL desechable de Testcontainers.
- Backend actualizado de Java 21 LTS a Java 25 LTS, con versiones de Java y Node.js fijadas para reproducir el entorno de desarrollo.
