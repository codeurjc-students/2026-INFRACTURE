# Changelog

Este fichero resume los cambios relevantes entregados en Infracture Local para quienes usan, instalan o mantienen la aplicación. Todavía no hay una versión publicada; los cambios incorporados permanecen en `Unreleased`.

El formato sigue [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Los criterios de selección y publicación de notas oficiales están en [Seguimiento](docs/tracking.md#changelog-y-notas-de-versión). Los planes y las decisiones de diseño se conservan en la spec y los ADR.

## [Unreleased]

### Added

- Backend inicial con Spring Boot, PostgreSQL para desarrollo mediante Docker Compose y migraciones de esquema con Flyway.
- Inicialización de un único administrador desde configuración externa, sin sobrescribir sus credenciales al reiniciar. El primer arranque sin administrador requiere completar el `.env` de la raíz a partir de `.env.example` o configurar las variables `INFRACTURE_ADMIN_*`; Flyway V5 impide crear un segundo `ADMIN`.
- Catálogo persistente de seis plantillas de componente —HTTP Service, Worker, Load Generator, PostgreSQL, Redis y RabbitMQ— y endpoint `GET /api/v1/component-templates` para consultar las plantillas habilitadas.
- Generación reproducible del contrato OpenAPI del backend en formatos YAML y HTML.
- Frontend SPA con React, TypeScript, React Router y Vite, junto con un cliente HTTP tipado y una vista del catálogo que representa los estados de carga, contenido, vacío y error.
- Script de desarrollo para coordinar PostgreSQL, backend y frontend en local.
- Pruebas del catálogo en backend, frontend, API HTTP e integración cliente-servidor, junto con pruebas de sistema en Chromium sobre la aplicación real.
- Informes de cobertura con JaCoCo y Vitest y un umbral independiente del 70 % de líneas para backend y frontend.
- Integración continua para ramas, pull requests y `main`, con validación del código, pruebas y análisis de SonarQube Cloud que exige superar el Quality Gate.
- Guía de desarrollo para reproducir el arranque, las pruebas y la generación de OpenAPI, y colección de Postman para consultar el catálogo.

### Changed

- Registro de cuentas con rechazo de campos controlados por el servidor y respuesta 409 para correos o nombres de usuario duplicados, incluso ante solicitudes simultáneas.

- Catálogo con variantes por tipo y carga incremental de diez plantillas, ordenadas por nombre y clave. `GET /api/v1/component-templates` acepta `q`, `type`, `page` y `size` y devuelve `{content, page}` en lugar de un array; los clientes deben adaptarse al nuevo formato. Flyway aplica V3 para permitir variantes conservando datos y claves únicas.
- Verificación del catálogo ampliada para cubrir carga, vacío, errores HTTP o de red, campos visibles, recarga y navegación de retorno desde una página inexistente.
- Pruebas de navegador aisladas de los servidores y datos de desarrollo mediante un backend con PostgreSQL desechable de Testcontainers.
- Backend actualizado de Java 21 LTS a Java 25 LTS, con versiones de Java y Node.js fijadas para reproducir el entorno de desarrollo.
