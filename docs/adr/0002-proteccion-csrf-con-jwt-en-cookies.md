---
status: accepted
date: 2026-10-15
---

# Protección CSRF en la API REST

## Contexto

React consume la API REST de Infracture con JWT en cookies de autenticación `HttpOnly`. El navegador puede enviar esas cookies automáticamente, por lo que una petición necesita una protección adicional frente a operaciones forjadas desde otra web. Usar REST y sesiones sin estado no elimina por sí solo ese riesgo.

## Decisión

Mantener CSRF en las operaciones de escritura de la API, incluidos registro, login, renovación y logout, con la configuración `.csrf(csrf -> csrf.spa())` de Spring Security.

El cliente leerá la cookie `XSRF-TOKEN` y enviará su valor en la cabecera `X-XSRF-TOKEN`. Esta cookie es legible por JavaScript para preparar la petición; las cookies JWT conservan `HttpOnly`. El token CSRF y los JWT tienen funciones independientes.

Antes de la primera escritura, el cliente obtendrá la cookie mediante un `GET` público. La obtención del token y el envío de la cabecera se centralizarán en el cliente HTTP de React.

## Motivo y consecuencias

Desactivar CSRF simplificaría las peticiones, pero eliminaría esta protección con el transporte por cookies elegido. Autenticar exclusivamente mediante una cabecera de autorización permitiría revisar la necesidad de CSRF en esos endpoints, a cambio de cambiar el manejo de credenciales.

Se acepta una petición inicial y una cabecera adicional en React y los clientes de API. Las escrituras sin un token CSRF válido reciben `403`; los `GET` se mantienen sin efectos de escritura. Esta protección no requiere nuevas entidades ni tablas.

## Aplicación

La configuración y sus pruebas se consultan en [SecurityConfig](../../backend/src/main/java/es/codeurjc/infracture/security/SecurityConfig.java) y [SecurityConfigTests](../../backend/src/test/java/es/codeurjc/infracture/security/SecurityConfigTests.java). `GET /api/v1/auth/csrf` está implementado en [AuthController](../../backend/src/main/java/es/codeurjc/infracture/identity/api/AuthController.java): permite acceso anónimo y devuelve `204` con la cookie `XSRF-TOKEN` cuando el cliente aún no la tiene. La integración del frontend y la verificación del flujo completo con cuentas reales siguen pendientes en la [issue #74](https://github.com/codeurjc-students/2026-INFRACTURE/issues/74).

## Referencias

- [Spring Security: CSRF y aplicaciones sin estado](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html#csrf-and-stateless-browser-applications).
- [Spring Security: integración CSRF con SPA](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa).
- [Decisiones de cuenta y sesión de Fase 3](../PHASE_3_SPEC.md#permisos-cuenta-e-imágenes).
