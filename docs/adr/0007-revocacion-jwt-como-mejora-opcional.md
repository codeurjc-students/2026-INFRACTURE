---
status: proposed
date: 2026-10-17
---

# Revocación de JWT como mejora opcional de seguridad

## Contexto

La API autentica mediante JWT de acceso y renovación en cookies `HttpOnly`. El logout actual borra `AuthToken` y `RefreshToken`, limpia el contexto de seguridad de la petición y mantiene la protección CSRF. El alumno ha decidido conservar esta implementación sin estado adicional de revocación por sesión.

## Sugerencia de la IA

Codex propuso añadir revocación en el servidor como una mejora de seguridad: impediría reutilizar copias de los tokens después del logout. Es una sugerencia de la IA, pendiente de valorar con el profesor; no es un requisito aprobado ni una implementación incorporada.

## Motivo y coste

El navegador deja de enviar las credenciales al recibir el logout. Una copia de un JWT continúa siendo válida hasta su caducidad; una copia del token de renovación permite obtener accesos mientras siga vigente. El logout no invalida esas copias en el servidor.

La mejora propuesta permitiría identificar cada login y rechazar sus tokens tras cerrar sesión. Requiere almacenar estado en el servidor y comprobarlo al validar las peticiones.

## Consulta pendiente con el profesor

Valorar si la protección adicional justifica incorporar revocación como una futura ampliación. La [issue #74](https://github.com/codeurjc-students/2026-INFRACTURE/issues/74) mantiene el logout por borrado de cookies y la comprobación de caducidad; no exige revocar JWT todavía válidos tras cerrar sesión.

La implementación actual se consulta en [UserLoginService](../../backend/src/main/java/es/codeurjc/infracture/security/jwt/UserLoginService.java) y [AuthController](../../backend/src/main/java/es/codeurjc/infracture/identity/api/AuthController.java).
