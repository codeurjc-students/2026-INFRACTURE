---
status: accepted
date: 2026-10-30
---

# La carga es un Workload del escenario

## Contexto

La spec trataba el Load Generator como un `ComponentType` más: se arrastraba al canvas y se unía a un HTTP Service con una flecha `LOAD_TARGET`. Era la única flecha que no significaba «depende de», el único nodo que nadie podía usar como dependencia y el único que debía arrancar después de que todo lo demás estuviera sano.

La carga no forma parte de la arquitectura que se estudia. Es el estímulo que se le aplica para observarla.

## Decisión

- **Workload.** Cada `Scenario` tiene como máximo un `Workload` en 0.1.0: objetivo, perfil de carga (`CONSTANT_HTTP`, `REPEATED_READ`, `WEIGHTED_CRUD` o `TASK_PRODUCER`), parámetros enteros y semilla.
- **Objetivo por capacidad.** El objetivo es un `Component` que ofrezca la capacidad que pide el perfil de carga, por ejemplo `RESOURCE_READ` para `REPEATED_READ`. Puede ser un HTTP Service o un Load Balancer.
- **Mismo guardado y misma revisión.** El `Workload` se envía y se guarda con el grafo, en el mismo `PUT` y con la misma `Scenario revision`. Entra en el `Execution snapshot`.
- **Tabla propia.** Se guarda en `scenario_workload`, con una fila por escenario y una clave foránea al objetivo. Si se borra el objetivo, la referencia queda vacía y la validación devuelve un error que bloquea `Run`. La configuración de carga no se pierde.
- **Sin Workload se puede ejecutar.** Un escenario sin `Workload` produce una advertencia («no habrá tráfico») y puede ejecutarse.
- **k6 con scripts fijos.** La imagen de carga contiene un script fijo por perfil. El backend solo le pasa números por variables de entorno; no genera código al ejecutar.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Load Generator como nodo (spec original) | Rompe el significado único de las flechas y obliga a casos especiales en validación, orden de arranque y canvas. |
| `Workload` como `jsonb` dentro de `scenarios` | Más simple, pero la base de datos no protegería la referencia al objetivo. |
| Borrar el `Workload` en cascada con su objetivo | El usuario perdería la configuración de carga sin aviso. |
| Generar el script de k6 desde el backend | Más flexible, pero es generar código ejecutable en cada `Run`. Los cuatro perfiles fijos cubren la Fase 3. |

Se acepta un concepto nuevo en el dominio y una tabla más. A cambio, todas las flechas del canvas significan lo mismo y el catálogo de nodos solo contiene piezas de arquitectura. En el canvas, el `Workload` se dibuja como tráfico que entra en su objetivo, no como un nodo.

## Aplicación

Sustituye al Load Generator en ID13, ID17, ID18 e ID20 y en US051, US062, US063 y US071 de la [spec](../PHASE_3_SPEC.md). La ficha `load-generator` de la migración V2 se retirará con una migración nueva. Los perfiles de carga se describen en la [arquitectura de ejecución](../EXECUTION_ARCHITECTURE.md#7-la-carga-workload).
