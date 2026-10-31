---
status: accepted
date: 2026-10-24
---

# Guardado del grafo completo con revisión esperada

## Contexto

El propietario edita el grafo de un `Scenario` en el navegador y lo guarda cuando quiere. El mismo escenario puede estar abierto en dos pestañas, y la ejecución posterior debe usar exactamente la revisión guardada ([ADR 0004](0004-ejecuciones-ligadas-a-una-revision-inmutable.md)). Hace falta decidir cómo se guarda el grafo, cómo se detecta que otra pestaña guardó antes y cómo se identifica cada nodo antes de existir en el servidor.

## Decisión

- **Guardado completo.** `PUT /api/v1/projects/{projectId}/scenarios/{id}/graph` recibe el grafo entero junto con la `revision` de la que partió el cliente. El servidor lo reemplaza en una transacción y suma 1 a la revisión. Si la revisión no coincide, responde `409 Conflict` y no cambia nada. Renombrar el escenario no cambia la revisión.
- **Bloqueo del escenario.** Todo cambio del escenario, incluido el renombrado, lee su fila con `SELECT … FOR UPDATE` (`ScenarioRepository.findForUpdateByIdAndProjectId`). Dos guardados simultáneos se ordenan: el primero confirma y el segundo encuentra la revisión nueva y recibe `409`. #91 usará este mismo punto para coordinar el guardado con la reserva de una ejecución.
- **Reglas en el agregado.** `Scenario.replaceGraph` aplica las reglas estructurales: revisión, límite de nodos, claves válidas y únicas, nombres, y fichas existentes y habilitadas para los nodos nuevos. Fusiona por clave: actualiza los nodos que siguen, crea los nuevos y borra los que faltan. Así cada nodo conserva su `id` entre guardados.
- **El contrato no impide guardar.** Un perfil desconocido o un parámetro que falta o se sale de sus límites se guarda como borrador. La respuesta del guardado, y la de cada lectura del grafo, trae la lista de `ValidationIssue` que calcula `ScenarioValidator` con el `ContractCatalog` (validación en dos niveles, [arquitectura §8](../EXECUTION_ARCHITECTURE.md#8-la-validación)).
- **Claves creadas por el cliente.** Cada nodo tiene un `component_key` UUID, único dentro de su escenario. Lo genera el navegador con `crypto.randomUUID()`, que produce la versión 4. El nodo necesita identidad antes de guardarse: el borrador local y, en #80, las conexiones apuntan a ella. El servidor acepta cualquier UUID válido y solo busca claves dentro del escenario, así que una clave no puede mover ni leer nodos de otro escenario. `crypto.randomUUID()` solo existe en contextos seguros (HTTPS o `localhost`).
- **Tabla propia.** Los nodos se guardan en `components` (migración V8). Las claves foráneas aseguran que pertenecen a un escenario y a una ficha existente, y la unicidad de la clave. Los parámetros son `jsonb` con valores enteros: un decimal se rechaza al leer la petición. El nodo no guarda la versión del contrato, porque solo existe la actual ([ADR 0010](0010-una-sola-version-ejecutable-del-contrato.md)).

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Que gane el último guardado | Una pestaña podría borrar sin aviso el trabajo de otra. |
| `@Version` de JPA | Subiría la revisión también al renombrar, y la revisión identifica el contenido que se ejecuta. Además, la revisión esperada debe venir del cliente, no del estado cargado en la petición. |
| Guardar nodo a nodo | Exigiría coordinar muchas peticiones y permitiría grafos a medio guardar. |
| Todo el grafo en un `jsonb` del escenario | El guardado y la copia serían más simples, pero la base de datos dejaría de garantizar la unicidad de las claves, la referencia a la ficha y, en #80, que las conexiones apunten a nodos existentes. |
| UUID v7 o v1 | La v7 ordena por tiempo, algo útil cuando el UUID es clave primaria de tablas grandes. Aquí la clave primaria es numérica y un escenario tiene pocos nodos. La v1 expone la hora y la máquina. La v4 viene en el navegador. |

Se acepta enviar el grafo entero en cada guardado, con un límite provisional de 50 nodos hasta fijar las cuotas en #107. Un conflicto no se resuelve solo: el usuario decide si recarga la versión guardada o guarda sus cambios encima.

## Aplicación

Implementado en #79 (backend). El contrato de la API está en [`docs/api`](../api) y los términos en el [glosario](../../GLOSSARY.md).
