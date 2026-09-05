# Infracture Local

Glosario de los conceptos propios que permiten diseñar, ejecutar y analizar experimentos locales de sistemas distribuidos.

## Diseño del experimento

**Scenario**:
Grafo editable que reúne los componentes y conexiones de un experimento. Controla que todas sus referencias internas sean coherentes.
_Avoid_: Canvas, topology cuando se habla de la entidad completa

**Scenario revision**:
Número que identifica el estado exacto de un Scenario después de guardarlo. Permite detectar ediciones concurrentes y ejecutar lo mismo que se validó.
_Avoid_: Version, schema version

**Component**:
Nodo editable colocado dentro de un Scenario a partir de una ComponentTemplate y ligado a una versión concreta de su contrato.
_Avoid_: ExecutedComponent, container

**Connection**:
Dependencia dirigida y editable entre dos Component del mismo Scenario.
_Avoid_: ExecutedConnection, arbitrary link

## Ejecución e historial

**Execution**:
Experimento iniciado desde una revisión concreta de un Scenario y conservado como historial independiente.
_Avoid_: Scenario, container

**Execution snapshot**:
Copia inmutable de la revisión y configuración exactas con las que se inició una Execution.
_Avoid_: Current scenario

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

**Template contract**:
Definición versionada que establece la configuración, capacidades, dependencias y conectores permitidos para una ComponentTemplate.
_Avoid_: Free-form JSON, Docker configuration

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
