---
status: accepted
date: 2026-10-30
---

# Puertos con nombre que piden capacidades

## Contexto

La spec describía las conexiones como una lista de pares permitidos: origen, destino, tipo de conexión (`DATABASE`, `CACHE`, `HTTP_CALL`…) y cardinalidades. Cada regla nombraba tipos concretos: `CACHE` debía apuntar a Redis y `HTTP_CALL` a un HTTP Service.

Ese modelo tiene tres problemas:

- El Worker necesitaba reglas de grupo, como «un PostgreSQL o un Redis, nunca los dos».
- Añadir un tipo nuevo, como el Load Balancer, obligaba a reescribir las reglas de los tipos existentes.
- Los ciclos solo se comprobaban en las llamadas HTTP, aunque ningún ciclo de dependencias puede arrancar.

## Decisión

- **Puertos por perfil.** Cada perfil declara sus puertos de salida con nombre, cardinalidad (mínimo y máximo) y etiqueta visible. Por ejemplo, `CACHE_ASIDE` tiene `cache` y `database`, y el Worker `CONSUMER` tiene `queue` y `result`.
- **Una Connection sale de un puerto.** Una `Connection` une un puerto de un `Component` con otro `Component` del mismo escenario. Sigue significando «el origen depende del destino».
- **Los puertos piden capacidades, no tipos.** Cada puerto pide una capacidad (`SQL_DATABASE`, `KEY_VALUE`, `MESSAGE_BROKER`, `HTTP`…). Cada perfil declara las capacidades que ofrece. Una conexión es válida si el destino ofrece lo que pide el puerto.
- **Grafo acíclico.** El grafo de dependencias completo debe ser acíclico. Su orden topológico es también el orden de arranque: si A depende de B, B arranca antes.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Lista de pares permitidos (spec original) | Crece con cada tipo nuevo y necesita casos especiales para grupos y ciclos. |
| Puertos que piden un tipo concreto | Resuelven los grupos, pero el Load Balancer seguiría necesitando reglas propias en cada puerto HTTP. |
| Detectar ciclos solo en HTTP | Con el Load Balancer aparecen ciclos indirectos (HTTP → LB → HTTP). Ninguno tiene sentido al arrancar. |

Es el modelo de *requirements* y *capabilities* de TOSCA, el estándar de OASIS para describir topologías: un nodo declara lo que necesita y lo que ofrece, y el orquestador comprueba que encajan.

Se acepta que el contrato de cada perfil sea algo más largo. A cambio, el validador aplica una única regla de compatibilidad y añadir un tipo solo exige declarar sus capacidades. El canvas puede usar los mismos datos para dejar soltar una flecha solo sobre destinos compatibles.

## Aplicación

Sustituye las reglas de pares y cardinalidades de ID18 e ID19 de la [spec](../PHASE_3_SPEC.md#grafo-catálogo-y-contratos). Las conexiones se implementan en #80. La tabla de puertos y capacidades por perfil está en la [arquitectura de ejecución](../EXECUTION_ARCHITECTURE.md#5-perfiles-puertos-y-capacidades).
