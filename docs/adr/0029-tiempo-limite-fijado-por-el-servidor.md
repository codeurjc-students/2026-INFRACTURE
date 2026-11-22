---
status: accepted
date: 2026-11-13
---

# Tiempo límite fijado por el servidor

## Contexto

ID25 de la [spec](../PHASE_3_SPEC.md#ejecución-observación-y-eliminación) fija un máximo inicial de 30 minutos por experimento, configurable y aplicado por el backend aunque no haya ningún navegador abierto. Quedaban por concretar cuándo empieza el reloj, qué esperas independientes tiene cada paso de Docker y cómo se registra el final.

La primera versión del motor (#86) contaba los 30 minutos desde la creación de la ejecución. Así, una descarga lenta de imágenes le quitaba tiempo al experimento. Además, el límite se trataba como un fallo, cuando un experimento que llega al final de su tiempo ha terminado bien.

## Decisión

- **El reloj empieza en `RUNNING`.** Cuando todos los nodos están sanos, `Execution.markRunning` guarda `deadlineAt = ahora + max-duration` (columna `deadline_at`). El arranque tiene su propia espera de 5 minutos.
- **El plazo es fijo.** Una vez guardado no cambia: cerrar la pestaña, reconectar o cambiar la configuración no lo mueve. La base de datos exige que toda ejecución en `RUNNING` tenga plazo.
- **Lo comprueba el bucle, antes de llamar a Docker.** En cada vuelta, el `ExecutionReconciler` compara el plazo con su `Clock` sobre la fila bloqueada ([ADR 0025](0025-bucle-de-reconciliacion-con-bloqueo-de-fila.md)). Así el límite se cumple aunque Docker no responda.
- **Tiene su propia causa, `TIME_LIMIT`**, tan grave como Stop ([ADR 0026](0026-la-causa-de-parada-mas-grave-decide-el-resultado.md)). Termina `COMPLETED` y sigue el mismo camino de limpieza que Stop.
- **La interfaz solo lo muestra.** La API devuelve `deadlineAt` y `secondsLeft`, calculado con el reloj del servidor. El canvas nunca decide cuándo parar.
- **Configuración.** `infracture.execution.max-duration` (por defecto `30m`). Las pruebas lo reducen a un segundo.
- **Cada paso de Docker tiene además su propia espera**, independiente del límite: 5 minutos para arrancar los nodos o k6, y 2 minutos para parar k6, `ps`, `logs` y `down -v`. La tabla completa está en la [sección 10.4 de la arquitectura](../EXECUTION_ARCHITECTURE.md#104-tiempo-límite-y-esperas).

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Temporizador en el navegador | Cerrar la pestaña no es Stop (ID24): el experimento seguiría consumiendo la máquina sin límite. |
| Contar desde la creación (primera versión) | Una descarga o un arranque lentos se comen el tiempo del experimento, así que dos experimentos iguales tendrían duraciones útiles distintas. |
| Usar como límite la duración de la carga de k6 | Un escenario puede no tener carga, y los nodos siguen vivos cuando k6 termina. La duración de la carga es un parámetro del experimento ([ADR 0016](0016-limites-de-la-carga-constant-http.md)), no un tope de la plataforma. |
| Recalcular el plazo en cada vuelta desde `max-duration` | Cambiar la configuración y reiniciar movería el final de los experimentos que ya están en marcha. |
| Tratar el límite como `FAILURE` | El experimento no falló: llegó al final previsto. Su resultado tiene que poder interpretarse como uno completo. |

Se acepta que el final llega en la vuelta siguiente al plazo, es decir, hasta 2 s tarde más lo que dure el paso de Docker en curso. Se acepta también que la cuenta atrás del canvas avance sola entre dos consultas y se corrija con cada respuesta del servidor. A cambio, el límite no depende del navegador, se ve igual en todas las pestañas y sobrevive a un reinicio, porque está guardado en la fila. La migración pone a las ejecuciones que ya estaban en marcha el plazo que tenían: 30 minutos desde su creación.

## Aplicación

Se implementó en los commits `ba80be8` y `d94fadd` (#89), con la migración [`V14__add_execution_time_limit.sql`](../../backend/src/main/resources/db/migration/V14__add_execution_time_limit.sql). Las reglas están en `markRunning`, `isPastDeadline`, `reachTimeLimit` y `secondsLeft` de [`Execution`](../../backend/src/main/java/es/codeurjc/infracture/execution/domain/Execution.java), y la comprobación en `nextStep` de [`ExecutionReconciler`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionReconciler.java). El canvas lo muestra en [`execution-badge.tsx`](../../frontend/app/features/scenario-canvas/components/execution-badge.tsx). Lo comprueban [`ExecutionTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/domain/ExecutionTests.java) (`startsTheClockWhenRunningAndCompletesAtTheTimeLimit`, `keepsTheTimeLimitAsTheCauseWhenStopFollowsButLetsAFailureWin`), [`ExecutionReconcilerTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ExecutionReconcilerTests.java) (`stopsTheLoadAndCleansUpAtTheTimeLimitCountedFromRunning`, `appliesTheTimeLimitEvenWhenDockerCannotReportTheNodes`) y [`ExecutionTimeLimitApiSystemTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/api/ExecutionTimeLimitApiSystemTests.java).

## Referencias

- [Java: `java.time.Clock`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Clock.html).
- [Docker Compose: `up --wait` y `--wait-timeout`](https://docs.docker.com/reference/cli/docker/compose/up/).
- [Spring Boot: conversión de `Duration` en propiedades](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.conversion.durations).
