# Changelog

Este fichero registra los cambios relevantes incorporados a Infracture Local. El proyecto todavía no ha publicado una versión, por lo que los cambios permanecen en `Unreleased`.

El formato sigue las ideas de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Las funcionalidades planificadas no se incluyen hasta que se implementan; las decisiones y especificaciones se identifican como documentación.

## [Unreleased]

### Added

- Documentación de la Fase 1 con la especificación funcional, el análisis del dominio, los bocetos principales, el mapa de navegación y la arquitectura prevista para la ejecución local.
- Registro académico del uso de IA, glosario del dominio, guía de contribución para personas y agentes, plantillas de issues y pull requests y versiones reproducibles de Java y Node.js.
- Backend inicial con Spring Boot, PostgreSQL para desarrollo mediante Docker Compose y migraciones de esquema con Flyway.
- Catálogo persistente de seis plantillas de componente —HTTP Service, Worker, Load Generator, PostgreSQL, Redis y RabbitMQ— y endpoint `GET /api/v1/component-templates` para consultar las plantillas habilitadas.
- Generación reproducible del contrato OpenAPI del backend en formatos YAML y HTML.
- Frontend SPA con React, TypeScript, React Router y Vite, junto con un cliente HTTP tipado y una vista del catálogo que representa los estados de carga, contenido, vacío y error.
- Script de desarrollo para coordinar PostgreSQL, backend y frontend en local.
- Pruebas unitarias del servicio de catálogo con JUnit, Mockito y AssertJ, pruebas de la ruta del catálogo con Vitest y React Testing Library, pruebas de integración con Spring Boot, Flyway, Testcontainers y PostgreSQL desechable, una prueba de sistema de la API con REST Assured, una prueba de integración entre el cliente HTTP real y el backend y una prueba de sistema en Chromium sobre la aplicación completa.
- Informes reproducibles de cobertura del backend con JaCoCo en HTML y XML y del frontend con Vitest en HTML y LCOV, con un umbral inicial independiente del 70 % de líneas.
- CI básico con jobs independientes para compilar y probar unitariamente el backend y para validar, probar y construir el frontend en cada push a una rama de trabajo.
- CI completo para pull requests dirigidas a `main`, con pruebas backend, validaciones frontend, integración cliente-servidor, sistema en Chromium y artefactos de diagnóstico cuando falla una suite.
- Análisis CI-based de backend y frontend con SonarQube Cloud en pull requests, importando los informes JaCoCo XML y LCOV y haciendo que el job espere el resultado del Quality Gate.
- ADR que registra la elección de MinIO como futuro almacenamiento local de imágenes compatible con S3; la integración todavía no está implementada.

### Changed

- Alineación de la convención de ramas con GitHub Flow: `main` se mantiene estable, el trabajo usa nombres cortos y descriptivos en inglés y los cambios se integran mediante pull requests.
- Protección activa de `main`: los cambios requieren pull request y los cuatro checks del CI completo; también se bloquean la eliminación de la rama y los force pushes, sin bypasses configurados.
- Sustitución de MySQL por PostgreSQL como base de datos permanente prevista para la plataforma y como componente relacional del catálogo controlado.
- Revisión documental del modelo de dominio y de ejecución para conservar identidades históricas, revisiones inmutables y contratos tipados sin adelantar su implementación.
- Selección de Recharts para las gráficas, SonarQube Cloud para el análisis estático y Playwright para las pruebas de sistema; SonarQube Cloud y Playwright ya están integrados, mientras que Recharts permanece pendiente de su bloque de implementación.
- Migración del backend de Java 21 LTS a Java 25 LTS y actualización del entorno reproducible asociado.

### Fixed

- Corrección de la plantilla de issues de implementación para que GitHub la muestre correctamente.
