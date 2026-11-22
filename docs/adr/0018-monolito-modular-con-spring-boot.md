---
status: accepted
date: 2026-09-01
---

# Monolito modular con Spring Boot

## Contexto

Infracture tiene varias áreas con reglas propias: cuentas y sesión, catálogo, proyectos y escenarios, y ejecución de experimentos. Las desarrolla una sola persona, se instala en un solo ordenador ([ADR 0015](0015-una-ejecucion-por-instancia-en-local.md)) y casi todas las operaciones cruzan varias áreas. Por ejemplo, `Run` bloquea el escenario, lo valida contra el catálogo y reserva la ejecución en la misma transacción.

Hacía falta decidir cómo se reparte el backend: en una sola aplicación o en varios servicios, cómo se separan las áreas por dentro y con qué versión de Java se construye. La propuesta de la Fase 1 ya fijaba un monolito modular con Spring Boot. Este ADR recoge el porqué y cómo se aplica.

## Decisión

- **Una sola aplicación Spring Boot.** El backend es un único proceso, con una base de datos y un despliegue.
- **Un paquete por área.** Hay un módulo por cada área del dominio: `identity`, `catalog`, `project` y `execution`. Aparte están `security`, para la sesión, y `shared`, para las excepciones comunes.
- **Las mismas cuatro capas en cada módulo.** `api` tiene el controlador, los DTO y el mapper; `application`, el servicio; `domain`, las entidades y sus reglas; `persistence`, los repositorios. Una petición siempre sigue el orden controlador → servicio → repositorio.
- **Mappers con MapStruct.** El paso entre entidad y DTO se declara en una interfaz `@Mapper` y MapStruct genera el código al compilar.
- **Java 25 LTS.** El proyecto pasó de Java 21 a Java 25 el 16 de septiembre de 2026. Solo se fija la versión mayor.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Microservicios (uno por área) | Las operaciones centrales, como `Run`, necesitan una transacción que abarca varias áreas. Con servicios separados harían falta sagas, mensajería y varias bases de datos, para una aplicación que instala y usa una sola persona en su ordenador. |
| Monolito por capas técnicas (`controllers/`, `services/`…) | Mezcla todas las áreas en cada carpeta. Para cambiar la ejecución habría que tocar cuatro carpetas compartidas con el resto. |
| Spring Modulith o ArchUnit para imponer los límites | Comprobarían en las pruebas que un módulo no entra en otro. Hoy los módulos son pocos y los revisa una persona, así que se aplaza hasta que haga falta. |
| Mappers escritos a mano | Son código repetitivo y se pueden desincronizar del DTO sin que nada avise. MapStruct genera el mapeo al compilar y avisa si falta algún campo. |
| Mantener Java 21 | Java 25 es la LTS siguiente y Spring Boot 4 la admite. Cambiar al empezar evita una migración a mitad del TFG. |

Se acepta que los límites entre módulos son una convención y no una regla que se compruebe sola. Por ejemplo, `execution` usa directamente `Scenario` y `ScenarioValidator` del módulo `project`. A cambio, una operación que cruza áreas es una llamada en el mismo proceso, dentro de una transacción, y el despliegue es una sola imagen.

Si Infracture Cloud necesitara escalar la ejecución por separado, el módulo `execution` es el primer candidato a salir como servicio propio. Su interfaz con el resto ya pasa por `ScenarioService` y el `ExecutionReconciler`.

## Aplicación

Concreta ID01 de la [spec](../PHASE_3_SPEC.md#arquitectura-y-vocabulario). Se creó con el backend en el commit `d827b93` (#35). La organización está en el [mapa del código](../CODE_MAP.md#cómo-está-organizado) y en la [sección 16 de la arquitectura de ejecución](../EXECUTION_ARCHITECTURE.md#16-mapa-al-código). El código está en [`backend/src/main/java/es/codeurjc/infracture`](../../backend/src/main/java/es/codeurjc/infracture), y los mappers son, por ejemplo, [`ProjectMapper`](../../backend/src/main/java/es/codeurjc/infracture/project/api/ProjectMapper.java) y [`ExecutionMapper`](../../backend/src/main/java/es/codeurjc/infracture/execution/api/ExecutionMapper.java). La versión de Java está en [`.java-version`](../../.java-version) y en `java.version` de [`backend/pom.xml`](../../backend/pom.xml). Las pruebas siguen el mismo árbol de paquetes en [`backend/src/test/java`](../../backend/src/test/java/es/codeurjc/infracture).

## Referencias

- [Spring Boot: estructurar el código](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html).
- [Martin Fowler: MonolithFirst](https://martinfowler.com/bliki/MonolithFirst.html).
- [Spring Modulith: referencia](https://docs.spring.io/spring-modulith/reference/).
- [MapStruct: guía de referencia](https://mapstruct.org/documentation/stable/reference/html/).
- [OpenJDK: JDK 25](https://openjdk.org/projects/jdk/25/).
