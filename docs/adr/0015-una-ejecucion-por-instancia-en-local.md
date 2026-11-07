---
status: accepted
date: 2026-11-05
---

# Una ejecución por instancia en Infracture Local

## Contexto

Infracture 0.1.0 es una instancia local: corre en el ordenador de quien la instala, con su propio Docker. Un experimento con los seis tipos de componente puede necesitar varios contenedores, además de la plataforma. Dos experimentos a la vez compiten por la misma CPU y memoria, y sus medidas se influyen.

La spec ya fija una sola `Execution` reservada, arrancando, activa o limpiándose en toda la instancia, aunque la pidan usuarios distintos (US079, ID21). El objetivo a largo plazo es otro: que cada usuario pueda tener su propio experimento en marcha. Ese escenario corresponde a Infracture Cloud, el segundo TFG, que desplegará la plataforma en AWS.

## Decisión

- **En 0.1.0, una ejecución por instancia.** Un segundo `Run` se rechaza con `409`, aunque sea de otro usuario, hasta que la ejecución anterior termine su limpieza.
- **La garantiza la base de datos.** El índice único parcial `ux_executions_single_active` (`ON executions ((TRUE)) WHERE released_at IS NULL`) impide dos ejecuciones sin liberar, también con peticiones simultáneas.
- **El mecanismo se conserva para el futuro.** Una ejecución por usuario es el mismo índice con otra clave (`owner_id` en lugar de `TRUE`). El resto de reglas (revisión exacta, snapshot, limpieza por etiqueta) no dependen del número de ejecuciones.

## Motivo y consecuencias

| Alternativa | Por qué no en 0.1.0 |
| --- | --- |
| Una ejecución por usuario | En una instancia local suele haber un solo usuario y la CPU es la misma, así que no aporta nada. Además exige un límite global, que ya no cabe en un índice único, y un motor que atienda varias ejecuciones a la vez. |
| Sin límite | Varios experimentos podrían agotar la CPU y la memoria de la máquina y harían las medidas poco fiables. |
| Límite comprobado solo en Java | Dos peticiones simultáneas podrían pasar la comprobación antes de que se guarde ninguna. |

Se acepta que en una instancia compartida los usuarios se turnen para ejecutar. A cambio, cada experimento tiene la máquina para él, sus medidas se pueden interpretar y la limpieza y la recuperación tras una caída solo tratan una ejecución.

Para pasar a una ejecución por usuario en Infracture Cloud harán falta:

1. Una columna `owner_id` en `executions` y el índice único parcial sobre ella.
2. Un límite global de ejecuciones simultáneas, o cuotas de CPU y memoria, protegido con un bloqueo en la base de datos.
3. Un `ExecutionReconciler` que atienda varias ejecuciones sin que un arranque lento bloquee a las demás.
4. Cambiar US079 e ID21 de la spec y explicar cómo se aíslan las medidas de experimentos simultáneos.

## Aplicación

Concreta US079 e ID21 de la [spec](../PHASE_3_SPEC.md) y la reserva de la [arquitectura de ejecución](../EXECUTION_ARCHITECTURE.md#92-qué-ocurre-al-pulsar-run). Se implementa en #86 con la migración `V10__create_executions.sql`. El cambio a una ejecución por usuario queda para Infracture Cloud.
