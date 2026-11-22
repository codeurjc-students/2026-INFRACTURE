---
status: accepted
date: 2026-09-03
---

# Contrato OpenAPI generado desde el código

## Contexto

La API REST bajo `/api/v1` es el contrato entre el frontend, Postman y quien evalúe el TFG. Hace falta una descripción que se pueda leer y versionar junto al código. También tiene que coincidir con los controladores reales, para no tener que mantener a mano un documento que se quede viejo. La issue #15 (P2-09) pedía exportar ese contrato de forma reproducible.

## Decisión

- **El código es la fuente.** springdoc-openapi lee los controladores, los DTO y los `@ExceptionHandler`, y publica la especificación en `/v3/api-docs`, con Swagger UI en `/swagger-ui.html`.
- **Exportación con un perfil Maven.** `./mvnw verify -Popenapi` arranca la aplicación, descarga `/v3/api-docs.yaml` con `springdoc-openapi-maven-plugin`, genera el HTML con OpenAPI Generator y lo deja todo en [`docs/api/`](../api).
- **El contrato se guarda en el repositorio.** [`api-docs.yaml`](../api/api-docs.yaml) y [`api-docs.html`](../api/api-docs.html) se regeneran en cada PR que cambia la API, y el diff se revisa con el resto del cambio.
- **Pocas anotaciones.** El alumno pidió retirar las anotaciones descriptivas y la prueba contractual del primer intento. La especificación sale de los tipos y de los manejadores de error, por eso cada operación muestra sus respuestas `ProblemDetail` ([ADR 0024](0024-errores-de-la-api-como-problemdetail.md)).
- **Tipos del frontend escritos a mano.** Cada DTO tiene su fichero `*DTO.ts` en `features/<área>/model/`. No se genera un cliente.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Escribir primero el YAML y generar el servidor (API-first) | Habría dos fuentes que mantener: el YAML y el código generado con sus adaptaciones. Para una API con un único cliente, que cambia con cada issue, es más trabajo que beneficio. |
| Solo Swagger UI en vivo, sin exportar | No queda una versión fija en el repositorio, ni un diff que revisar, ni una documentación que leer sin arrancar el backend. |
| Spring REST Docs | Genera la documentación a partir de las pruebas y garantiza que los ejemplos funcionan, pero hay que escribir cada fragmento en ellas. Las pruebas de sistema con REST Assured ya comprueban el comportamiento ([ADR 0022](0022-capas-de-prueba.md)). |
| Generar el cliente TypeScript desde el YAML | Evitaría que los tipos del frontend se desincronicen, pero añade un paso de build. Los DTO son pocos y los cubren las pruebas de integración cliente-servidor. Se puede añadir si la API crece. |

Se acepta que exportar el contrato necesita PostgreSQL en marcha, y que nada falla si alguien olvida regenerarlo: lo detecta la revisión de la PR. También que, sin anotaciones, las descripciones son mínimas. A cambio, el contrato no puede contradecir al código. La exportación es reproducible: el alumno la ejecutó dos veces y obtuvo los mismos hashes.

## Aplicación

Se implementó en el commit `a66ba06` (#15), según la entrada AI-2026-09-03-015 del [registro de uso de IA](../AI_USAGE.md). La configuración es el perfil `openapi` de [`backend/pom.xml`](../../backend/pom.xml), con las rutas de `springdoc` en [`application.properties`](../../backend/src/main/resources/application.properties) y su acceso público en [`SecurityConfig`](../../backend/src/main/java/es/codeurjc/infracture/security/SecurityConfig.java). El comando está en la [guía de desarrollo](../development-guide.md#openapi). Concreta ID32 de la [spec](../PHASE_3_SPEC.md#api-y-experiencia-de-uso).

## Referencias

- [springdoc-openapi](https://springdoc.org/).
- [springdoc-openapi-maven-plugin](https://github.com/springdoc/springdoc-openapi-maven-plugin).
- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html).
- [OpenAPI Generator: generador HTML](https://openapi-generator.tech/docs/generators/html/).
- [Spring REST Docs](https://docs.spring.io/spring-restdocs/docs/current/reference/htmlsingle/).
