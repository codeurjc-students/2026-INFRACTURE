---
status: accepted
date: 2026-10-15
---

# Recuperación sin reanudación automática de experimentos

## Contexto

Después de una caída del backend pueden quedar contenedores activos y datos de progreso incompletos. El estado persistido y los recursos de Docker no forman una única transacción. Reanudar la carga sin reconciliarlos puede prolongar un experimento cuyo desarrollo durante la caída se desconoce.

## Decisión

Al reiniciar, identificar las ejecuciones inconclusas, registrar la interrupción y reconciliar sus recursos. Conservar el snapshot y las evidencias disponibles, detener la carga y limpiar los recursos temporales identificados de la ejecución.

Los nuevos `Run` permanecen bloqueados hasta completar la limpieza. Si falla o Docker no está disponible, el problema queda visible y la operación puede reintentarse de forma idempotente. Los borrados ya solicitados continúan su propio proceso de eliminación.

## Motivo y consecuencias

La reanudación automática reduciría la intervención del usuario, pero exigiría reconstruir un estado experimental fiable y decidir cómo interpretar el periodo sin control del backend. Se prioriza un cierre verificable, con una interrupción explícita en el historial y conservación de recursos ajenos.

El usuario deberá iniciar otro experimento después de la recuperación. Una limpieza correcta libera los recursos; no convierte la ejecución interrumpida en un éxito.

## Aplicación

Este ADR registra la política de recuperación ya acordada. Su implementación y verificación están pendientes al registrarlo. Los estados, casos de caída y criterios detallados permanecen en [Ejecución, observación y eliminación de la spec](../PHASE_3_SPEC.md#ejecución-observación-y-eliminación).
