---
status: accepted
date: 2026-11-22
---

# Eventos de ejecución por SSE con aviso de PostgreSQL

## Contexto

Mientras un experimento corre, la página debe mostrar lo que pasa: cada paso del ciclo de vida, el estado de cada nodo y lo que imprimen los contenedores (#92, que absorbió #94). Hasta ahora el canvas preguntaba por REST cada pocos segundos y solo veía el estado del momento, sin historia ni logs.

Hay cinco condiciones:

- **Sin pérdidas ni duplicados.** Una pestaña que pierde la conexión, o cuyo token de 5 minutos caduca ([ADR 0002](0002-proteccion-csrf-con-jwt-en-cookies.md)), debe seguir donde lo dejó.
- **Sobrevivir a un reinicio.** Lo ocurrido debe poder leerse después de reiniciar el backend ([ADR 0005](0005-recuperacion-sin-reanudacion-automatica.md)).
- **Solo el propietario.** Una cuenta desactivada pierde el acceso.
- **Logs limpios.** Ningún secreto conocido debe guardarse en la base de datos ni llegar al navegador.
- **Aislamiento.** Una pestaña lenta no puede frenar al reconciler ni a las demás.

## Decisión

### 1. Los eventos son filas con un id que sirve de cursor

Cada hecho se guarda en `execution_events` (V16). Cada fila tiene:

- `id` creciente.
- Ejecución.
- Nodo opcional.
- `kind`: `STATE`, `EVENT` o `LOG`.
- `at`.
- `payload` JSON con lo que cambia según el tipo. Los `EVENT` llevan un `type`: `PHASE`, `STOP`, `CLEANUP_FAILED`, `RESPONSE` o `LOGS_OMITTED`.

`ExecutionJournal` escribe cada evento **en la misma transacción** que el cambio que describe. Para eso compara una foto (`Mark`) de antes con el estado de después.

El `id` es el cursor. El navegador recuerda el último que recibió y pide solo lo posterior.

### 2. Se envían por Server-Sent Events

`GET …/executions/{id}/events` responde `text/event-stream`. Cada mensaje lleva:

- `id:`, el cursor.
- `event:`, que es `state`, `event` o `log`.
- `data:`, el evento en JSON.

Para continuar, el navegador envía `Last-Event-ID` al reconectar solo, o `?after=` al reabrir. Si el cursor ya no existe, el servidor envía `reset` con el estado completo y sigue desde el evento más nuevo.

### 3. `SseEmitter` con un hilo virtual por flujo

Se usa Spring MVC, igual que el resto de la API. Cada flujo corre en su propio hilo virtual y lee mediante `ExecutionFeed`, con transacciones cortas. Ningún flujo comparte hilo con el reconciler ni con otro flujo.

### 4. PostgreSQL avisa y el flujo lee

La migración V17 añade un trigger `AFTER INSERT` en `execution_events`. El trigger ejecuta `pg_notify('execution_events', <id de la ejecución>)`.

`ExecutionEventSignal` funciona así:

- Mantiene **una conexión propia, fuera del pool**, que hace `LISTEN execution_events`.
- Despierta solo a los flujos de la ejecución avisada.
- Cada flujo lee entonces de la tabla todo lo posterior a su cursor.

El aviso no transporta eventos: solo dice «vuelve a leer». Por eso hay tres salvaguardas:

- **Un aviso perdido no pierde eventos, solo los retrasa.** Aunque no llegue ningún aviso, cada flujo lee al menos una vez por latido (15 s).
- **Sin escucha, se vuelve a sondear.** Si la conexión de escucha se cae, `isListening()` es falso y los flujos leen cada segundo. La escucha se reintenta cada segundo.
- **Al reconectar, todos leen una vez.** Los eventos guardados mientras nadie escuchaba no tuvieron aviso, así que cada flujo lee una vez más.

Un flujo toma la «versión» de su ejecución **antes** de leer y espera con ella **después**. Así, un aviso que llega mientras lee no se pierde.

### 5. El flujo dura lo que el token con el que se abrió

Cuando el token de acceso caduca, el servidor envía `renew` con el cursor y cierra. La página renueva la sesión y reabre con `?after=`. Cada lectura comprueba además que la cuenta sigue activa.

### 6. Open Session in View apagado

`spring.jpa.open-in-view=false`. Los servicios devuelven un `ExecutionView` con todo lo que necesita la respuesta, cargado dentro de la transacción.

### 7. Los logs se acotan y se limpian antes de guardarse

Límites:

- 100 líneas por vuelta del reconciler; el resto se guarda en la siguiente.
- 1.000 líneas por nodo y ejecución. Lo que pase de ahí solo se cuenta en un `LOGS_OMITTED`.
- 500 caracteres por línea.

`LogRedactor` sustituye:

- Los secretos del propio backend, por su valor exacto.
- El valor que sigue a `password=`, `token=`, `secret=` o `api_key=`.
- Los tokens Bearer y los JWT.

### 8. Límites y cierre

- Como mucho 200 eventos por lectura.
- Un comentario de latido cada 15 s.
- Cinco flujos por cuenta y proceso; el sexto recibe `429`.

Al apagarse el backend, cada flujo termina enseguida. Los navegadores reconectan solos.

## Motivo y consecuencias

### Transporte

| Alternativa | Por qué no |
| --- | --- |
| Seguir preguntando por REST cada pocos segundos | Cada pregunta repite el estado completo, no trae historia y obliga a elegir entre retraso y carga. |
| WebSocket | Es bidireccional y aquí solo habla el servidor; Run y Stop ya van por REST. Habría que programar la reconexión y el cursor, que `EventSource` trae hechos (`Last-Event-ID`), y adaptar la autenticación por cookies y CSRF, que en SSE es la de cualquier `GET`. |
| WebFlux (`Flux<ServerSentEvent>`) | Añade un segundo modelo de programación, el reactivo, a una aplicación MVC. Con hilos virtuales, un hilo bloqueado por flujo cuesta poco, así que no compensa. |

### De dónde salen los eventos

| Alternativa | Por qué no |
| --- | --- |
| Solo en memoria (publicar y olvidar) | Lo que pasa mientras la pestaña está desconectada, o antes de un reinicio, se pierde. Con varios backends, cada uno solo vería lo suyo. |
| Un broker (Redis Streams, Kafka, RabbitMQ) | Es un servicio más que instalar, vigilar y respaldar en la máquina del usuario. PostgreSQL ya guarda los datos y ya sabe avisar. |
| Ordenar por la hora `at` en lugar del `id` | Dos eventos pueden tener la misma hora y el reloj puede retroceder. El `id` es único y siempre crece. |

### Cómo se entera el flujo de que hay algo nuevo

| Alternativa | Por qué no |
| --- | --- |
| Sondear la tabla cada segundo (primera versión de #92) | Funciona y tiene menos piezas, pero tarda hasta un segundo y hace una consulta por segundo por cada pestaña aunque no pase nada. Se conserva como respaldo cuando la escucha está caída. |
| Avisar desde Java (`ApplicationEventPublisher`) | Solo avisa dentro del mismo proceso, y hay que acordarse de avisar en cada sitio que guarda eventos. Además, el aviso podría salir antes del commit. |
| `NOTIFY` desde el código en lugar de un trigger | Igual que el anterior: depende de no olvidarlo en cada escritura. El trigger se ejecuta con cada inserción, venga de donde venga. |
| Replicación lógica o Debezium | Sirve para copiar cambios entre sistemas. Aquí solo hace falta despertar a quien espera. |
| Escuchar con una conexión del pool | Quitaría una conexión al pool para siempre, y el pool está pensado para préstamos cortos. Una conexión propia se reconecta con sus propias reglas. |

Por qué este diseño, en concreto:

- **PostgreSQL entrega `NOTIFY` solo cuando la transacción hace commit.** Nunca se avisa de algo que luego se deshace.
- **PostgreSQL junta los avisos repetidos de una misma transacción.** Guardar 100 líneas de log produce un solo aviso por ejecución.
- **El aviso lleva solo el id de la ejecución**, muy lejos del límite de 8.000 bytes de `NOTIFY`.

Lo que se acepta a cambio:

- **Una conexión más, abierta siempre.** El backend la reconecta solo.
- **La escucha necesita conexión directa a PostgreSQL.** Si en un despliegue se pone PgBouncer en modo transacción, `LISTEN` no funciona a través de él.
- **Una cuenta desactivada pierde su flujo en menos de 15 s**, en lugar de en menos de un segundo, porque sin eventos la comprobación ocurre en cada latido.
- **Hay más que probar**: la entrega tras el commit, el aislamiento por ejecución, la reconexión y el respaldo.

### Sesión y seguridad

- **Cerrar el flujo al caducar el token** obliga a reconectar cada 5 minutos, pero cada reapertura vuelve a pasar por la autenticación. Un flujo que ignorase la caducidad seguiría enviando datos con un token que ya no vale.
- **Redactar antes de guardar** evita que un secreto se copie a la base de datos, a las copias de seguridad y al navegador. No protege frente a quien ya controla el proceso del backend: ese caso lo cubren las capas de fuera (`docker-proxy`, usuario sin privilegios, secretos fuera del código; [ADR 0013](0013-motor-de-ejecucion-con-docker-compose.md)).
- **Redactar tiene un límite.** Los patrones no reconocen un secreto impreso sin nombre delante. Hoy el usuario no puede introducir secretos: los nodos usan imágenes propias con variables fijas. Cuando pueda, la solución es marcarlos como secretos y sustituirlos por su valor exacto, como hacen GitHub Actions o GitLab CI.

### Open Session in View

Con OSIV encendido, la petición SSE retendría una conexión del pool durante toda la vida del flujo, minutos sin usarla. Con unas diez pestañas abiertas, el pool (10 conexiones por defecto) se agotaría y toda la aplicación se pararía.

Apagarlo obliga a que el servicio cargue todo lo que la respuesta necesita (`ExecutionView`). A cambio, ninguna consulta queda escondida en el controlador o el mapper: si falta algo, los tests fallan con `LazyInitializationException`.

### Límites

- **200 eventos por lectura** acotan cada respuesta. Si la página llega llena, se vuelve a leer sin esperar.
- **El latido de 15 s** evita que proxies y navegadores corten un flujo silencioso.
- **Cinco flujos por cuenta** acotan conexiones e hilos. El contador es de cada proceso; con varios backends, cada uno aplica el suyo.
- Con HTTP/1.1 el navegador abre como mucho seis conexiones por dominio, y cada flujo ocupa una. El despliegue debe servir la aplicación con HTTP/2.

## Aplicación

Código:

- Escritura: [V16](../../backend/src/main/resources/db/migration/V16__create_execution_events.sql), [V17](../../backend/src/main/resources/db/migration/V17__notify_execution_events.sql), [ExecutionEvent](../../backend/src/main/java/es/codeurjc/infracture/execution/domain/ExecutionEvent.java), [ExecutionJournal](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionJournal.java), [LogRedactor](../../backend/src/main/java/es/codeurjc/infracture/execution/application/LogRedactor.java).
- Aviso y lectura: [ExecutionEventSignal](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionEventSignal.java), [ExecutionFeed](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionFeed.java), [ExecutionView](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ExecutionView.java).
- Envío: [ExecutionEventStream](../../backend/src/main/java/es/codeurjc/infracture/execution/api/ExecutionEventStream.java).
- Navegador: [`useExecutionEvents`](../../frontend/app/features/scenario-canvas/use-execution-events.ts).

Pruebas:

- [ExecutionEventSignalIntegrationTests](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ExecutionEventSignalIntegrationTests.java): aviso tras el commit, aislamiento por ejecución, reconexión.
- [ExecutionEventStreamTests](../../backend/src/test/java/es/codeurjc/infracture/execution/api/ExecutionEventStreamTests.java): orden, cursor, `reset`, `renew`, latido, cierre al apagar.
- [ExecutionApiSystemTests](../../backend/src/test/java/es/codeurjc/infracture/execution/api/ExecutionApiSystemTests.java): un flujo abierto recibe un evento nuevo sin sondear.

El funcionamiento completo se explica en la [arquitectura de ejecución](../EXECUTION_ARCHITECTURE.md#113-muestras-eventos-y-sse).

## Referencias

- [WHATWG: Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html).
- [PostgreSQL: NOTIFY](https://www.postgresql.org/docs/current/sql-notify.html) y [LISTEN](https://www.postgresql.org/docs/current/sql-listen.html).
- [Spring Framework: peticiones asíncronas y SSE en Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html).
- [Spring Boot: apagado ordenado](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html).
- [JEP 444: hilos virtuales](https://openjdk.org/jeps/444).
