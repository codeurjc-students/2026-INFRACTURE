---
status: accepted
date: 2026-11-14
---

# Ejecuciones abiertas por un proceso anterior

## Contexto

El [ADR 0005](0005-recuperacion-sin-reanudacion-automatica.md) fija la política: tras una caída, la ejecución abierta se marca como interrumpida, se limpia y nunca se reanuda. Faltaba el mecanismo. ¿Cómo sabe el backend que una ejecución abierta la dejó un proceso anterior y no la acaba de reservar él? ¿Qué se guarda como evidencia? ¿Qué pasa si hay un segundo reinicio a mitad de la limpieza?

Hay una carrera concreta. La primera vuelta del `ExecutionReconciler` puede empezar antes de que la aplicación termine de arrancar. Si la comprobación solo se hiciera una vez al arrancar, una reserva antigua en `RESERVED` podría arrancar como si fuera nueva.

## Decisión

- **El reconciler guarda el instante en que arranca su proceso** (`processStartedAt`, tomado de su `Clock` en el constructor). Solo hay un backend, así que una ejecución abierta con `createdAt` anterior a ese instante la dejó otro proceso.
- **Se comprueba en cada vuelta, sobre la fila bloqueada** ([ADR 0025](0025-bucle-de-reconciliacion-con-bloqueo-de-fila.md)) y antes de decidir el siguiente paso. Así una reserva antigua nunca llega a `STARTING`, aunque la vuelta empiece antes de que acabe el arranque.
- **Se registra `INTERRUPTION`**, la causa más grave ([ADR 0026](0026-la-causa-de-parada-mas-grave-decide-el-resultado.md)), con `interrupted_at` e `interrupted_phase`: cuándo la encontró y en qué fase estaba. Solo se guarda el primer reinicio. La API los devuelve como `interruptedAt` e `interruptedPhase`, y el canvas avisa al dueño.
- **Después sigue el mismo camino que Stop**: para k6 y guarda su resumen si aún no lo tenía, ejecuta `down -v` y limpia por etiqueta de instancia y de ejecución.
- **Cada ejecución se interrumpe una vez por proceso.** Un conjunto en memoria (`recovered`) recuerda las ya tratadas, y solo se añaden cuando la transacción ha confirmado. Si la transacción falla, la vuelta siguiente lo intenta de nuevo.
- **Cada reinicio da una ronda nueva de reintentos** a una limpieza que había fallado ([ADR 0028](0028-reintentos-de-la-limpieza.md)).
- **La base de datos protege la coherencia:** `interrupted_at` e `interrupted_phase` van juntos y solo existen con `stop_cause = 'INTERRUPTION'`.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Revisar una sola vez al arrancar (`ApplicationReadyEvent`) | La primera vuelta programada puede llegar antes y arrancar una reserva antigua. Tampoco se repetiría si esa transacción fallase. |
| Guardar en cada fila un id del proceso, o un latido periódico (*lease*) | Necesita una columna más y escribir en cada vuelta. Solo sería necesario con varios backends, que Infracture Local no tiene ([ADR 0015](0015-una-ejecucion-por-instancia-en-local.md)). |
| Preguntar a Docker qué contenedores siguen vivos | No dice si la ejecución es de este proceso o de otro. Además, si Docker no responde, la reserva antigua quedaría sin clasificar. |
| Reanudar la ejecución | Ya lo descarta el ADR 0005. |

Se acepta que la regla se apoya en que solo hay un backend y en el reloj de la máquina: `createdAt` y `processStartedAt` salen del mismo reloj del sistema. Si el reloj retrocediera entre dos procesos, una ejecución antigua podría parecer nueva. Con varios backends habría que pasar a un latido o a un id de proceso. A cambio, no hacen falta columnas ni escrituras extra, y la recuperación es la misma vuelta del bucle con una causa más.

## Aplicación

Se implementó en los commits `864c731` y `a9b8f69` (#90), con la migración [`V15__record_execution_interruption.sql`](../../backend/src/main/resources/db/migration/V15__record_execution_interruption.sql). La detección está en `isLeftover` de [`ExecutionReconciler`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionReconciler.java), y el registro en `Execution.interrupt`. Lo comprueban [`ExecutionReconcilerTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ExecutionReconcilerTests.java) (`neverStartsAReservationLeftByAnEarlierProcess`, `interruptsAStartCutShortByARestartAndCleansItUp`, `keepsRunBlockedWhileDockerCannotCleanUpAfterARestartAndDuplicatesNothingOnTheNext`), [`ExecutionTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/domain/ExecutionTests.java) (`recordsWhenAndInWhichPhaseTheFirstRestartFoundItAndKeepsThatOnLaterOnes`) y [`ExecutionRecoveryApiSystemTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/api/ExecutionRecoveryApiSystemTests.java). El funcionamiento, incluido el límite conocido de Docker Desktop, está en la [sección 10.5 de la arquitectura](../EXECUTION_ARCHITECTURE.md#105-reinicio-del-backend).

## Referencias

- [Java: `java.time.Clock`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Clock.html).
- [Martin Kleppmann: How to do distributed locking](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html), sobre los límites de los *leases* y de los relojes cuando varios procesos compiten.
- [Kubernetes: el patrón controlador](https://kubernetes.io/docs/concepts/architecture/controller/).
