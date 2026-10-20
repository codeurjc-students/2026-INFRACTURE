# Changelog

Este fichero resume los cambios notables de cada versión de Infracture Local para quienes la usan, la instalan o consumen su API. Todavía no hay ninguna versión publicada, así que todo lo incorporado está en `Unreleased`.

El formato sigue [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/) y las versiones siguen [Semantic Versioning](https://semver.org/lang/es/). Los criterios para decidir y redactar cada entrada están en [Seguimiento](docs/tracking.md#changelog-y-notas-de-versión).

## [Unreleased]

### Added

- Catálogo de plantillas de componente en `/catalogue`: HTTP Service, Worker, Load Generator, PostgreSQL, Redis y RabbitMQ, con sus variantes, búsqueda por nombre, filtro por tipo y carga de diez en diez. La API lo ofrece sin sesión en `GET /api/v1/component-templates`, con los parámetros `q`, `type`, `page` y `size`, y responde `{content, page}`.
- Cuentas de usuario: registro, inicio y cierre de sesión desde la web y desde la API (`POST /api/v1/users`, `POST /api/v1/auth/login` y `POST /api/v1/auth/logout`). Al registrarse desde la web se inicia sesión automáticamente. El registro solo acepta `username`, `displayName`, `email` y `password`, responde 409 si el correo o el nombre de usuario ya existen y devuelve los errores como `application/problem+json`, con el motivo en `detail`.
- Sesión en cookies `HttpOnly`. El token de acceso dura 5 minutos y `POST /api/v1/auth/refresh` lo renueva durante 7 días. La web todavía no lo renueva sola, así que pasados 5 minutos pide iniciar sesión otra vez. Reiniciar el backend cierra todas las sesiones.
- Protección CSRF: las peticiones a la API que modifican datos deben enviar la cabecera `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`, que se obtiene con `GET /api/v1/auth/csrf`. La web lo hace por su cuenta.
- Página privada de cuenta en `/account` con el nombre, el usuario y el correo. Sin sesión, o con la sesión caducada, lleva al inicio de sesión y después vuelve a la página pedida.
- Administrador único creado en el primer arranque. Antes de arrancar hay que copiar `.env.example` a `.env` en la raíz y completarlo, o definir las variables `INFRACTURE_ADMIN_*`. Sus credenciales no se sobrescriben al reiniciar y la base de datos impide crear un segundo administrador.
- Interfaz web con tema claro u oscuro según la preferencia del sistema.
- Ejecución local con PostgreSQL en Docker Compose y `scripts/start-dev.sh`, que arranca base de datos, backend y frontend a la vez. Requiere Java 25 y Node.js 24 en las versiones exactas de `.java-version` y `.nvmrc`; los pasos están en la [guía de desarrollo](docs/development-guide.md).
- Contrato OpenAPI de la API en YAML y HTML y colección de Postman, en [`docs/api`](docs/api).
