---
status: accepted
date: 2026-10-15
---

# Ejecuciones ligadas a una revisión inmutable

## Contexto

Un escenario puede cambiar entre su validación y el arranque, o después de terminar un experimento. Si la ejecución leyera el grafo editable en cada paso, podría arrancar un diseño distinto del validado o mostrar un historial modificado por ediciones posteriores.

## Decisión

`Run` debe guardar, validar y ejecutar la misma `Scenario revision`. La reserva comprueba esa revisión y crea un `Execution snapshot` inmutable con sus `ExecutedComponent` y `ExecutedConnection`.

Desde la reserva hasta terminar la limpieza, el escenario permanece en lectura. Una vez desbloqueado puede editarse, conservando la independencia del snapshot y de las identidades históricas de cada ejecución.

## Motivo y consecuencias

Ejecutar siempre el grafo actual evitaría la copia histórica, pero permitiría cambios entre validación, arranque y consulta de resultados. Permitir edición durante el experimento exigiría definir cómo sincronizar dos diseños con ciclos de vida distintos.

Se acepta conservar una copia y coordinar guardado y reserva para relacionar las evidencias con el contenido exacto ejecutado. Esto aporta trazabilidad del diseño y sus parámetros; no garantiza resultados idénticos de rendimiento entre ejecuciones. Los conflictos se resuelven antes del arranque.

## Aplicación

Es una decisión de diseño aceptada cuya implementación está pendiente al registrar este ADR. Los criterios permanecen en [Grafo, catálogo y contratos](../PHASE_3_SPEC.md#grafo-catálogo-y-contratos) y [Ejecución, observación y eliminación](../PHASE_3_SPEC.md#ejecución-observación-y-eliminación). Los términos se definen en el [glosario](../../GLOSSARY.md#ejecución-e-historial).
