---
status: accepted
date: 2026-10-31
---

# Borrador local del lienzo en el navegador

## Contexto

El grafo de un escenario se guarda entero y con su revisión, solo cuando el usuario pulsa Guardar ([ADR 0009](0009-guardado-del-grafo-completo-con-revision-esperada.md)). Entre dos guardados, todo el trabajo vive solo en la memoria de la pestaña. Hay varias formas de perderlo sin querer:

- recargar o cerrar la pestaña;
- un `409` porque otra pestaña guardó antes, cuando el usuario elige quedarse con la versión del servidor;
- que caduque la sesión justo al guardar, de modo que el login lleva a otra página;
- desde #91, que otra pestaña reserve el escenario para un Run y el lienzo pase a solo lectura.

Hacía falta decidir dónde se guarda ese trabajo pendiente sin convertirlo en un segundo guardado que compita con la revisión del servidor.

## Decisión

- **Una copia en `localStorage` mientras haya cambios sin guardar.** Contiene lo que el usuario construyó en el lienzo (nodos, posiciones, parámetros, conexiones y la carga) y la revisión de la que partió (`baseRevision`). No contiene nada secreto.
- **Una clave por cuenta y escenario:** `infracture:scenario-draft:<userId>:<scenarioId>`. Otra cuenta en el mismo navegador nunca ve el borrador.
- **Se ofrece, no se aplica solo.** Al abrir el escenario, si hay borrador, el lienzo propone restaurarlo o descartarlo, y nada lo sobrescribe hasta que el usuario elige. Al restaurarlo se conserva `baseRevision`, así que el guardado sigue comprobando la revisión y un conflicto se detecta igual.
- **Se borra solo cuando el servidor confirma el guardado.** Mientras un guardado está en curso, la copia se mantiene, porque todavía puede fallar.
- **Un fallo del almacenamiento no rompe el editor.** Si `localStorage` está lleno, bloqueado o editado a mano, simplemente no hay borrador.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Guardado automático en el servidor | Cada cambio crearía una revisión nueva y competiría con el guardado de otras pestañas. Además, Run ejecutaría una revisión que el usuario no eligió guardar ([ADR 0004](0004-ejecuciones-ligadas-a-una-revision-inmutable.md)). |
| Una tabla de borradores en el servidor | Añade una entidad, una API y sus permisos para algo que solo sirve en el navegador donde se hizo el trabajo. |
| `sessionStorage` | Se pierde al cerrar la pestaña, que es uno de los casos que hay que cubrir, y no se comparte con la pestaña que abre el login tras caducar la sesión. |
| IndexedDB | Es asíncrono y más complejo. Un grafo de 50 nodos como máximo cabe de sobra en `localStorage`. |
| Avisar con `beforeunload` y no guardar nada | No cubre el conflicto, la sesión caducada ni el bloqueo desde otra pestaña, y el navegador decide si muestra el aviso. |

Se acepta que el borrador solo existe en ese navegador, que un usuario puede ver su propio borrador antiguo si vuelve semanas después, y que los borradores guardados antes de existir las conexiones o la carga se leen con esos campos vacíos. A cambio, ningún cambio sin guardar se pierde por recargar, por un conflicto o por la sesión. El servidor sigue siendo la única fuente de las revisiones, y la regla de la revisión esperada no cambia.

## Aplicación

Se implementó en el commit `95655d8` (#79). Desde `367c590` (#91), el lienzo guarda también en el borrador el trabajo pendiente cuando otra pestaña reserva el escenario. El código está en [`draft-storage.ts`](../../frontend/app/features/scenario-canvas/draft-storage.ts) y lo usa [`scenario-canvas.tsx`](../../frontend/app/features/scenario-canvas/components/scenario-canvas.tsx). Lo comprueban [`draft-storage.test.ts`](../../frontend/test/features/scenario-canvas/draft-storage.test.ts) y el recorrido de [`scenario-canvas.spec.ts`](../../frontend/test/system/scenario-canvas.spec.ts), que recupera el borrador tras volver a iniciar sesión y comprueba que otra cuenta no lo ve. El papel del borrador en el guardado está en el [mapa del código](../CODE_MAP.md#diseñar-y-guardar-un-escenario).

## Referencias

- [MDN: `Window.localStorage`](https://developer.mozilla.org/en-US/docs/Web/API/Window/localStorage).
- [HTML Living Standard: Web Storage](https://html.spec.whatwg.org/multipage/webstorage.html).
- [MDN: evento `beforeunload`](https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeunload_event).
