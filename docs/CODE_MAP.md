# Mapa del código

Este documento sirve para explicar cualquier pieza de Infracture en un minuto: qué hace, quién la usa y dónde seguir leyendo. No repite el diseño. El porqué de cada decisión está en la [arquitectura de ejecución](EXECUTION_ARCHITECTURE.md), en los [ADR](adr/) y en el [glosario](../GLOSSARY.md).

**Cómo se mantiene.** Cada PR que añade, renombra o cambia el papel de una clase actualiza aquí su línea. Una clase cuyo papel no cabe en una frase es una señal de que hace demasiado.

## Índice

- [Cómo está organizado](#cómo-está-organizado)
- [Recorridos principales](#recorridos-principales)
- [Backend](#backend)
- [Frontend](#frontend)
- [Fuera de las aplicaciones](#fuera-de-las-aplicaciones)
- [Explicaciones visuales](#explicaciones-visuales)

## Cómo está organizado

**Backend** ([`backend/src/main/java/es/codeurjc/infracture`](../backend/src/main/java/es/codeurjc/infracture)). Es un monolito modular de Spring Boot. Cada módulo es un paquete con cuatro capas, y una petición siempre las recorre en el mismo orden:

```
api (controlador + DTO + mapper) → application (servicio) → domain (entidades y reglas) → persistence (repositorio)
```

| Módulo | Responsabilidad |
| --- | --- |
| `identity` | Cuentas, registro, administrador inicial y política de contraseñas. |
| `security` | Inicio de sesión con JWT en cookies, CSRF y qué rutas son públicas. |
| `catalog` | Plantillas de componentes y el contrato de cada tipo: perfiles, parámetros, puertos y capacidades. |
| `project` | Proyectos, escenarios, el grafo (nodos y conexiones) y su validación. |
| `execution` | Run y Stop: reserva, plan de Compose, motor de Docker y bucle de control. |
| `shared` | Excepciones comunes y su traducción a respuestas HTTP. |

**Frontend** ([`frontend/app`](../frontend/app)). Es una SPA de React con React Router. Cada ruta de `routes/` carga sus datos en un `clientLoader` y guarda en un `clientAction`; la lógica de cada área vive en `features/<área>/`, con `api/` (llamadas HTTP), `model/` (tipos de los DTO) y `components/`.

## Recorridos principales

### Registrarse e iniciar sesión

1. `routes/register.tsx` envía el formulario con `auth-service.register` a `POST /api/v1/users`.
2. `UserController` → `UserService.registerUser` valida con `PasswordPolicy`, cifra la contraseña (`EncoderConfig`) y guarda el `User`.
3. `auth-service.login` llama a `POST /api/v1/auth/login`; `AuthController` → `UserLoginService` comprueba las credenciales y `JwtTokenProvider` deja los tokens de acceso y renovación en cookies `HttpOnly`.
4. En cada petición, `JwtRequestFilter` lee la cookie y autentica al usuario. Las que cambian datos llevan además la cabecera CSRF, que `api-fetch.ts` obtiene de `GET /api/v1/auth/csrf`.
5. Si el token de acceso caduca, `api-fetch.ts` llama a `POST /api/v1/auth/refresh` y repite la petición. `private-layout.tsx` (`requireUser`) manda a `/login` a quien no tenga sesión.

### Diseñar y guardar un escenario

1. `routes/scenario.tsx` carga a la vez proyecto, escenario, grafo, catálogo, perfiles de carga y la ejecución actual, y monta `ScenarioCanvas`.
2. Cada cambio del lienzo (añadir, mover, conectar, configurar, la carga del panel Load) es una acción de `graph-reducer.ts`; `draft-storage.ts` guarda el trabajo sin guardar en el navegador.
3. Guardar envía el grafo entero con la revisión de partida: `graph-service.saveGraph` → `PUT …/graph` → `ScenarioController` → `ScenarioService.saveGraph`.
4. `ScenarioService` bloquea la fila del escenario, rechaza la escritura si una ejecución lo reserva y llama a `Scenario.replaceGraph`, que comprueba la revisión (409 si otra pestaña guardó antes) y las reglas estructurales.
5. `ScenarioValidator` revisa el grafo guardado contra los contratos de `ContractCatalog` y devuelve errores y avisos; el lienzo los pinta en los nodos, las flechas y el panel de incidencias.

### Run → Stop

1. `RunButton` → `use-execution.ts` → `execution-service.runScenario` → `POST …/executions` con la revisión guardada.
2. `ExecutionService.run` bloquea el escenario, comprueba que no hay otra ejecución, vuelve a validar, guarda la `Execution` en `RESERVED` con la copia congelada de nodos, conexiones y carga (`ExecutedWorkload`) y le adjunta el plan de `PlanCompiler`, que incluye a k6 con el perfil de Compose `load`. No toca Docker.
3. `ExecutionReconciler`, cada 2 s, ve la reserva, pasa a `STARTING` y pide a `ComposeRuntimeEngine` un `docker compose up --wait` a través de `docker-proxy`, que arranca solo los nodos. Al estar sanos, `startLoad` lanza k6, la fase pasa a `RUNNING` y `ServiceProbe` pide la respuesta real del servicio.
4. Mientras corre, cada vuelta lee `docker compose ps` y guarda el estado de cada nodo en `ExecutionObservations`; el front lo consulta con `GET …/executions/current` cada 2 s y lo muestra en `ExecutionBadge`, con la cuenta atrás hasta el tiempo límite, en los nodos, en el inspector y, para la carga, en el panel Load. El editor está en solo lectura. Al llegar el plazo que se fijó en `RUNNING`, el bucle registra `TIME_LIMIT` y limpia como en Stop.
5. Stop solo apunta la causa (`POST …/executions/{id}/stop`); el bucle para k6 con `stopLoad` y guarda su resumen, hace `down -v`, borra lo que quede con las etiquetas y libera la reserva. Si k6 termina antes por sí solo, su resumen se guarda en cuanto la vigilancia lo ve parado. Si Stop llega mientras arranca, el motor corta el arranque y la siguiente vuelta limpia. Si la limpieza falla, la ejecución sigue en `STOPPING` con el error a la vista y se reintenta. Detalle completo en la [página De Run a Stop](explainers/run-to-stop.html).

## Backend

Rutas relativas a `backend/src/main/java/es/codeurjc/infracture/`. Los `*DTO` son la forma JSON de la API y los `*Mapper` (MapStruct) convierten entre DTO y dominio; se listan juntos por módulo.

### `identity`

| Clase | Qué hace | Quién la usa |
| --- | --- | --- |
| `UserController` | `POST /api/v1/users` (registro) y `GET /api/v1/users/me`. | Front: `auth-service`. |
| `AuthController` | `login`, `refresh`, `logout` y `csrf` bajo `/api/v1/auth`. | Front: `auth-service`, `api-fetch`. |
| `UserExceptionHandler` | Convierte usuario repetido y contraseña rechazada en errores 409 y 400. | Spring, ante esas excepciones. |
| `RegisterUserDTO`, `UserDTO`, `UserMapper` | Datos de registro y de la cuenta visible; rechazan campos desconocidos. | Controladores de `identity`. |
| `UserService` | Registra usuarios, comprueba duplicados y crea el administrador inicial. | Controladores, `AdminInitializer`, seguridad. |
| `PasswordPolicy` | Exige de 15 a 128 caracteres y rechaza las contraseñas más comunes de una lista incluida. | `UserService`. |
| `AdminInitializer` | Crea al arrancar el único administrador con las variables `INFRACTURE_ADMIN_*`. | Spring, al iniciar. |
| `PasswordPolicyException`, `UserAlreadyExistsException` | Errores de registro con su mensaje para el usuario. | `UserService`, `UserExceptionHandler`. |
| `User`, `UserRole` | La cuenta: nombre, correo, contraseña cifrada, rol y si está activa. | Todo `identity` y `project`. |
| `UserRepository` | Búsquedas por correo y nombre, sin distinguir mayúsculas. | `UserService`, `RepositoryUserDetailsService`. |

### `security`

| Clase | Qué hace | Quién la usa |
| --- | --- | --- |
| `SecurityConfig` | La cadena de filtros: rutas públicas, CSRF con cookie, sesión sin estado y filtro JWT. | Spring Security. |
| `EncoderConfig` | El cifrado de contraseñas con Argon2. | `UserService`, autenticación. |
| `RepositoryUserDetailsService` | Carga el usuario por correo para Spring Security. | Autenticación. |
| `JwtTokenProvider` | Crea, firma, lee y valida los tokens de acceso y renovación. | `UserLoginService`, `JwtRequestFilter`. |
| `JwtRequestFilter` | Autentica cada petición con el token de la cookie. | `SecurityConfig`. |
| `UserLoginService` | Inicia sesión, renueva y cierra, escribiendo o borrando las cookies. | `AuthController`. |
| `UnauthorizedHandlerJwt` | Responde 401 cuando falta una sesión válida. | `SecurityConfig`. |
| `LoginRequest`, `AuthResponse`, `TokenType` | Cuerpo del login, respuesta y tipo de token. | `AuthController`, `UserLoginService`. |

### `catalog`

| Clase | Qué hace | Quién la usa |
| --- | --- | --- |
| `ComponentTemplateController` | `GET /api/v1/component-templates`, paginado y con filtros por nombre y tipo. | Front: catálogo y lienzo. |
| `WorkloadProfileController`, `WorkloadProfileDTO` | `GET /api/v1/workload-profiles`: cada perfil de carga con sus límites, la semilla, lo que pide al objetivo y sus valores iniciales. | Front: lienzo (panel Load). |
| `ComponentTemplateDTO`, `TemplateContractDTO`, `ComponentConfigurationDTO`, `ComponentTemplateMapper` | La plantilla en JSON con su contrato y su configuración inicial; el mapper también convierte los perfiles de carga. | `ComponentTemplateController`, `WorkloadProfileController`. |
| `ComponentTemplateService` | Lista las plantillas activas y las busca por clave. | Controlador, `ScenarioService`. |
| `ContractCatalog` | Reúne el contrato de cada tipo y no deja arrancar si a un tipo le falta o le sobra uno. | Validación, `ExecutionService`. |
| `HttpServiceDefinition`, `WorkerDefinition`, `PostgresqlDefinition`, `RedisDefinition`, `RabbitmqDefinition` | El contrato de cada tipo: perfiles, parámetros con su rango, puertos y capacidades. | `ContractCatalog`. |
| `ComponentTemplate` | Una ficha del catálogo guardada en la base de datos. | Servicio y repositorio. |
| `ComponentType`, `Capability` | Los cinco tipos y lo que un nodo ofrece a una flecha (por ejemplo, caché). | Todo el dominio. |
| `TemplateContract`, `ProfileContract`, `ParameterContract`, `PortContract`, `ContractViolation` | Las piezas de un contrato y el resultado de comprobar una configuración. `ParameterContract.check` comprueba los parámetros de nodos y carga. | Definiciones, `ScenarioValidator`. |
| `WorkloadContract` | Los perfiles de carga: hoy solo `CONSTANT_HTTP`, con sus límites (ADR 0016), la semilla, la capacidad que pide al objetivo y sus valores iniciales. | `ScenarioValidator`, `WorkloadProfileController`. |
| `ComponentConfiguration`, `ComponentTypeDefinition` | Perfil más parámetros de un nodo; interfaz que implementa cada definición. | Dominio de `project` y `catalog`. |
| `ComponentTemplateRepository` | Búsquedas de plantillas activas, ordenadas por nombre. | `ComponentTemplateService`. |

### `project`

| Clase | Qué hace | Quién la usa |
| --- | --- | --- |
| `ProjectController` | Crear, listar, leer y editar proyectos propios. | Front: `project-service`. |
| `ScenarioController` | Escenarios de un proyecto, y leer o guardar su grafo (`/graph`). | Front: `scenario-service`, `graph-service`. |
| `*DTO`, `ProjectMapper`, `ScenarioMapper` | Proyecto, escenario, grafo, nodo, conexión, carga (`WorkloadDTO`), posición e incidencia en JSON. | Controladores de `project`, `ExecutionExceptionHandler`. |
| `ProjectService` | Reglas de los proyectos: solo el dueño los ve, nombres válidos y normalizados. | `ProjectController`, `ScenarioService`. |
| `ScenarioService` | Escenarios y grafo: bloquea la fila al escribir, impide editar si hay una ejecución y devuelve el grafo con sus incidencias. | `ScenarioController`, `ExecutionService`. |
| `ScenarioValidator` | Revisa el escenario guardado contra los contratos: rangos, conexiones obligatorias, ciclos, escenario vacío, RabbitMQ sin consumidor y la carga (sin carga, sin objetivo, objetivo sin `HTTP`, límites). Nunca impide guardar. | `ScenarioService`, `ExecutionService`. |
| `ScenarioGraph` | El escenario más sus incidencias, lo que devuelve el servicio. | `ScenarioService`, controlador. |
| `ScenarioReservations` | Pregunta "¿hay una ejecución usando este escenario?". La implementa `execution`, así `project` no depende de él. | `ScenarioService`. |
| `DetailNormalizer` | Recorta y normaliza nombres y descripciones. | `ProjectService`, `ScenarioService`. |
| `Project`, `Scenario` | Las entidades. `Scenario.replaceGraph` comprueba la revisión y las reglas estructurales del grafo y de su carga. | Servicios. |
| `Workload` | La carga del escenario: objetivo (puede quedar vacío), perfil, parámetros y semilla. No es un nodo; se guarda con el grafo en `scenario_workload`. | `Scenario`, validador, `Execution`. |
| `Component`, `ComponentDraft`, `Connection` | Un nodo guardado, un nodo recibido para guardar y una flecha (origen, puerto, destino). | `Scenario`, validador, `Execution`. |
| `ValidationIssue` | Una incidencia: gravedad, código, mensaje, elemento y campo. | Validador, front. |
| `ProjectRepository`, `ScenarioRepository` | Búsquedas por dueño y proyecto; `findForUpdate…` bloquea la fila. | Servicios. |

### `execution`

| Clase | Qué hace | Quién la usa |
| --- | --- | --- |
| `ExecutionController` | Run (`POST …/executions`), Stop (`…/{id}/stop`) y la ejecución actual (`…/current`). | Front: `execution-service`. |
| `ExecutionDTO`, `ExecutedComponentDTO`, `ExecutedWorkloadDTO`, `RunExecutionDTO`, `ExecutionMapper` | La ejecución en JSON con cada nodo, su estado y su respuesta, y la carga congelada con el resumen de k6. | `ExecutionController`. |
| `ExecutionExceptionHandler` | Convierte un Run con errores en 422 con la lista de incidencias. | Spring. |
| `ExecutionService` | Reserva la revisión exacta (idempotente, una sola activa), apunta Stop y lee la actual. No toca Docker. | `ExecutionController`. |
| `ExecutionReservations` | Implementa `ScenarioReservations`: el escenario está reservado mientras haya una ejecución sin liberar. | `ScenarioService`. |
| `PlanCompiler` | Convierte la ejecución en un plan de Compose endurecido, con etiquetas y límites, sin secretos. Añade el servicio `workload` de k6 con el perfil `load`. | `ExecutionService`, reconciler. |
| `ComponentDriver`, `HttpServiceDriver` | Lo que cada tipo añade al plan: imagen, variables, salud, dónde pedir su respuesta y a qué URL envía la carga (`httpUrl`). | `PlanCompiler`. |
| `ExecutionReconciler` | El bucle de cada 2 s: arranca los nodos y después k6 (y cancela el arranque si llega Stop), vigila cada nodo y la carga, aplica el tiempo límite, para k6 y guarda su resumen, limpia y libera. Reintenta una limpieza fallida tres veces, con pausas, y luego espera a Stop. | Spring (`@Scheduled`). |
| `ExecutionScheduling` | Enciende el bucle; los tests de API lo apagan con una propiedad. | Spring. |
| `RuntimeEngine`, `ComposeRuntimeEngine` | Habla con Docker con el CLI: `up --wait`, `startLoad`, `stopLoad` (para k6 y lee su código y su resumen), `ps`, `down -v`, barrido por etiquetas y unirse a la red. Mientras arranca, pregunta cada segundo si debe parar y, si es así, termina el comando. | Reconciler. |
| `LoadReport` | Lo que dejó k6: código de salida (0 terminó, 105 lo paró Stop, vacío si nunca arrancó) y resumen. | Motor, reconciler. |
| `RuntimeEngineException`, `StartCancelledException` | Docker no pudo hacer algo; su mensaje acaba como motivo del fallo o de la limpieza fallida. La segunda dice que el arranque se canceló por Stop, que no es un fallo. | Motor, reconciler. |
| `ComponentStatus` | Estado de un nodo según Docker: arrancando, sano, no sano o parado. | Motor, observaciones, DTO. |
| `ExecutionObservations` | Lo último visto de cada nodo (estado y respuesta), en memoria. Atajo hasta P08. | Reconciler (escribe), mapper (lee). |
| `ServiceProbe` | Una petición `GET /` al servicio como evidencia de que funciona. | Reconciler. |
| `ScenarioNotRunnableException` | Run encontró errores; lleva las incidencias. | `ExecutionService`, handler. |
| `Execution` | La entidad: fase, causa de parada (gana la más grave), resultado, plazo del tiempo límite, copia congelada y plan. Lleva aparte los fallos de limpieza, que no cambian el resultado. | Servicio, reconciler. |
| `ExecutedComponent` | Copia inmutable de un nodo tal como estaba al reservar. | `Execution`, métricas futuras. |
| `ExecutedWorkload`, `LoadSummary` | Copia de la carga al reservar y el resumen agregado de k6 al terminar; lo que k6 no midió queda vacío. Se guarda el primer resumen. | `Execution`, reconciler, DTO. |
| `Phase`, `StopCause`, `Outcome` | Qué hace el motor, por qué para y cómo acabó. | `Execution`, DTO. |
| `ExecutionRepository` | La ejecución activa (con bloqueo y nodos), la última de un escenario y si un escenario está reservado. | Servicio, reconciler, reservas. |

### `shared` y arranque

| Clase | Qué hace | Quién la usa |
| --- | --- | --- |
| `ConflictException`, `InvalidDataException`, `NotFoundException` | Errores de negocio comunes: 409, 400 y 404. | Todos los servicios. |
| `ApiExceptionHandler` | Traduce cualquier error a `application/problem+json` con un mensaje claro. | Spring. |
| `InfractureBackendApplication` | Punto de entrada de Spring Boot. | `./mvnw spring-boot:run`, imagen. |

## Frontend

Rutas relativas a `frontend/app/`. Los ficheros de `features/*/model/` son los tipos de los DTO del backend con el mismo nombre; no se listan uno a uno.

### Rutas (`routes/`)

| Fichero | Qué hace |
| --- | --- |
| `home.tsx`, `component-catalogue.tsx` | Página de inicio y catálogo público de plantillas. |
| `register.tsx`, `login.tsx`, `logout.tsx`, `account.tsx` | Alta, inicio y cierre de sesión, y la página de la cuenta. |
| `private-layout.tsx` | Envuelve las páginas privadas y exige sesión (`requireUser`). |
| `projects.tsx`, `new-project.tsx`, `project.tsx` | Lista, alta y detalle editable de proyectos. |
| `new-scenario.tsx`, `scenario.tsx` | Alta de escenario y el editor; `scenario.tsx` carga todo lo que necesita el lienzo, también la ejecución actual. |

### `shared/` y `components/`

| Fichero | Qué hace |
| --- | --- |
| `shared/api/api-fetch.ts` | Todas las llamadas HTTP: añade CSRF, renueva la sesión ante un 401 y convierte los errores en `HttpError`. |
| `components/app-header.tsx`, `logo.tsx`, `paged-list.tsx` | Barra de las páginas privadas, isotipo y lista paginada. `components/ui/` son los componentes de shadcn sin cambios. |

### `features/auth` y `features/projects`

| Fichero | Qué hace |
| --- | --- |
| `auth/api/auth-service.ts`, `auth/session.ts` | Llamadas de cuenta y sesión; `requireUser` y la redirección segura tras iniciar sesión. |
| `auth/components/*` | Formularios de acceso y registro, campo de contraseña y la banda térmica de la portada de acceso. |
| `projects/api/*` | Llamadas de proyectos y escenarios. |
| `projects/components/*`, `details-form-data.ts` | Tarjetas, migas, formulario de nombre y descripción, edición en la propia página y página de error o 404. |

### `features/scenario-canvas`

| Fichero | Qué hace |
| --- | --- |
| `components/scenario-canvas.tsx` | El editor completo: barra superior, paleta, lienzo de React Flow, avisos y panel lateral. Decide qué se puede hacer en cada momento (guardar, Run, solo lectura). |
| `graph-reducer.ts` | El estado del grafo y todas sus acciones: añadir, mover, conectar, configurar, la carga, guardar y conflicto. |
| `connections.ts` | Identidad de las flechas y si un puerto puede conectarse a un nodo. |
| `draft-storage.ts` | El borrador sin guardar en `localStorage`, por cuenta y escenario, con la carga. |
| `api/graph-service.ts`, `api/execution-service.ts` | Leer y guardar el grafo; Run, ejecución actual y Stop. |
| `component-catalogue/api/workload-profile-service.ts` | Lee los perfiles de carga con sus límites (`GET /api/v1/workload-profiles`). |
| `use-execution.ts` | Estado de la ejecución, Run y Stop sin dobles envíos (Stop también reintenta una limpieza fallida), y la consulta cada 2 s mientras está activa. |
| `execution-status.ts` | Palabra y tono de cada fase, de cada estado de nodo y de la carga, con el vocabulario de `DESIGN.md`, y el texto de la cuenta atrás. |
| `components/component-palette.tsx` | Lista de plantillas para añadir con un clic o arrastrando. |
| `components/component-node.tsx` | Cómo se dibuja un nodo: configuración, puertos, incidencias, estado en el experimento y el carril de tráfico si recibe la carga. |
| `components/traffic-lane.tsx` | El carril de la carga que entra en su objetivo: frecuencia y peticiones que lo recorren mientras k6 envía tráfico (animación en `app.css`). |
| `components/node-inspector.tsx` | Formulario del nodo seleccionado, sus conexiones y la sección "In the experiment" con la respuesta. |
| `components/parameter-field.tsx` | Campo de un parámetro entero con sus límites; lo usan el nodo y la carga. |
| `components/workload-inspector.tsx` | Panel Load: añadir, configurar o quitar la carga (objetivo, perfil, números y semilla) y lo que hizo en el último experimento, con el resumen de k6. |
| `components/issue-list.tsx`, `issue-note.tsx` | Panel de incidencias y una incidencia en una línea. Las de la carga abren el panel Load. |
| `components/scenario-details.tsx` | Nombre y descripción del escenario en el panel lateral. |
| `components/run-button.tsx`, `execution-badge.tsx`, `status-icon.tsx` | Botón Run/Stop/Retry cleanup, estado del experimento en la barra e icono de cada estado. |
| `components/component-icon.tsx` | Icono de cada tipo de componente. |

## Fuera de las aplicaciones

| Ruta | Qué es |
| --- | --- |
| `backend/src/main/resources/db/migration/` | Migraciones de Flyway, de `V1` en adelante. Nunca se editan una vez en `main`. |
| `demo-app/` | La imagen `infracture/demo-app`, el HTTP Service que corre dentro del experimento. |
| `load-generator/` | La imagen `infracture/load-generator`: k6 con un script fijo por perfil de carga y `run.sh`, que elige el script. |
| `docker/Dockerfile`, `docker/docker-proxy/haproxy.cfg` | Imagen de la aplicación y lista blanca del proxy de Docker. |
| `compose.yaml` | Postgres de desarrollo y, con `--profile app`, el proxy y el backend en contenedor. |
| `scripts/` | Arranque de desarrollo y los entornos desechables de las pruebas de integración y de navegador. |
| `docs/models/execution-lifecycle/` | El modelo TLA+ del ciclo de vida de una ejecución. |

## Explicaciones visuales

Páginas interactivas guardadas en [`docs/explainers/`](explainers/). Se abren directamente en el navegador, sin servidor:

| Página | Qué explica |
| --- | --- |
| [De Run a Stop](explainers/run-to-stop.html) | El recorrido completo de un experimento paso a paso, con lo que cambia en la base de datos y en Docker, en cuatro casos: todo bien, falla el arranque, un nodo cae y falla la limpieza. |
| [PlanCompiler paso a paso](explainers/plan-compiler.html) | Cómo una ejecución reservada se convierte en el plan de Compose, etapa a etapa, en JSON y en YAML. |
| [Proxy de Docker](explainers/docker-proxy.html) | Por qué el backend llega a Docker a través de HAProxy y qué llamadas deja pasar. |
