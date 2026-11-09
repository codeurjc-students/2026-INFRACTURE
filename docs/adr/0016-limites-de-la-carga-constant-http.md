---
status: accepted
date: 2026-11-09
---

# Límites de la carga CONSTANT_HTTP

## Contexto

El [ADR 0012](0012-la-carga-es-un-workload-del-escenario.md) convierte la carga en un `Workload` del escenario y la genera con Grafana k6, con un script fijo por perfil que solo recibe números. Falta decidir qué números elige el usuario, con qué límites y qué fija Infracture.

k6 admite miles de usuarios virtuales y frecuencias muy altas. Esos límites no sirven aquí: Infracture Local corre en un portátil y cada contenedor del experimento tiene un tope de 0,5 CPU y 384 MB. Con cifras grandes, el cuello de botella sería k6 o Docker y no la arquitectura que se estudia.

## Decisión

`CONSTANT_HTTP` envía `GET /` al puerto 8080 del objetivo a una frecuencia fija. Usa el executor `constant-arrival-rate` de k6, que empieza las peticiones a su ritmo aunque las anteriores no hayan respondido.

| Parámetro | Límites | Opción de k6 |
| --- | --- | --- |
| `requestsPerSecond` | 1 a 50 | `rate`, con `timeUnit` fijo en `1s`. |
| `virtualUsers` | 1 a 20 | `preAllocatedVUs` y `maxVUs`, con el mismo valor: peticiones en vuelo como máximo. |
| `durationSeconds` | 10 a 600 | `duration`. |
| `seed` | 0 a 2147483647 | Ninguna. k6 no tiene semilla; los scripts usarán una propia. |

- **Los límites viven en el contrato del backend** (`WorkloadContract`). La API y el inspector muestran el mismo error, y un valor fuera de rango se guarda como borrador con su `ValidationIssue`, igual que los parámetros de un nodo.
- **La semilla no cambia `CONSTANT_HTTP`**, porque la petición es siempre la misma. Se pide y se guarda igualmente, para que los cuatro perfiles tengan la misma forma. `REPEATED_READ` y `WEIGHTED_CRUD` la usarán para elegir recursos y operaciones.
- **Infracture fija el resto:** el periodo de un segundo, una parada ordenada corta para que `Stop` no espere los 30 s de k6, descartar el cuerpo de las respuestas, reutilizar conexiones, el `timeout` de cada petición, el `User-Agent` y las estadísticas del resumen.
- **No se exponen** cabeceras, autenticación, cookies, TLS, `hosts`, `startTime`, `exec`, `env` ni `tags` libres. Serían configuración arbitraria, y el ADR 0012 solo permite números.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Los límites de k6 | Medirían la máquina, no la arquitectura, y podrían dejar el portátil sin recursos. |
| Exponer también `timeout`, `gracefulStop` o la reutilización de conexiones | Son útiles para estudios concretos, pero añaden opciones que la Fase 3 no necesita. Se pueden añadir como parámetros nuevos sin cambiar el modelo. |
| Concurrencia igual a la frecuencia | Oculta la saturación: con más latencia, k6 tendría que crear más usuarios. Con un tope, k6 cuenta en `dropped_iterations` las peticiones que no pudo empezar, y ese dato es evidencia del experimento. |

Se acepta que un experimento con `responseDelayMs` alto y mucha frecuencia sature el generador. Por ejemplo, 50 peticiones/s con 5 s de retraso necesitarían unos 250 usuarios virtuales. El resumen lo mostrará como iteraciones descartadas, no como un error.

La carga más larga, 10 minutos, cabe en el tiempo máximo de una ejecución (30 minutos por defecto) y deja margen para observar el sistema después.

## Aplicación

Concreta el perfil `CONSTANT_HTTP` de la [arquitectura de ejecución](../EXECUTION_ARCHITECTURE.md#7-la-carga-workload). Se implementa en #87.
