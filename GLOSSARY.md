# Infracture Local

Glosario de los conceptos propios que permiten diseñar, ejecutar y analizar experimentos locales de sistemas distribuidos.

## Diseño del experimento

**Scenario**:
Grafo editable que reúne los componentes, conexiones y la carga de un experimento. Controla que todas sus referencias internas sean coherentes.
_Avoid_: Canvas, topology cuando se habla de la entidad completa

**Scenario revision**:
Número que identifica el estado exacto de un Scenario después de guardarlo. Permite detectar ediciones concurrentes y ejecutar lo mismo que se validó.
_Avoid_: Version, schema version

**Component**:
Nodo editable colocado dentro de un Scenario a partir de una ComponentTemplate, con su perfil y sus parámetros.
_Avoid_: ExecutedComponent, container

**Connection**:
Dependencia dirigida y editable que sale de un puerto de un Component hacia otro Component del mismo Scenario.
_Avoid_: ExecutedConnection, arbitrary link

**Workload**:
Carga que el Scenario aplica a uno de sus Component: objetivo, perfil de carga, parámetros y semilla. No es un nodo del grafo.
_Avoid_: Load Generator, load node

**ValidationIssue**:
Problema que la validación encuentra en un Scenario, con severidad, motivo y elemento afectado. Un error bloquea Run; una advertencia no.
_Avoid_: Validation error cuando también incluye advertencias

## Ejecución e historial

**Execution**:
Experimento iniciado desde una revisión concreta de un Scenario y conservado como historial independiente.
_Avoid_: Scenario, container

**Execution snapshot**:
Copia inmutable de la revisión, la carga, las versiones de contrato, las imágenes y el plan con los que se inició una Execution.
_Avoid_: Current scenario

**ExecutionPlan**:
Traducción de un Execution snapshot a los servicios, la red, la salud y las dependencias que se crean en Docker.
_Avoid_: Docker configuration, compose file escrito por el usuario

**ExecutedComponent**:
Identidad histórica de un Component dentro de una Execution concreta.
_Avoid_: Component, Docker container

**ExecutedConnection**:
Identidad histórica de una Connection dentro de una Execution concreta.
_Avoid_: Connection, proxy

**FaultAction**:
Registro de una petición controlada para alterar un componente o una conexión ejecutados y de su resultado real.
_Avoid_: ExecutionEvent, failure

## Catálogo controlado

**ComponentType**:
Familia de comportamiento de un componente. El catálogo admite HTTP Service, Worker, PostgreSQL, Redis, RabbitMQ y Load Balancer.
_Avoid_: ComponentTemplate, Component

**ComponentTemplate**:
Ficha reutilizable del catálogo que pertenece a un ComponentType y reúne un nombre, un icono y la configuración inicial que copian sus nodos nuevos. El administrador puede crear varias fichas del mismo tipo.
_Avoid_: ComponentType, Component, Docker image

**Template contract**:
Definición que el backend conoce para un ComponentType: sus perfiles, parámetros, puertos y capacidades. Solo existe su versión actual.
_Avoid_: Free-form JSON, Docker configuration

**Profile**:
Forma concreta de comportarse que un Template contract ofrece a sus Component, como `CACHE_ASIDE`.
_Avoid_: Mode, variant

**Port**:
Salida con nombre de un Profile que pide una Capability y admite un número acotado de Connection.
_Avoid_: Connector, connection type

**Capability**:
Lo que un Profile ofrece a quien se conecta a él o le aplica carga, como `KEY_VALUE` o `RESOURCE_READ`.
_Avoid_: Type, protocol

## Aprendizaje guiado

**GuidedLab**:
Identidad editorial de un laboratorio que puede publicarse en varias revisiones.
_Avoid_: GuidedLabRevision, LabAttempt

**GuidedLabRevision**:
Edición inmutable de un GuidedLab con el escenario inicial, objetivos, reglas, pistas, puntuación y conceptos que se evaluarán.
_Avoid_: Current lab, Scenario

**LabAttempt**:
Realización de una GuidedLabRevision concreta por un usuario, con su progreso y una única ejecución como evidencia al completarse.
_Avoid_: Execution, GuidedLab
