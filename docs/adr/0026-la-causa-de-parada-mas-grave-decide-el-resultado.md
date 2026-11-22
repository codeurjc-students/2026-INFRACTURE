---
status: accepted
date: 2026-11-05
---

# La causa de parada más grave decide el resultado

## Contexto

Una ejecución puede terminar por varios motivos que se solapan: el usuario pulsa Stop, se agota el tiempo, falla Docker o el backend se reinicia. El [ADR 0013](0013-motor-de-ejecucion-con-docker-compose.md) hace que todos pidan lo mismo, la fase `STOPPED`, y el [ADR 0005](0005-recuperacion-sin-reanudacion-automatica.md) exige que una interrupción nunca acabe como éxito. Faltaba decidir qué se guarda cuando llegan dos motivos: por ejemplo, Stop durante el arranque y después un fallo de salud.

La regla más natural parecía «manda la primera causa». Antes de programarla, el diseño se comprobó con un modelo en TLA+ ([sección 10.3 de la arquitectura](../EXECUTION_ARCHITECTURE.md#103-cómo-se-comprobó-el-diseño-con-tla)). Con esa regla, el verificador TLC encontró un camino de cinco pasos en el que un arranque fallido terminaba como éxito: Run, compilar, Stop, falla el arranque, limpieza. Encontró otro en el que una caída durante la limpieza de un Stop también acababa como éxito.

## Decisión

- **La fase y la causa se guardan por separado.** La fase (`RESERVED` → `STARTING` → `RUNNING` → `STOPPING` → `RELEASED`) dice qué hace el motor y solo avanza en orden. La causa (`stopCause`) dice por qué termina.
- **Cada causa tiene una gravedad** en [`StopCause`](../../backend/src/main/java/es/codeurjc/infracture/execution/domain/StopCause.java): `STOP_REQUESTED` y `TIME_LIMIT` valen 1, `FAILURE` vale 2 e `INTERRUPTION` vale 3.
- **Una causa nueva sustituye a la guardada solo si es más grave.** Entre dos igual de graves se queda la primera, así que Stop después del tiempo límite conserva `TIME_LIMIT`. Después de liberar la ejecución, ninguna causa cambia nada.
- **El resultado sale de la causa** al liberar la ejecución: `COMPLETED`, `FAILED` o `INTERRUPTED`.
- **Un fallo de limpieza no es una causa**, y se guarda aparte ([ADR 0028](0028-reintentos-de-la-limpieza.md)).

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Manda la primera causa | TLC encontró dos caminos en los que un fallo o una caída terminaban como éxito, lo que rompe el ADR 0005. |
| Manda la última causa | Un Stop pulsado después de un fallo lo convertiría en éxito. El resultado dependería del orden de llegada, no de lo que pasó. |
| Una sola columna de estado con todas las combinaciones | Mezcla «qué hace el motor» con «por qué termina». Cada combinación nueva (arrancando con Stop pedido, limpiando tras un fallo…) sería un estado más, con sus transiciones. |
| Guardar todas las causas en una lista | Es más información, pero el resultado seguiría necesitando una regla para elegir una. La primera causa de fallo ya se guarda en `failureReason`. |

Se acepta que el resultado no cuenta el orden de lo ocurrido. Por ejemplo, Stop seguido de un fallo da `FAILED`. A cambio, «una ejecución con problemas nunca acaba como éxito» se cumple en todas las combinaciones de Stop, tiempo, fallo y caída. Con esta regla, TLC recorrió los 2.160 estados alcanzables del modelo con dos escenarios sin encontrar ninguno que rompiera las cuatro reglas.

Se acepta también que el modelo no es el código. Por eso cada camino que encontró TLC se convirtió en una prueba.

## Aplicación

La regla está en `Execution.recordCause` y en [`StopCause`](../../backend/src/main/java/es/codeurjc/infracture/execution/domain/StopCause.java). El modelo está en [`docs/models/execution-lifecycle`](../models/execution-lifecycle), con [`first-cause.cfg`](../models/execution-lifecycle/first-cause.cfg) y [`worst-cause.cfg`](../models/execution-lifecycle/worst-cause.cfg) para comparar las dos reglas. Los dos caminos de TLC son pruebas de [`ExecutionTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/domain/ExecutionTests.java): `failsWhenStartUpFailsAfterStopWasRequested` e `isInterruptedWhenTheBackendStopsDuringTheCleanupOfAStop`; `keepsTheTimeLimitAsTheCauseWhenStopFollowsButLetsAFailureWin` cubre el empate. Se documentó en el commit `4111cd4` y se implementó con la `Execution` de #86. Las tablas de fases, causas y resultados están en la [sección 10.2 de la arquitectura](../EXECUTION_ARCHITECTURE.md#102-fase-causa-y-resultado).

## Referencias

- [Leslie Lamport: The TLA+ Home Page](https://lamport.azurewebsites.net/tla/tla.html).
- [Learn TLA+](https://learntla.com/).
- [Newcombe et al.: How Amazon Web Services Uses Formal Methods (CACM, 2015)](https://lamport.azurewebsites.net/tla/formal-methods-amazon.pdf).
