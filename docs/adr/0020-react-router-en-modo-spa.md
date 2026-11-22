---
status: accepted
date: 2026-09-05
---

# React Router en modo SPA

## Contexto

La propuesta de la Fase 1 ya fijaba una SPA con React, Vite y React Router que consume la API REST de Spring Boot. React Router 8 se puede usar de tres formas: declarativa (`<BrowserRouter>`), con datos (`createBrowserRouter` con loaders) o como framework (plugin de Vite, rutas en `routes.ts`, tipos generados y renderizado en servidor opcional). Al crear el frontend había que elegir una y decidir si las páginas se generan en el servidor.

Infracture es una aplicación privada detrás de un login: un lienzo, formularios y estados de ejecución. No necesita posicionamiento en buscadores. La sesión va en cookies `HttpOnly` que valida Spring ([ADR 0002](0002-proteccion-csrf-con-jwt-en-cookies.md)), y la distribución debe ser una sola imagen sin Node en el ordenador del usuario (ID36 e ID37).

## Decisión

- **React Router en modo framework y sin renderizado en servidor** (`ssr: false` en [`react-router.config.ts`](../../frontend/react-router.config.ts)). El resultado de `npm run build` son ficheros estáticos.
- **Cada ruta carga sus datos en el navegador** con `clientLoader` y guarda con `clientAction`. Todas las llamadas pasan por [`apiFetch`](../../frontend/app/shared/api/api-fetch.ts), que añade CSRF y renueva la sesión. La excepción es el stream de eventos de una ejecución, que usa `EventSource` ([ADR 0017](0017-eventos-de-ejecucion-por-sse-con-aviso-de-postgresql.md)).
- **En desarrollo, Vite reenvía `/api` al backend** ([`vite.config.ts`](../../frontend/vite.config.ts)), así que navegador y API comparten origen y las cookies funcionan igual que en la distribución.
- **En la distribución, Spring servirá los ficheros estáticos** y refrescará las rutas profundas sin devolver React a una ruta `/api` que no existe (ID35 e ID36). Está previsto para P10; hoy la imagen solo lleva el backend.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Modo framework con SSR | Exige un servidor Node en la distribución, junto a Spring, y que ese servidor reenvíe las cookies de sesión en cada carga. Aporta SEO y primera pintura más rápida, que una aplicación privada no necesita. |
| Modo declarativo (`<BrowserRouter>`) | No tiene loaders ni actions, ni tipos generados por ruta. Cada página tendría que gestionar a mano la carga, los errores y la revalidación. |
| Modo datos sin el plugin de Vite | Tiene loaders, pero pierde la definición de rutas en fichero, la división automática del código y `react-router typegen`. |
| Next.js | Está pensado alrededor del renderizado en servidor y de su propio servidor Node. Además, el alumno ya conocía React Router por un proyecto anterior. |

Se acepta que la primera carga muestre un estado de carga mientras llegan los datos, y que ninguna página tenga contenido sin JavaScript. A cambio, el frontend son ficheros estáticos que Spring puede servir. Solo hay una aplicación que valida la sesión, y las pruebas de rutas montan cada página en un router en memoria con Vitest.

## Aplicación

Concreta ID02 de la [spec](../PHASE_3_SPEC.md#arquitectura-y-vocabulario). El alumno creó el proyecto y activó el modo SPA en el commit `16cb6ad` (#39), según la entrada AI-2026-09-05-020 del [registro de uso de IA](../AI_USAGE.md). Las rutas están en [`app/routes.ts`](../../frontend/app/routes.ts) y [`app/routes/`](../../frontend/app/routes), y el flujo está descrito en la [guía de desarrollo](../development-guide.md#arquitectura) y en el [mapa del código](../CODE_MAP.md#cómo-está-organizado). Las pruebas de [`frontend/test/routes`](../../frontend/test/routes) cubren cada ruta con su `clientLoader`.

## Referencias

- [React Router: modos de uso](https://reactrouter.com/start/modes).
- [React Router: SPA](https://reactrouter.com/how-to/spa).
- [React Router: carga de datos en el cliente](https://reactrouter.com/how-to/client-data).
- [Vite: proxy del servidor de desarrollo](https://vite.dev/config/server-options#server-proxy).
