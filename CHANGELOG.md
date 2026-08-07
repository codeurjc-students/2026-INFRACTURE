# Changelog

Este fichero registrará los cambios relevantes de Infracture Local a medida que se publiquen nuevas versiones. El proyecto todavía no dispone de una versión ejecutable.

El formato sigue las ideas de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [Unreleased]

### Added

- Especificación inicial de Infracture Local como aplicación web para diseñar, validar, ejecutar y analizar escenarios de sistemas distribuidos sobre Docker en un único equipo.
- Definición de usuarios anónimos, registrados y administradores, con autenticación local, autorización por roles y propiedad de los recursos.
- Gestión de proyectos privados y públicos, escenarios visuales, clonación privada, perfiles, seguimiento de usuarios y estrellas como marcadores de proyectos.
- Catálogo controlado de seis tipos de componente reutilizables: HTTP Service, Worker, Load Generator, MySQL, Redis y RabbitMQ.
- Contratos de componentes basados en perfiles, capacidades, conexiones compatibles y validación previa a la ejecución.
- Motor de ejecución local con una red Docker aislada por ejecución, una única ejecución activa, comprobaciones de salud y limpieza segura de recursos.
- Generación de carga reproducible mediante Grafana k6 y aplicación de fallos controlados de parada, reinicio, pausa, reanudación y latencia mediante Docker y Toxiproxy.
- Observación de estados, eventos, logs y métricas en tiempo real mediante Server-Sent Events, junto con historial persistente y análisis del impacto de fallos sobre el grafo de dependencias.
- Laboratorios guiados con objetivos, intentos, puntuación, conceptos explorados y un profesor de IA consultivo que no podrá controlar Docker.
- Modelo inicial de entidades, relaciones y permisos, acompañado de bocetos de alta fidelidad y un mapa de navegación de las pantallas principales.
- Arquitectura de monolito modular con Java y Spring Boot, API REST, React y TypeScript, MySQL, Spring Data JPA, Flyway, MinIO, Docker Compose y docker-java.
- Sistema de interfaz basado en Vite, React Router, Tailwind CSS, shadcn/ui, React Flow, Motion for React y Lucide React.
- Estrategia de pruebas para backend, frontend y sistema con JUnit, Spring Boot Test, Mockito, REST Assured, Testcontainers, Vitest, React Testing Library y Playwright.
- Integración y entrega continuas con GitHub Actions para ejecutar controles de calidad, medir la cobertura y generar imágenes Docker y paquetes versionados.
- Documentación inicial formada por `README.md`, el modelo detallado de componentes y ejecución, el registro del uso de IA y los recursos visuales seleccionados del prototipo.
