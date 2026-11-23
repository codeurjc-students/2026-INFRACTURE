---
status: accepted
date: 2026-11-23
---

# Sesiones por id de usuario y versión de token

## Contexto

Hasta la [issue #75](https://github.com/codeurjc-students/2026-INFRACTURE/issues/75) el correo no se podía cambiar, y los JWT identificaban a la cuenta por él. Al permitir editarlo aparecen dos problemas:

- Si Alice cambia su correo y otra persona se registra con el antiguo, un token anterior de Alice autenticaría a quien lo presenta como esa cuenta nueva.
- Un cambio de correo o contraseña debe terminar las demás sesiones de la cuenta, y un JWT firmado sigue siendo válido hasta caducar. El token de renovación dura 7 días.

## Decisión

- **El token identifica la cuenta por id y versión.** Los tokens de acceso y renovación llevan `sub` con el id del usuario y `ver` con su versión. No contienen el correo. El login sigue siendo con correo y contraseña.
- **La versión vive en `users.token_version`.** Cada petición y cada renovación cargan la cuenta por id y exigen que exista, esté activa y tenga la misma versión. Un `sub` o `ver` ausente o mal formado no autentica.
- **Correo y contraseña suben la versión una vez**, en la misma transacción que el cambio. La sesión que lo hace recibe cookies nuevas y las demás dejan de funcionar en su siguiente petición o renovación.
- **Nombre y nombre de usuario no tocan la versión**, porque no son credenciales.
- **El usuario autenticado conserva el correo actual como `getUsername()`.** Así, `Principal.getName()` y los servicios `getOwn*(email)` siguen funcionando, sin refactorizar la propiedad de proyectos, escenarios y ejecuciones.
- **Los flujos SSE guardan el id y la versión** con que se abrieron, y se cierran en su siguiente lectura si la cuenta ya no está activa, no existe o tiene otra versión.
- **Los cambios de cuenta bloquean la fila del usuario.** Hibernate escribe todas las columnas, y sin el bloqueo un guardado simultáneo podría devolver `token_version` a un valor anterior y revivir sesiones ya terminadas.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Mantener el correo en `sub` | Un token antiguo podría autenticar a quien registre el correo liberado. |
| Tabla de sesiones por dispositivo | Permitiría cerrar un solo dispositivo, pero añade una entidad, su limpieza y su gestión, que quedan fuera de esta issue. |
| Lista de tokens revocados | Hay que guardar cada token emitido o revocado hasta su caducidad. Una versión por cuenta resuelve el caso con un entero. |
| Cambiar la clave de firma | Terminaría las sesiones de todas las cuentas, no solo las de quien cambia sus credenciales. |
| Esperar a que caduquen los tokens | Una sesión robada seguiría renovándose hasta 7 días después de cambiar la contraseña. |

La validación no añade una consulta nueva, porque el filtro ya cargaba la cuenta en cada petición. Los tokens emitidos antes de este cambio dejan de ser válidos, igual que ocurre en cada reinicio del backend, y no requieren migración.

Se aceptan estos límites:

- La revocación es por cuenta: no se puede cerrar un único dispositivo.
- El logout sigue borrando solo las cookies. Revocar copias de un token tras cerrar sesión continúa como [mejora opcional](0007-revocacion-jwt-como-mejora-opcional.md).
- Una petición que ya pasó el filtro cuando se confirma el cambio termina con los datos que tenía.
- Un flujo SSE puede tardar hasta un latido, menos de 15 s, en cerrarse.

## Aplicación

La columna se añade en [`V18__add_user_token_version.sql`](../../backend/src/main/resources/db/migration/V18__add_user_token_version.sql). [`JwtTokenProvider`](../../backend/src/main/java/es/codeurjc/infracture/security/jwt/JwtTokenProvider.java) emite los tokens, y [`RepositoryUserDetailsService.loadSession`](../../backend/src/main/java/es/codeurjc/infracture/security/RepositoryUserDetailsService.java) aplica la regla para [`JwtRequestFilter`](../../backend/src/main/java/es/codeurjc/infracture/security/jwt/JwtRequestFilter.java) y [`UserLoginService`](../../backend/src/main/java/es/codeurjc/infracture/security/jwt/UserLoginService.java). [`UserService`](../../backend/src/main/java/es/codeurjc/infracture/identity/application/UserService.java) sube la versión, y [`ExecutionFeed`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionFeed.java) la comprueba en cada lectura del flujo.

Lo comprueban [`AccountApiSystemTests`](../../backend/src/test/java/es/codeurjc/infracture/identity/api/AccountApiSystemTests.java), incluido el token antiguo frente a una cuenta nueva con el correo liberado, y [`ExecutionApiSystemTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/api/ExecutionApiSystemTests.java) para el cierre del flujo SSE.

## Referencias

- [RFC 7519: claims registrados de JWT](https://www.rfc-editor.org/rfc/rfc7519#section-4.1).
- [Sesión con JWT en cookies y protección CSRF](0002-proteccion-csrf-con-jwt-en-cookies.md).
- [Decisiones de cuenta y sesión de Fase 3](../PHASE_3_SPEC.md#permisos-cuenta-e-imágenes).
