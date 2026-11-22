---
status: accepted
date: 2026-10-21
---

# Errores de la API como ProblemDetail

## Contexto

Hasta octubre de 2026 los errores de la API llegaban de formas distintas. El catálogo devolvía la página de error estándar de Spring. El registro de usuarios tenía su propio `UserExceptionHandler`. Los 401 y 403 de Spring Security no pasaban por ningún manejador, y el refresh no válido respondía con un campo `FAILURE`. El frontend tenía que adivinar el formato en cada servicio para mostrar el mensaje.

ID32 e ID35 de la [spec](../PHASE_3_SPEC.md#api-y-experiencia-de-uso) piden un contrato común de errores y que un fallo inesperado no exponga detalles internos.

## Decisión

- **Todos los errores de `/api` son `application/problem+json`** según la RFC 9457, con `status` y `detail` legible. Se usa el `ProblemDetail` de Spring.
- **Un manejador global en `shared/api`.** [`ApiExceptionHandler`](../../backend/src/main/java/es/codeurjc/infracture/shared/api/ApiExceptionHandler.java) traduce las excepciones comunes: `InvalidDataException` y la validación a 400, `ConflictException` a 409, `NotFoundException` a 404, autenticación a 401, permisos a 403 y una ruta inexistente a 404. Tiene la menor precedencia, así que un módulo puede añadir su propio manejador para un caso concreto, como el 422 con `issues` de [`ExecutionExceptionHandler`](../../backend/src/main/java/es/codeurjc/infracture/execution/api/ExecutionExceptionHandler.java).
- **Spring Security pasa por el mismo manejador.** [`SecurityConfig`](../../backend/src/main/java/es/codeurjc/infracture/security/SecurityConfig.java) delega los 401 y 403 en el `HandlerExceptionResolver`, así que también llegan como `ProblemDetail`. Unas credenciales incorrectas (`BadCredentialsException`) y una sesión ausente dan textos distintos.
- **Un 500 nunca expone la excepción.** Responde «An unexpected error occurred.» y la traza solo va al log.
- **El frontend lee solo `detail`.** [`apiFetch`](../../frontend/app/shared/api/api-fetch.ts) lo extrae cuando el `Content-Type` es `application/problem+json` y lanza un `HttpError` con el estado.
- **Fuera del alcance:** errores por campo y valores `type` estables. Hoy `type` es `about:blank`.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Un DTO de error propio (`{ code, message }`) | Sería un formato inventado que hay que documentar y mantener. La RFC 9457 es un estándar que Spring ya produce y que cualquier cliente HTTP reconoce. |
| Manejadores sueltos en cada controlador | Es lo que había: formatos distintos y casos olvidados, como los 401 de Spring Security, que no pasan por los controladores. |
| La página de error de Spring Boot (`/error`) | Su formato es distinto del de los manejadores y depende de la configuración de `server.error.*`. Además puede incluir detalles internos si alguien la activa. |
| Devolver el mensaje de la excepción en el 500 | Puede revelar SQL, rutas o clases internas. |

Se acepta que el frontend solo distingue errores por estado y texto, y que los mensajes por campo se muestran juntos en un aviso general. Además, OpenAPI muestra todas las respuestas de error en todas las operaciones ([ADR 0023](0023-contrato-openapi-generado-desde-el-codigo.md)). A cambio, hay una sola forma de leer un error en todo el cliente, y el contrato se puede comprobar en cada prueba con el mismo ayudante.

## Aplicación

Se implementó en el commit `a96795b`, según la entrada AI-2026-10-21-063 del [registro de uso de IA](../AI_USAGE.md). Las excepciones comunes están en [`shared`](../../backend/src/main/java/es/codeurjc/infracture/shared). Las pruebas de API comprueban el formato con [`ProblemResponses.problem`](../../backend/src/test/java/es/codeurjc/infracture/shared/api/ProblemResponses.java), y [`SecurityConfigTests`](../../backend/src/test/java/es/codeurjc/infracture/security/SecurityConfigTests.java) cubre los 401 y 403. En el cliente, lo comprueba [`api-fetch.test.ts`](../../frontend/test/shared/api/api-fetch.test.ts).

## Referencias

- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457).
- [Spring Framework: respuestas de error REST](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).
- [Spring Security: manejo de excepciones](https://docs.spring.io/spring-security/reference/servlet/architecture.html#servlet-exceptiontranslationfilter).
