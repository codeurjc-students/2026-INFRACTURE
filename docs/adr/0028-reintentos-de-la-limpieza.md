---
status: accepted
date: 2026-11-10
---

# Reintentos de la limpieza y etiqueta de instancia

## Contexto

Para liberar la reserva, el motor tiene que retirar todo lo que creó: contenedores, red y volúmenes ([ADR 0013](0013-motor-de-ejecucion-con-docker-compose.md)). Esa limpieza puede fallar. Docker puede no responder, un volumen puede seguir en uso o el proxy puede rechazar una llamada. El [ADR 0005](0005-recuperacion-sin-reanudacion-automatica.md) acepta que entonces `Run` quede bloqueado, siempre que el problema se vea y se pueda reintentar sin efectos duplicados.

Quedaban tres preguntas. ¿Cuántas veces se reintenta solo? ¿Cambia un fallo de limpieza el resultado del experimento? ¿Cómo se evita borrar contenedores de otra instalación de Infracture que use el mismo Docker, como las pruebas de integración junto al backend de desarrollo?

## Decisión

- **El fallo de limpieza va aparte de la causa.** Se guarda en `cleanup_failures` (intentos seguidos), `cleanup_error` (el último error de Docker) y `cleanup_failed_at`. No es una `StopCause` y no cambia el resultado: un experimento detenido con Stop termina `COMPLETED` aunque su limpieza necesitara varios intentos ([ADR 0026](0026-la-causa-de-parada-mas-grave-decide-el-resultado.md)).
- **Tres intentos automáticos con pausas cada vez más largas.** Tras el fallo número *n*, el siguiente intento espera *n* × 10 s (`infracture.execution.cleanup-retry-pause`): 10 s y después 20 s. Tras el tercer fallo (`AUTOMATIC_CLEANUP_ATTEMPTS = 3`), `cleanupRetryNeeded` pasa a `true` y el motor deja de intentarlo.
- **Después decide el dueño.** El canvas muestra el error y el botón **Retry cleanup**, que es el mismo Stop: pone el contador a cero y da otra ronda de intentos. Un reinicio del backend también da una ronda nueva ([ADR 0027](0027-ejecuciones-abiertas-por-un-proceso-anterior.md)).
- **Cada intento repite la limpieza entera.** Ejecuta `down -v` y después busca y retira por etiqueta. No se guarda en qué paso iba, y solo se libera la reserva cuando no queda nada.
- **Etiqueta de instancia.** Todo recurso lleva `io.infracture.instance=<id>` e `io.infracture.execution=<n>`, y el proyecto Compose se llama `infracture-<id>-exec-<n>`. La limpieza filtra por las dos etiquetas a la vez. El id viene de `INFRACTURE_INSTANCE_ID` (por defecto `local`) y tiene que ser un nombre válido para Compose.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Marcar la ejecución como `FAILED` si la limpieza falla | Confunde dos cosas: cómo terminó el experimento y si Docker dejó retirar sus restos. Un experimento correcto aparecería como fallido. |
| Reintentar sin límite | Si el error es permanente, como un volumen bloqueado por otro proceso, el motor llenaría el log y llamaría a Docker cada pocos segundos para siempre. |
| No reintentar solo, esperar siempre al dueño | Muchos fallos son pasajeros, como un Docker Desktop que arranca o un contenedor que tarda en parar. Obligar a pulsar un botón en cada uno sería molesto. |
| Liberar la reserva aunque queden restos | Rompe la regla de que liberada implica cero recursos con la etiqueta (`ReleasedLeavesNothing` en el modelo TLA+). El siguiente experimento podría compartir red o nombres con el anterior. |
| Filtrar solo por la etiqueta de ejecución | Los ids de ejecución empiezan en 1 en cada instalación. Las pruebas y el backend de desarrollo, sobre el mismo Docker, borrarían cada uno la ejecución del otro. |

Se acepta que, tras tres fallos, el escenario se queda en solo lectura y `Run` bloqueado hasta que alguien actúe. Es el estado sin salida que el ADR 0005 ya aceptaba, pero visible y con su causa. A cambio, el resultado del experimento siempre dice lo que pasó en él, la limpieza nunca toca contenedores ajenos y reintentarla no duplica nada.

## Aplicación

Los reintentos se implementaron en el commit `1d68c87` (#88), con la migración [`V13__track_cleanup_failures.sql`](../../backend/src/main/resources/db/migration/V13__track_cleanup_failures.sql), y el canvas los muestra desde `3e4ae02`. La etiqueta de instancia entró con el plan de Compose en `c20b1ba` (#86). Las reglas están en `isCleanupDue` y `recordCleanupFailure` de [`Execution`](../../backend/src/main/java/es/codeurjc/infracture/execution/domain/Execution.java), las etiquetas en `labels` y `projectName` de [`PlanCompiler`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/PlanCompiler.java), y la limpieza en `down` de [`ComposeRuntimeEngine`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ComposeRuntimeEngine.java). Lo comprueban [`ExecutionTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/domain/ExecutionTests.java) (`tracksAFailedCleanupWithoutChangingHowTheExecutionEnds`, `waitsLongerAfterEachFailedCleanupAndThenForTheOwner`), [`ExecutionReconcilerTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ExecutionReconcilerTests.java) (`stopsRetryingAfterTheAutomaticAttemptsUntilStopIsPressedAgain`), [`PlanCompilerTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/PlanCompilerTests.java) (`namesTheComposeProjectAfterTheInstanceSoInstancesSharingDockerStayApart`) y [`ComposeRuntimeEngineIntegrationTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ComposeRuntimeEngineIntegrationTests.java) (`failsTheCleanupOfAVolumeInUseAndRetriesItWithoutTouchingOtherContainers`, `leavesContainersOfOtherExecutionsAlone`). El funcionamiento está en las secciones [9.5](../EXECUTION_ARCHITECTURE.md#95-fase-deseada-y-limpieza-por-etiqueta) y [10.2](../EXECUTION_ARCHITECTURE.md#102-fase-causa-y-resultado) de la arquitectura de ejecución.

## Referencias

- [Docker: etiquetas de objetos](https://docs.docker.com/engine/manage-resources/labels/).
- [Docker Compose: `down`](https://docs.docker.com/reference/cli/docker/compose/down/).
- [Amazon Builders' Library: Timeouts, retries and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/).
