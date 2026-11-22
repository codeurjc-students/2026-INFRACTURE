---
status: accepted
date: 2026-11-06
---

# Bucle de reconciliación con una vuelta programada y bloqueo de fila

## Contexto

El [ADR 0013](0013-motor-de-ejecucion-con-docker-compose.md) fija que un `ExecutionReconciler` compara la fase deseada de la ejecución con la real y actúa hasta igualarlas. Faltaba decidir cómo se ejecuta ese bucle y cómo convive con las peticiones que cambian la misma ejecución o el mismo escenario.

Hay varios actores a la vez. El usuario pulsa Run, Stop o guarda el grafo desde una o varias pestañas. El bucle arranca, vigila y limpia. Además, cada paso de Docker puede tardar minutos: descargar imágenes, `up --wait` o `down -v`. ID22 exige que ninguna transacción SQL quede abierta esperando a Docker. Sin una regla de orden, Stop podría sobrescribir la fase que el bucle acaba de escribir, o un guardado podría colarse entre la validación y la reserva de Run.

## Decisión

- **Una sola vuelta programada.** `reconcile()` lleva `@Scheduled(fixedDelayString = "${infracture.execution.reconcile-interval:2s}")`. Con `fixedDelay`, la siguiente vuelta empieza 2 s después de que termine la anterior, así que dos vueltas nunca se solapan y el bucle no necesita bloqueos en memoria.
- **Cada vuelta tiene tres tiempos.** (1) Una transacción corta bloquea la fila de la ejecución activa (`findForUpdateByReleasedAtIsNull`, `PESSIMISTIC_WRITE`, que en PostgreSQL es `SELECT … FOR UPDATE`), decide el siguiente paso y confirma. (2) Como mucho un paso de Docker, fuera de cualquier transacción. (3) Otra transacción corta vuelve a bloquear la fila por su id y guarda el resultado.
- **Stop solo apunta la causa.** Bloquea la misma fila, registra la causa y responde enseguida. Si el bucle está en medio de una transacción, Stop espera a que termine, y al revés.
- **Durante el arranque, Stop se consulta sin bloqueo.** Mientras espera a `up --wait`, el motor pregunta cada segundo con `existsByIdAndStopCauseIsNotNull`, una lectura simple, para que Stop no tenga que esperar a esa consulta.
- **Guardar y Run bloquean la misma fila del escenario.** `ScenarioService.lockOwnScenario` usa `findForUpdateByIdAndProjectId`. Si gana el guardado, Run ve la revisión nueva y responde `409`. Si gana Run, el guardado ve la reserva y responde `409`. El índice único `ux_executions_single_active` resuelve la carrera entre dos escenarios distintos ([ADR 0015](0015-una-ejecucion-por-instancia-en-local.md)).
- **Las pruebas pueden apagar el bucle.** `infracture.execution.reconciler.enabled=false` desactiva `ExecutionScheduling`, y la prueba llama a `reconcile()` a mano.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Un hilo o `@Async` por ejecución | Habría varios hilos tocando la misma ejecución, con bloqueos en memoria que se pierden al reiniciar. Con una sola ejecución por instancia, no aporta nada. |
| Cola de mensajes (RabbitMQ) o eventos de Spring | Añade una pieza más a la plataforma. Un evento perdido en una caída deja la ejecución sin dueño. El bucle, en cambio, vuelve a leer el estado en cada vuelta. |
| Escuchar `docker events` | Exige abrir más rutas en `docker-proxy`. Los eventos ocurridos mientras el backend estaba caído se pierden, así que de todos modos haría falta una vuelta de reconciliación. |
| Bloqueo optimista (`@Version`) | Stop y el bucle fallarían por conflicto y tendrían que reintentar. Con el bloqueo pesimista, el segundo solo espera unos milisegundos y nunca pierde lo que escribió el primero. |
| Mantener la transacción durante el paso de Docker | Ocuparía una conexión y un bloqueo durante minutos: Stop esperaría a que acabe la descarga de imágenes. ID22 lo prohíbe. |
| Quartz o ShedLock | Sirven para coordinar varios backends o tareas persistentes. Infracture Local tiene un solo backend, así que bastan el planificador de Spring y la base de datos. |

Se acepta que la reacción tarda hasta 2 s más lo que dure el paso de Docker en curso, y que la ejecución avanza un paso por vuelta. Un arranque lento retrasa la vuelta siguiente, lo cual solo es aceptable porque hay una ejecución por instancia. Para varias ejecuciones simultáneas haría falta otro diseño ([ADR 0015](0015-una-ejecucion-por-instancia-en-local.md), punto 3). A cambio, cada vuelta parte del estado guardado, así que el mismo código sirve para el funcionamiento normal y para recuperarse de una caída ([ADR 0027](0027-ejecuciones-abiertas-por-un-proceso-anterior.md)).

## Aplicación

El bucle está en [`ExecutionReconciler`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionReconciler.java) y se activa en [`ExecutionScheduling`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionScheduling.java). Los bloqueos están en [`ExecutionRepository`](../../backend/src/main/java/es/codeurjc/infracture/execution/persistence/ExecutionRepository.java) y [`ScenarioRepository`](../../backend/src/main/java/es/codeurjc/infracture/project/persistence/ScenarioRepository.java), y los usan [`ExecutionService`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionService.java) y [`ScenarioService`](../../backend/src/main/java/es/codeurjc/infracture/project/application/ScenarioService.java). El bloqueo del escenario entró con el guardado del grafo (#79, 31 de octubre) y Run lo usa desde la reserva (#86); el bucle se implementó en el commit `2954f94` (#86). Lo comprueban [`ExecutionReconcilerTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ExecutionReconcilerTests.java) (`cancelsTheStartWhenStopArrivesWhileTheNodesStart`), [`ExecutionServiceTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ExecutionServiceTests.java) (`turnsALostRaceForTheReservationIntoAConflict`, `recordsStopWithoutTouchingDocker`) y [`ExecutionApiSystemTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/api/ExecutionApiSystemTests.java) (`ordersASaveAndARunOfTheSameRevisionSoTheSnapshotIsOneSavedRevision`). El funcionamiento está en las secciones [9.2](../EXECUTION_ARCHITECTURE.md#92-qué-ocurre-al-pulsar-run) y [9.5](../EXECUTION_ARCHITECTURE.md#95-fase-deseada-y-limpieza-por-etiqueta) de la arquitectura de ejecución.

## Referencias

- [Spring Framework: tareas programadas](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-scheduled).
- [Spring Data JPA: bloqueos](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
- [PostgreSQL: bloqueos de fila](https://www.postgresql.org/docs/current/explicit-locking.html#LOCKING-ROWS).
- [Kubernetes: el patrón controlador](https://kubernetes.io/docs/concepts/architecture/controller/).
