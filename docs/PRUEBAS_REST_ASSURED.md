# Pruebas de API con REST Assured

## 1. Objetivo de esta guía

Este documento explica la prueba HTTP implementada para el catálogo de componentes de Infracture, por qué se considera una prueba de sistema de la API, qué responsabilidad tiene cada herramienta y cómo se prepara, ejecuta y elimina su entorno.

La implementación descrita corresponde a:

- el issue P2-15;
- la clase `ComponentTemplateApiSystemTests`;
- el endpoint `GET /api/v1/component-templates`;
- REST Assured 6.0.1;
- Spring Boot Test;
- Testcontainers 2.0.5;
- PostgreSQL 18.6 Alpine;
- Flyway;
- JUnit 5 y Hamcrest.

## 2. Resumen rápido

La prueba hace lo siguiente:

1. Testcontainers crea un PostgreSQL real, vacío y desechable.
2. Spring Boot recibe automáticamente la conexión a ese PostgreSQL.
3. Flyway aplica las migraciones V1 y V2.
4. La migración V2 crea la tabla `component_template` e inserta las seis plantillas iniciales.
5. Spring Boot arranca la aplicación y un servidor HTTP real en un puerto libre.
6. REST Assured envía una petición HTTP `GET` al endpoint público.
7. La prueba valida el estado HTTP, el tipo de contenido y el JSON devuelto.
8. Al terminar la clase, Testcontainers detiene y elimina el PostgreSQL temporal.

Por tanto, la prueba no llama directamente al controlador, al servicio ni al repositorio. Entra por la misma frontera pública que usaría un cliente HTTP:

```text
REST Assured
    |
    | GET /api/v1/component-templates
    v
Servidor HTTP de Spring Boot
    |
    v
ComponentTemplateController
    |
    v
ComponentTemplateService
    |
    v
ComponentTemplateRepository / JPA
    |
    v
PostgreSQL temporal creado por Testcontainers
```

## 3. ¿Qué tipo de prueba es?

En este proyecto se denomina **prueba de sistema de la API**.

Es una prueba de sistema porque arranca el backend completo y comprueba su comportamiento desde su interfaz pública HTTP. Atraviesa el servidor web, Spring MVC, el controlador, el mapeo a DTO, el servicio, el repositorio, Hibernate/JPA, el driver JDBC y una base de datos PostgreSQL real.

También podría aparecer clasificada como prueba de integración de API en otras taxonomías. Los nombres varían entre equipos. Lo importante no es únicamente la etiqueta, sino la frontera que se prueba: aquí la frontera es el backend desplegado como servidor HTTP.

No es una prueba end-to-end completa de la aplicación porque no ejecuta el frontend ni un navegador. Tampoco comprueba la interacción de una persona con la interfaz gráfica.

### Comparación con las otras pruebas del catálogo

| Nivel | Clase o herramienta | Frontera ejercitada | Dobles de prueba | Infraestructura real |
| --- | --- | --- | --- | --- |
| Unitaria | `ComponentTemplateServiceTests` | Un objeto `ComponentTemplateService` | Repositorio simulado con Mockito | Ninguna |
| Integración backend | `ComponentTemplateRepositoryTests` | Servicio, repositorio, JPA y base de datos | Ninguno para la persistencia | PostgreSQL temporal |
| Sistema de API | `ComponentTemplateApiSystemTests` | Backend completo desde HTTP | Ninguno | Servidor HTTP y PostgreSQL temporal |
| E2E de interfaz | Playwright, cuando corresponda | Navegador, frontend, backend y base de datos | Idealmente ninguno en el recorrido principal | Sistema completo |

La prueba de REST Assured no sustituye a las pruebas unitarias ni a las de persistencia. Cada nivel localiza tipos de error diferentes:

- la unitaria detecta rápidamente errores en la regla de negocio;
- la de integración detecta errores de JPA, consultas, migraciones o conexión;
- la de sistema de API detecta además errores de rutas, serialización, servidor HTTP y contrato de respuesta;
- la E2E detecta problemas que solo aparecen al recorrer la aplicación desde la interfaz.

## 4. Código bajo prueba

La clase se encuentra en:

```text
backend/src/test/java/es/codeurjc/infracture/catalog/api/ComponentTemplateApiSystemTests.java
```

Su estructura esencial es:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class ComponentTemplateApiSystemTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.6-alpine");

    @LocalServerPort
    private int port;

    @Test
    void returnsSeededCatalogueThroughHttp() {
        given()
                .port(port)
        .when()
                .get("/api/v1/component-templates")
        .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", equalTo(6));
    }
}
```

La prueba real contiene más comprobaciones sobre las claves, el orden y campos representativos del primer y último elemento.

### Contrato JSON esperado

El endpoint devuelve conceptualmente este array:

```json
[
  {
    "key": "http-service",
    "name": "HTTP Service",
    "type": "HTTP_SERVICE"
  },
  {
    "key": "worker",
    "name": "Worker",
    "type": "WORKER"
  },
  {
    "key": "load-generator",
    "name": "Load Generator",
    "type": "LOAD_GENERATOR"
  },
  {
    "key": "postgresql",
    "name": "PostgreSQL",
    "type": "POSTGRESQL"
  },
  {
    "key": "redis",
    "name": "Redis",
    "type": "REDIS"
  },
  {
    "key": "rabbitmq",
    "name": "RabbitMQ",
    "type": "RABBITMQ"
  }
]
```

`ComponentTemplateDTO` solo contiene `key`, `name` y `type`. El identificador interno de base de datos y el campo `enabled` pertenecen al modelo persistente y no forman parte del contrato público. La prueba actual comprueba los valores relevantes, pero no contiene una aserción explícita que prohíba añadir otros campos al JSON; esa es una posible ampliación si el contrato necesita ser cerrado estrictamente.

## 5. Responsabilidad de cada anotación

### `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)`

Carga el contexto completo de Spring Boot y arranca el servidor web embebido en un puerto disponible elegido por el sistema operativo.

Sin `RANDOM_PORT`, el modo predeterminado de `@SpringBootTest` no iniciaría un servidor HTTP real. La prueba podría cargar el contexto, pero REST Assured no tendría un servidor al que enviar la petición.

### `@Testcontainers`

Activa la extensión de Testcontainers para JUnit 5. Esa extensión busca los campos anotados con `@Container` y controla su ciclo de vida.

### `@Container`

Indica que el campo `postgres` representa un contenedor administrado por Testcontainers.

El campo es `static`, por lo que se crea una vez para todos los métodos de esa clase y se elimina al terminar la clase. Si fuese un campo de instancia, Testcontainers lo reiniciaría para cada método de prueba.

Actualmente la clase contiene un único método, pero declarar el contenedor como estático evita reinicios innecesarios si se añaden más casos en la misma clase.

### `@ServiceConnection`

Permite que Spring Boot obtenga del contenedor los datos de conexión reales y cree la configuración JDBC correspondiente. Entre esos datos están el host, el puerto publicado, el nombre de la base de datos, el usuario y la contraseña.

Esta conexión generada para la prueba tiene prioridad sobre los valores de desarrollo de `application.properties`. Por eso la prueba no se conecta al PostgreSQL persistente de Docker Compose ni necesita escribir manualmente una URL JDBC temporal.

### `@LocalServerPort`

Inyecta en `port` el puerto concreto en el que ha arrancado el servidor HTTP de Spring Boot. REST Assured usa ese número mediante `.port(port)`.

### `@Test`

Marca `returnsSeededCatalogueThroughHttp()` como caso ejecutable por JUnit 5.

## 6. ¿Por qué hay puertos aleatorios?

Intervienen dos puertos distintos y ambos pueden ser dinámicos.

### Puerto HTTP de Spring Boot

Dentro de una ejecución normal, la aplicación suele escuchar en el puerto 8080. En la prueba se utiliza `RANDOM_PORT` para que el sistema operativo elija un puerto libre.

Esto evita:

- conflictos si el backend de desarrollo ya usa el 8080;
- conflictos entre varias ejecuciones de pruebas;
- depender de una configuración fija de la máquina;
- fallos en CI cuando un puerto concreto está ocupado.

REST Assured no adivina ese puerto: `@LocalServerPort` lo obtiene y `.port(port)` lo configura para la petición.

### Puerto de PostgreSQL

PostgreSQL escucha en el puerto 5432 dentro del contenedor. Testcontainers publica ese puerto en otro puerto libre del equipo anfitrión. `@ServiceConnection` descubre el mapeo y configura el `DataSource` de Spring con la dirección correcta.

Que el puerto exterior sea aleatorio no cambia PostgreSQL ni aporta seguridad por sí mismo. Su finalidad en estas pruebas es el aislamiento y evitar colisiones.

## 7. ¿Quién crea y quién llena la base de datos?

Son responsabilidades separadas:

- **Testcontainers crea el servidor PostgreSQL vacío.**
- **`@ServiceConnection` conecta Spring Boot a ese servidor.**
- **Flyway crea y rellena el esquema ejecutando las migraciones del proyecto.**

La prueba no inserta manualmente las seis plantillas. Durante el arranque de Spring Boot, Flyway encuentra las migraciones en:

```text
backend/src/main/resources/db/migration/
```

En la base vacía:

1. Flyway crea su tabla de historial `flyway_schema_history`.
2. Aplica `V1__initialize_platform_database.sql`.
3. Aplica `V2__create_component_template_catalogue.sql`.
4. V2 crea `component_template`.
5. V2 ejecuta el `INSERT` de las seis filas del catálogo.
6. Hibernate valida que las entidades sean compatibles con el esquema porque `spring.jpa.hibernate.ddl-auto=validate`.

Flyway registra cada migración aplicada en su tabla de historial. Si Spring consultara otra vez la misma base durante esa ejecución, no volvería a aplicar una migración versionada que ya figure como completada.

Sin embargo, en una ejecución posterior se crea otro contenedor vacío. Esa nueva base no tiene historial, así que Flyway vuelve a aplicar V1 y V2. Esto es intencionado: comprueba que el proyecto puede reconstruir el esquema desde cero de forma reproducible.

## 8. Ciclo de vida completo de la prueba

El orden conceptual es el siguiente:

1. Maven Surefire descubre la clase de JUnit.
2. La extensión de Testcontainers solicita a Docker la imagen `postgres:18.6-alpine`.
3. Docker arranca un contenedor PostgreSQL y publica su puerto en un puerto libre.
4. Testcontainers espera a que PostgreSQL esté preparado para aceptar conexiones.
5. `@ServiceConnection` facilita a Spring Boot los datos de ese contenedor.
6. Spring Boot crea el `DataSource` conectado a la base temporal.
7. Flyway aplica las migraciones pendientes y siembra el catálogo.
8. JPA valida el esquema y Spring termina de crear el contexto.
9. El servidor web embebido arranca en otro puerto libre.
10. `@LocalServerPort` entrega a la prueba el puerto HTTP real.
11. REST Assured envía la petición `GET`.
12. El controlador consulta el servicio.
13. El servicio obtiene las plantillas habilitadas del repositorio y las ordena por `ComponentType`.
14. El mapper convierte las entidades en DTO.
15. Spring serializa la lista a JSON y devuelve la respuesta HTTP.
16. REST Assured y Hamcrest validan la respuesta.
17. JUnit informa del resultado.
18. Al terminar la clase, Testcontainers detiene y elimina su PostgreSQL.

Docker Desktop debe estar disponible durante los pasos 2 a 4. No hace falta arrancar manualmente el servicio PostgreSQL de `compose.yaml`.

## 9. La sintaxis de REST Assured

REST Assured ofrece una DSL basada en las palabras `given`, `when` y `then`.

### `given()`

Define la preparación de la petición. En esta prueba únicamente se configura el puerto:

```java
given()
        .port(port)
```

No se envía cuerpo, autenticación ni parámetros porque el endpoint es un `GET` público sin parámetros.

### `when()`

Ejecuta la acción:

```java
when()
        .get("/api/v1/component-templates")
```

REST Assured construye una petición HTTP real contra `localhost`, usando el puerto configurado.

### `then()`

Inicia las validaciones de la respuesta:

```java
then()
        .statusCode(200)
        .contentType(ContentType.JSON)
```

Las aserciones del cuerpo combinan las expresiones de consulta JSON de REST Assured con matchers de Hamcrest.

## 10. ¿Qué hace `.log().ifValidationFails()`?

Esta llamada está después de `then()`, por lo que configura el registro de la **respuesta**:

```java
.then()
        .log().ifValidationFails()
```

Si todas las validaciones pasan, no imprime la respuesta y mantiene limpia la salida de la suite. Si falla cualquiera de las validaciones posteriores, muestra los detalles de la respuesta para facilitar el diagnóstico.

Es más útil aquí que `.log().ifError()`. `ifError()` se centra en estados HTTP de error, mientras que `ifValidationFails()` también registra una respuesta `200 OK` cuyo JSON, orden o tipo de contenido sea incorrecto.

La posición importa:

- `given().log().ifValidationFails()` configura el registro de la petición;
- `then().log().ifValidationFails()` configura el registro de la respuesta.

La prueba actual registra la respuesta, que es la información más útil para entender por qué ha fallado una aserción del contrato.

## 11. ¿Qué valida exactamente?

### Estado HTTP

```java
.statusCode(200)
```

Comprueba que la petición termina correctamente con `200 OK`. Detectaría, por ejemplo, una ruta inexistente, un error de servidor o un fallo de conexión con la base de datos que terminase en otro estado.

### Tipo de contenido

```java
.contentType(ContentType.JSON)
```

Comprueba que el servidor declara la respuesta como JSON.

### Número de elementos

```java
.body("size()", equalTo(6))
```

El cuerpo esperado es un array JSON. `size()` consulta su longitud y Hamcrest exige que sea exactamente 6.

### Claves y orden contractual

```java
.body("key", contains(
        "http-service",
        "worker",
        "load-generator",
        "postgresql",
        "redis",
        "rabbitmq"))
```

La expresión `key` extrae el campo `key` de todos los objetos del array. `contains` exige los seis valores en ese orden exacto, no solo que estén presentes.

Esta comprobación atraviesa varias responsabilidades a la vez:

- Flyway debe haber insertado las filas;
- JPA debe poder leerlas;
- el servicio debe devolver únicamente las habilitadas;
- el servicio debe conservar el orden definido por `ComponentType`;
- el mapper y Jackson deben exponer `key` correctamente.

### Campos representativos

```java
.body("[0].name", equalTo("HTTP Service"))
.body("[0].type", equalTo("HTTP_SERVICE"))
.body("[5].name", equalTo("RabbitMQ"))
.body("[5].type", equalTo("RABBITMQ"));
```

Comprueba la serialización de `name` y `type` en los extremos de la lista. Las claves de los seis elementos ya garantizan su identidad y orden; estos campos representativos evitan repetir todas las propiedades de los seis DTO sin aportar una señal proporcionalmente mayor.

## 12. ¿Por qué se usa Testcontainers si REST Assured solo prueba HTTP?

REST Assured es el cliente de prueba. No sustituye las dependencias que necesita el backend.

Cuando llega la petición, el endpoint consulta el repositorio. Sin una base de datos, el backend no podría arrancar correctamente ni producir el catálogo real. Testcontainers proporciona esa dependencia con el mismo motor PostgreSQL utilizado por la aplicación.

La combinación cubre responsabilidades distintas:

| Herramienta | Responsabilidad |
| --- | --- |
| JUnit 5 | Descubrir, ejecutar y reportar el caso |
| Spring Boot Test | Arrancar el backend completo para la prueba |
| REST Assured | Construir la petición HTTP y validar la respuesta |
| Hamcrest | Expresar comparaciones sobre estado y JSON |
| Testcontainers | Crear y eliminar PostgreSQL de forma automatizada |
| Docker | Ejecutar el proceso PostgreSQL aislado |
| `@ServiceConnection` | Conectar automáticamente Spring con el contenedor |
| Flyway | Crear y versionar el esquema e insertar los datos iniciales |
| JPA/Hibernate | Mapear entidades, ejecutar consultas y validar el esquema |

## 13. ¿Es PostgreSQL “real”?

Sí. `postgres:18.6-alpine` ejecuta un servidor PostgreSQL real dentro de un contenedor. No se utiliza H2 ni una simulación en memoria.

La diferencia respecto al PostgreSQL de desarrollo no es el motor, sino el ciclo de vida:

| PostgreSQL de Testcontainers | PostgreSQL de Docker Compose |
| --- | --- |
| Se crea automáticamente para la prueba | Se arranca como infraestructura de desarrollo |
| Comienza vacío | Conserva datos en un volumen persistente |
| Se elimina al terminar | Permanece hasta que se detiene o elimina manualmente |
| Usa conexión y puerto dinámicos | Usa la configuración estable de desarrollo |
| Puede modificarse sin afectar datos personales | Puede contener datos que el desarrollador quiere conservar |

## 14. ¿Se podría probar contra el PostgreSQL de desarrollo?

Técnicamente sí, pero implicaría asumir varias responsabilidades manuales:

- garantizar que el servicio está arrancado antes de la suite;
- conocer y distribuir credenciales y dirección;
- llevar la base al estado esperado antes de cada ejecución;
- impedir que datos anteriores cambien el resultado;
- limpiar las modificaciones después de la prueba;
- evitar que dos ejecuciones se interfieran;
- reproducir el mismo servicio en CI;
- proteger los datos de desarrollo frente a borrados accidentales.

También se podría mantener una base dedicada exclusivamente a pruebas, pero seguiría necesitando aprovisionamiento, migración, limpieza y coordinación externa.

Testcontainers automatiza esas tareas y hace que el estado inicial forme parte de la propia prueba. Por eso se eligió para esta suite.

## 15. ¿Puede entrar en conflicto con `ComponentTemplateRepositoryTests`?

No en la implementación actual.

Cada clase declara su propio campo estático `PostgreSQLContainer`. Por ello, cada clase recibe una instancia independiente de PostgreSQL, con su propio almacenamiento y su propio puerto exterior. La modificación de una base no afecta a la otra.

Además:

- `ComponentTemplateRepositoryTests` está anotada con `@Transactional`;
- cada uno de sus métodos se ejecuta dentro de una transacción de prueba;
- cambios como `deleteAllInBatch()` y `saveAllAndFlush()` se revierten al terminar cada método;
- `ComponentTemplateApiSystemTests` solo consulta el catálogo sembrado y no modifica datos.

Dentro de `ComponentTemplateRepositoryTests`, sus dos métodos comparten el contenedor de esa clase, pero el rollback transaccional evita que el escenario modificado de un método contamine al otro.

La prueba HTTP no está anotada con `@Transactional`. En una prueba con servidor real, el hilo que ejecuta el test y el hilo que atiende la petición no comparten automáticamente una única transacción de prueba. Como el caso actual es de solo lectura, no necesita limpieza adicional. Si se añaden endpoints que escriban, habrá que preparar y limpiar sus datos explícitamente o crear un entorno aislado apropiado.

## 16. ¿Cuántos contenedores se crean al ejecutar la suite?

En el backend actual hay dos clases que declaran su propio `PostgreSQLContainer`:

- `ComponentTemplateRepositoryTests`;
- `ComponentTemplateApiSystemTests`.

Al ejecutar ambas clases, Testcontainers crea dos PostgreSQL independientes, uno para cada clase. Con la ejecución secuencial habitual de la suite, no tienen por qué permanecer activos al mismo tiempo: el primero puede eliminarse antes de que comience el segundo.

Compartir un único contenedor entre todas las clases podría reducir algo el tiempo de arranque, pero introduciría estado compartido y más coordinación de limpieza. Con el tamaño actual de la suite se ha preferido la solución más explícita y aislada.

## 17. Dependencias incorporadas

La dependencia específica de REST Assured está declarada en `backend/pom.xml`:

```xml
<rest-assured.version>6.0.1</rest-assured.version>

<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>${rest-assured.version}</version>
    <scope>test</scope>
</dependency>
```

El alcance `test` significa que REST Assured está disponible al compilar y ejecutar pruebas, pero no se incluye como dependencia de producción del backend.

La prueba reutiliza las dependencias de Testcontainers y Spring Boot Test que ya se habían incorporado para las pruebas de integración:

- `spring-boot-testcontainers`;
- `testcontainers-junit-jupiter`;
- `testcontainers-postgresql`;
- los módulos de test de Spring Boot necesarios para MVC, JPA y Flyway.

No se añadió configuración global de REST Assured ni una clase base porque solo existe un caso y el puerto puede indicarse directamente. Si crece la suite, se deberá abstraer únicamente cuando exista duplicación real.

## 18. Qué errores puede detectar

La prueba fallaría ante cambios como:

- eliminación o cambio de la ruta `/api/v1/component-templates`;
- servidor que no arranca;
- conexión inválida con PostgreSQL;
- migración que no se puede aplicar;
- tabla o columnas incompatibles con JPA;
- catálogo sin las seis filas iniciales;
- consulta que no recupera las plantillas habilitadas;
- orden de negocio incorrecto;
- mapeo erróneo entre entidad y DTO;
- serialización con nombres o tipos inesperados;
- respuesta que deja de ser JSON;
- estado HTTP distinto de 200.

## 19. Qué no demuestra esta prueba

Una prueba aprobada no demuestra por sí sola que:

- el frontend consuma y represente correctamente la respuesta;
- el flujo funcione en un navegador real;
- todos los campos de todos los DTO tengan el valor correcto;
- los endpoints futuros de escritura funcionen;
- las respuestas de error tengan el contrato correcto;
- autenticación, autorización, CORS o seguridad estén cubiertos;
- el sistema soporte carga, concurrencia o fallos de red;
- una imagen de producción o un despliegue completo arranquen correctamente.

Esas responsabilidades pertenecen a otros casos o niveles de prueba. Mantener explícitos estos límites evita interpretar una sola prueba como cobertura total del producto.

## 20. Cómo ejecutar la prueba

### Requisitos

- Java 25, según `.java-version`;
- Docker Desktop iniciado y operativo;
- acceso a la imagen `postgres:18.6-alpine` la primera vez que deba descargarse.

No es necesario ejecutar `docker compose up` para esta prueba.

### Solo la prueba REST Assured

Desde `backend/`:

```bash
./mvnw -Dtest=ComponentTemplateApiSystemTests test
```

### Suite completa del backend

Desde `backend/`:

```bash
./mvnw verify
```

Una ejecución correcta debe terminar con `BUILD SUCCESS` y sin fallos ni errores de JUnit. Los números de puerto concretos pueden cambiar en cada ejecución y eso es normal.

## 21. Diagnóstico de fallos habituales

### Testcontainers no encuentra un entorno Docker

**Síntoma:** aparece un mensaje indicando que no se ha encontrado un entorno Docker válido o que no se puede conectar al daemon.

**Causa habitual:** Docker Desktop está detenido o todavía no ha terminado de arrancar.

**Comprobación:** verificar que `docker info` responde antes de repetir la suite.

### Falla el arranque de Flyway

**Síntoma:** Spring no termina de crear el contexto y el error menciona una migración.

**Qué revisar:** los SQL de `backend/src/main/resources/db/migration/`, el orden de versiones y la causa PostgreSQL mostrada en el log.

Este fallo ocurre antes de que REST Assured envíe la petición, porque el servidor todavía no está preparado.

### `Connection refused` en REST Assured

**Qué revisar:** que se esté usando `.port(port)` con el valor de `@LocalServerPort` y que Spring Boot haya terminado de arrancar.

No debe fijarse manualmente el puerto 8080 en este test.

### Respuesta 200 pero falla el cuerpo

Gracias a `.then().log().ifValidationFails()`, REST Assured imprime la respuesta recibida. Hay que comparar su JSON con la aserción que falla y decidir si ha cambiado el contrato, los datos de Flyway, el orden del servicio o el mapper.

### La prueba pasa sola pero falla con toda la suite

Esto suele señalar estado compartido o configuración global mutable. En la implementación actual cada clase tiene su propia base, por lo que conviene revisar primero cualquier nueva configuración estática de REST Assured, reutilización de contenedores o escritura HTTP añadida después.

## 22. Criterios para ampliar la suite

Al añadir casos REST Assured:

1. Probar comportamientos observables de la API, no métodos internos.
2. Mantener la petición contra el servidor iniciado con `RANDOM_PORT`.
3. Preparar datos de forma explícita cuando el escenario no proceda de Flyway.
4. No depender del PostgreSQL persistente de desarrollo.
5. Validar lo necesario para expresar el contrato sin duplicar cada detalle sin valor.
6. Añadir casos negativos cuando existan entradas, validaciones o errores públicos.
7. Limpiar datos escritos, porque un rollback en el hilo del test no revierte automáticamente una transacción procesada por el servidor.
8. Evitar configuración compartida mutable de REST Assured si las pruebas pueden ejecutarse en paralelo.
9. Mantener REST Assured con alcance Maven `test`.
10. Ejecutar tanto el caso focalizado como `./mvnw verify`.

## 23. Mapa de ficheros relacionados

| Fichero | Papel |
| --- | --- |
| `backend/src/test/java/es/codeurjc/infracture/catalog/api/ComponentTemplateApiSystemTests.java` | Prueba de sistema HTTP |
| `backend/pom.xml` | Dependencias y versión de REST Assured |
| `backend/src/main/java/es/codeurjc/infracture/catalog/api/ComponentTemplateController.java` | Ruta HTTP probada |
| `backend/src/main/java/es/codeurjc/infracture/catalog/api/ComponentTemplateDTO.java` | Contrato serializado |
| `backend/src/main/java/es/codeurjc/infracture/catalog/api/ComponentTemplateMapper.java` | Conversión de entidad a DTO |
| `backend/src/main/java/es/codeurjc/infracture/catalog/application/ComponentTemplateService.java` | Filtrado y orden del catálogo |
| `backend/src/main/java/es/codeurjc/infracture/catalog/persistence/ComponentTemplateRepository.java` | Acceso a PostgreSQL |
| `backend/src/main/resources/application.properties` | Flyway activo y validación de esquema de Hibernate |
| `backend/src/main/resources/db/migration/V1__initialize_platform_database.sql` | Primera migración aplicada |
| `backend/src/main/resources/db/migration/V2__create_component_template_catalogue.sql` | Tabla e inserción de las seis plantillas |
| `backend/src/test/java/es/codeurjc/infracture/catalog/persistence/ComponentTemplateRepositoryTests.java` | Pruebas de integración con otro PostgreSQL temporal |
| `backend/src/test/java/es/codeurjc/infracture/catalog/application/ComponentTemplateServiceTests.java` | Pruebas unitarias sin Spring ni Docker |

## 24. Referencias oficiales

- [REST Assured: guía de uso](https://github.com/rest-assured/rest-assured/wiki/Usage)
- [Spring Boot: pruebas con un servidor en ejecución y puerto aleatorio](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- [Spring Boot: conexiones de servicio con Testcontainers](https://docs.spring.io/spring-boot/reference/features/dev-services.html#features.dev-services.testcontainers)
- [Testcontainers: integración con JUnit 5](https://java.testcontainers.org/test_framework_integration/junit_5/)
- [Flyway: funcionamiento de las migraciones](https://documentation.red-gate.com/fd/migrations-271585107.html)
