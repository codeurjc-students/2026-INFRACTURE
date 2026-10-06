# Changelog

Este fichero registra los cambios relevantes incorporados a Infracture Local. El proyecto todavía no ha publicado una versión, por lo que los cambios permanecen en `Unreleased`.

El formato sigue las ideas de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Se registran cambios relevantes de producto, calidad, infraestructura y documentación compartida. Las funcionalidades planificadas se incorporan cuando se implementan; la configuración personal de IA y el detalle de las sesiones se registran en `docs/AI_USAGE.md`.

## [Unreleased]

### Added

- Publicado el desglose documental de Fase 3 en GitHub: 12 issues padre y 60 subissues en inglés, con 60 relaciones padre–hijo y 107 bloqueos nativos verificados. Conservados criterios, pruebas, recorridos manuales y trazabilidad de historias/decisiones/rúbrica; T02 retirado, sin renumerar. Eliminados los 72 Markdown individuales tras comprobar cuerpos y relaciones; la spec #60 enlaza las issues reales. Los requisitos de entrega continua y ambas PR de versiones quedan detallados en T48/T50–T52/T60/T61. Publicación de planificación, sin implementación ni release de producto.
- Revisión documental de la guía de Fase 3 y nueve funciones básicas: matriz de requisitos, correcciones de descripción de Scenario, icono al crear ficha, nombres/carpeta Docker e introducción de desarrollo. Normas generales centralizadas para evitar su repetición en cada ticket; se conservan las pruebas concretas. No acredita implementación.
- Especificación integral de Fase 3 para Infracture 0.1.0, con historias, acuerdos del grill, aceptación, pruebas, pendientes por bloque y 31 comprobaciones obligatorias. Copia permanente en español y traducción inglesa en la issue #60, con alcance e identificadores equivalentes y huellas distintas por idioma. Documento de planificación; no acredita funcionalidades implementadas.
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
- CI completo para pull requests dirigidas a `main` y para el resultado integrado tras cada merge, con pruebas backend, validaciones frontend, integración cliente-servidor, sistema en Chromium y artefactos de diagnóstico cuando falla una suite.
- Análisis CI-based de backend y frontend con SonarQube Cloud en pull requests y en `main`, importando los informes JaCoCo XML y LCOV y haciendo que el job espere el resultado del Quality Gate.
- ADR que registra la elección de MinIO como futuro almacenamiento local de imágenes compatible con S3; la integración todavía no está implementada.
- Guía de desarrollo de Fase 2 con diagrama Mermaid de procesos independientes y protocolos de comunicación, herramientas, controles de calidad, métricas de tamaño del código y uso histórico de Git, arranque local, pruebas y OpenAPI comprobados desde un clon limpio; colección de Postman para la operación REST existente y capturas del GitHub Project, CI y SonarQube Cloud.

### Changed

- Revisadas las 72 issues de planificación de Fase 3 con los bloques de Fase 2: enlaces entre issues, sin referencias a fuentes locales ni instrucciones Human delivery. Conservadas pruebas concretas y trasladadas cuatro integraciones a las tareas que pueden demostrarlas. La spec local y la issue #60 incorporan el orden global recomendado; 12 padres, 60 tareas y 107 bloqueos permanecen. Cambio documental, sin funcionalidades implementadas.

- Alineadas Scope, spec local y tickets de Fase 3 con las versiones exigidas por la guía por instrucción posterior del alumno: dos PR antes/después de release, backend final de main `0.2.0-SNAPSHOT`, frontend `0.2.0` y Compose `0.2`. Sustituido el aplazamiento anterior; diferencias con la rúbrica conservadas y spec #60 equivalente en contenido a la copia local, en inglés por petición posterior del alumno. Cambio de planificación, sin modificar versiones de producto ni publicar releases.
- Renombrado el glosario de dominio de `CONTEXT.md` a `GLOSSARY.md`, conservando su contenido y actualizando enlaces y referencias para la convención de Matt Pocock v1.3. Cambio documental.

- Precisado el glosario del catálogo: los seis tipos de componente se distinguen de las fichas administrables, que podrán tener varias variantes por tipo; decisión de dominio para Fase 3, sin implementación todavía.
- Alineada la documentación de producto con los acuerdos de Fase 3 sobre variantes del catálogo, portadas automáticas e imágenes; identificado el prototipo con inyección de fallos como referencia del diseño inicial. Cambios documentales, sin implementación de esas funciones.
- Documentación del uso de IA ampliada con diagramas de arquitectura y flujo híbrido; registro de preparación agrupado por finalidad.
- Aclarado en la especificación funcional que la consulta y administración de laboratorios pertenecen al nivel avanzado, fuera del alcance básico de Fase 3; cambio documental, sin implementación de producto.
- Ampliación de las pruebas E2E del catálogo con comprobaciones de API, campos visibles y recarga, evidencias adjuntas y un recorrido de página inexistente y retorno.
- Las pruebas de la ruta del catálogo integran el cliente HTTP real y la vista, simulando `fetch` para comprobar carga, contenido, vacío y errores HTTP o de red.
- La prueba de sistema en Chromium utiliza el backend con PostgreSQL desechable de Testcontainers, reutilizando el arranque de integración y aislándose de los servidores y datos de desarrollo.
- El catálogo ordena alfabéticamente por nombre las plantillas habilitadas que muestra la API y la vista, en lugar de seguir el orden del enum.
- Documentación de Fase 1 dividida por secciones académicas en `docs/`, con el README como portada e índice y el registro de IA trasladado a `docs/`.
- Alineación de la convención de ramas con GitHub Flow: `main` se mantiene estable, el trabajo usa nombres cortos y descriptivos en inglés y los cambios se integran mediante pull requests.
- Protección activa de `main`: los cambios requieren pull request y los cuatro checks del CI completo; también se bloquean la eliminación de la rama y los force pushes, sin bypasses configurados.
- Sustitución de MySQL por PostgreSQL como base de datos permanente prevista para la plataforma y como componente relacional del catálogo controlado.
- Revisión documental del modelo de dominio y de ejecución para conservar identidades históricas, revisiones inmutables y contratos tipados sin adelantar su implementación.
- Selección de Recharts para las gráficas, SonarQube Cloud para el análisis estático y Playwright para las pruebas de sistema; SonarQube Cloud y Playwright ya están integrados, mientras que Recharts permanece pendiente de su bloque de implementación.
- Migración del backend de Java 21 LTS a Java 25 LTS y actualización del entorno reproducible asociado.

### Removed

- Retirado el índice local `docs/PHASE_3_TICKETS.md` por petición del alumno. La spec española se conserva; las tareas, dependencias y el orden de implementación se consultan en GitHub. Limpiados los archivos auxiliares de planificación y publicación en `.local-docs`, sin cambios de producto.
- Retiradas del README de Engram las notas de activación y el historial personal de configuración; se conserva la documentación de uso y sincronización del proyecto.

### Fixed

- Corrección de la plantilla de issues de implementación para que GitHub la muestre correctamente.
