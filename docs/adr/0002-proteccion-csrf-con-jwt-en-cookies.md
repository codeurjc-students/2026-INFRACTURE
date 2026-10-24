---
status: accepted
date: 2026-10-15
---

# Sesión con JWT en cookies y protección CSRF

## Contexto

React consume la API REST de Infracture con JWT en cookies de autenticación `HttpOnly`. El navegador puede enviar esas cookies automáticamente, por lo que una petición necesita una protección adicional frente a operaciones forjadas desde otra web. Usar REST y sesiones sin estado no elimina por sí solo ese riesgo.

## Decisión

Mantener CSRF en las operaciones de escritura de la API, incluidos registro, login, renovación y logout, con la configuración `.csrf(csrf -> csrf.spa())` de Spring Security.

El cliente leerá la cookie `XSRF-TOKEN` y enviará su valor en la cabecera `X-XSRF-TOKEN`. Esta cookie es legible por JavaScript para preparar la petición; las cookies JWT conservan `HttpOnly`. El token CSRF y los JWT tienen funciones independientes.

Antes de la primera escritura, el cliente obtendrá la cookie mediante un `GET` público. La obtención del token y el envío de la cabecera se centralizarán en el cliente HTTP de React.

## Duración y almacenamiento de la sesión

| Token | Cookie | Duración | Uso |
| --- | --- | --- | --- |
| Acceso | `AuthToken` | 5 minutos | Autentica cada petición a la API. |
| Renovación | `RefreshToken` | 7 días | Obtiene un token de acceso nuevo con `POST /api/v1/auth/refresh`. |

Ambas cookies son `HttpOnly`, usan `Path=/` y su `Max-Age` coincide con la duración del token. El servidor no guarda estado de sesión: valida la firma y la caducidad de cada JWT. La clave de firma se genera al arrancar el backend, por lo que un reinicio invalida todas las sesiones abiertas.

El logout borra las dos cookies, pero no revoca en el servidor copias de tokens todavía vigentes; la revocación queda como [mejora opcional](0007-revocacion-jwt-como-mejora-opcional.md). Con el token de acceso caducado, la API responde `401`. El frontend llama entonces una vez a `POST /api/v1/auth/refresh` y repite la petición; las peticiones que caducan a la vez comparten esa renovación. Si la renovación también falla, lleva a `/login?redirectTo=<página>` y, tras iniciar sesión, vuelve a esa página si pertenece a la aplicación.

No se fijan `Secure` ni `SameSite` en las cookies de sesión; la protección frente a peticiones forjadas la aporta CSRF. Marcar `Secure` queda pendiente del despliegue con HTTPS.

## Motivo y consecuencias

Desactivar CSRF simplificaría las peticiones, pero eliminaría esta protección con el transporte por cookies elegido. Autenticar exclusivamente mediante una cabecera de autorización permitiría revisar la necesidad de CSRF en esos endpoints, a cambio de cambiar el manejo de credenciales.

Se acepta una petición inicial y una cabecera adicional en React y los clientes de API. Las escrituras sin un token CSRF válido reciben `403`; los `GET` se mantienen sin efectos de escritura. Esta protección no requiere nuevas entidades ni tablas.

## Aplicación

La configuración y sus pruebas se consultan en [SecurityConfig](../../backend/src/main/java/es/codeurjc/infracture/security/SecurityConfig.java) y [SecurityConfigTests](../../backend/src/test/java/es/codeurjc/infracture/security/SecurityConfigTests.java). `GET /api/v1/auth/csrf` está implementado en [AuthController](../../backend/src/main/java/es/codeurjc/infracture/identity/api/AuthController.java): permite acceso anónimo y devuelve `204` con la cookie `XSRF-TOKEN` cuando el cliente aún no la tiene.

En React, [`apiFetch`](../../frontend/app/shared/api/api-fetch.ts) solicita esa cookie antes de la primera escritura y añade la cabecera. Las duraciones se definen en [TokenType](../../backend/src/main/java/es/codeurjc/infracture/security/jwt/TokenType.java). [JwtTokenProvider](../../backend/src/main/java/es/codeurjc/infracture/security/jwt/JwtTokenProvider.java) recibe un `Clock`, de modo que las pruebas comprueban la caducidad adelantando el reloj sin esperar. Las cubren [JwtTokenProviderTests](../../backend/src/test/java/es/codeurjc/infracture/security/jwt/JwtTokenProviderTests.java) y [AuthenticationApiSystemTests](../../backend/src/test/java/es/codeurjc/infracture/identity/api/AuthenticationApiSystemTests.java), junto con las pruebas de integración y navegador del frontend de la [issue #74](https://github.com/codeurjc-students/2026-INFRACTURE/issues/74).

## Referencias

- [Spring Security: CSRF y aplicaciones sin estado](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html#csrf-and-stateless-browser-applications).
- [Spring Security: integración CSRF con SPA](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa).
- [Decisiones de cuenta y sesión de Fase 3](../PHASE_3_SPEC.md#permisos-cuenta-e-imágenes).
