# Changelog

Este fichero resume los cambios notables de cada versión de Infracture Local para quienes la usan, la instalan o consumen su API. Todavía no hay ninguna versión publicada, así que todo lo incorporado está en `Unreleased`.

El formato sigue [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/) y las versiones siguen [Semantic Versioning](https://semver.org/lang/es/). Los criterios para decidir y redactar cada entrada están en [Seguimiento](docs/tracking.md#changelog-y-notas-de-versión).

## [Unreleased]

### Added

- Cuentas de usuario: registro, inicio y cierre de sesión. Al registrarse se inicia sesión automáticamente.
- La sesión dura 7 días y la web la renueva sola mientras tanto. Reiniciar el backend cierra todas las sesiones.
- Página de cuenta en `/account` con el nombre, el usuario y el correo.
- Proyectos privados en `/projects`: cada cuenta crea, lista y edita los suyos, y nadie más puede verlos. Todavía no se pueden borrar ni publicar.
- Escenarios dentro de cada proyecto, con nombre y descripción editables. Todavía no se pueden borrar.
- Catálogo público en `/catalogue` con las plantillas de HTTP Service, Worker, PostgreSQL, Redis y RabbitMQ, con búsqueda por nombre y filtro por tipo.
- Lienzo del escenario: se añaden hasta 50 componentes del catálogo con un clic o arrastrándolos, y cada uno se configura con un formulario que solo admite valores dentro de los límites de su tipo.
- El escenario se guarda entero. Si otra pestaña guardó antes, el editor avisa sin sobrescribir nada y deja elegir qué versión conservar.
- El trabajo sin guardar se conserva en el navegador y se puede recuperar tras recargar o tras caducar la sesión. Otra cuenta en el mismo navegador no lo ve.
- Tema claro u oscuro según la preferencia del sistema.
- API REST bajo `/api/v1` para todo lo anterior. Las peticiones que modifican datos deben enviar la cabecera `X-XSRF-TOKEN`, que se obtiene con `GET /api/v1/auth/csrf`, y los errores llegan como `application/problem+json`. El contrato OpenAPI y la colección de Postman están en [`docs/api`](docs/api).
- Administrador único creado en el primer arranque. Antes de arrancar hay que copiar `.env.example` a `.env` en la raíz y completarlo, o definir las variables `INFRACTURE_ADMIN_*`.
- Ejecución local con `scripts/start-dev.sh`, que arranca PostgreSQL en Docker Compose, el backend y el frontend. Requiere Java 25 y Node.js 24 en las versiones exactas de `.java-version` y `.nvmrc`; los pasos están en la [guía de desarrollo](docs/development-guide.md).
