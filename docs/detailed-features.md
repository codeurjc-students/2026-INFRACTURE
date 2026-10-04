# Funcionalidades detalladas

Las funcionalidades se agrupan en tres niveles de prioridad. En cada tabla se indica el tipo de usuario al que va dirigida cada una. La funcionalidad avanzada se describe con menor detalle y podrá concretarse durante la investigación y el desarrollo.

## Funcionalidad básica

| Funcionalidad | Tipo de usuario | Descripción |
| --- | --- | --- |
| Consulta pública | Anónimo, registrado y administrador | Consultar la información pública, proyectos publicados, perfiles públicos. |
| Registro, acceso y perfil | Registrado y administrador | Crear una cuenta, iniciar sesión con correo y contraseña, cerrar sesión, editar el perfil y subir un avatar. |
| Gestión de proyectos | Registrado y administrador | Crear, consultar, editar y eliminar proyectos propios. Serán privados por defecto y podrán publicarse voluntariamente. |
| Escenarios y canvas | Registrado y administrador | Crear escenarios dentro de un proyecto y guardar su definición antes de ejecutar. |
| Catálogo de componentes | Registrado y administrador | Utilizar exclusivamente seis plantillas controladas: HTTP Service, Worker, Load Generator, PostgreSQL, Redis y RabbitMQ. |
| Conexiones y validación | Registrado y administrador | Conectar componentes compatibles y detectar configuraciones incompletas o inválidas. |
| Ejecución local | Registrado y administrador | Ejecutar un escenario validado sobre Docker en una red aislada y detenerlo con limpieza de recursos. Solo habrá una ejecución activa en la instancia local. |
| Observación e historial | Registrado y administrador | Consultar estados, logs resumidos, eventos, métricas y el historial de las ejecuciones propias. |
| Administración | Administrador | Gestionar usuarios y plantillas del catálogo. |

## Funcionalidad intermedia

| Funcionalidad | Tipo de usuario | Descripción |
| --- | --- | --- |
| Parada y reinicio | Registrado y administrador | Detener un componente de una ejecución y volver a iniciarlo conservando el contexto. |
| Pausa y reanudación | Registrado y administrador | Pausar temporalmente un componente compatible y reanudarlo sin crear otra ejecución. |
| Latencia controlada | Registrado y administrador | Introducir y retirar latencia en una conexión compatible y registrar la acción. |
| Análisis de ejecución | Registrado y administrador | Comparar el estado esperado y el observado, consultar eventos y representar métricas con gráficos. |
| Comunidad de proyectos | Registrado y administrador | Seguir perfiles, marcar proyectos públicos con estrellas y clonar un proyecto público como copia privada. |
| Gestión de imágenes | Registrado y administrador | Subir avatares, portadas e iconos de las plantillas mediante almacenamiento local. |

## Funcionalidad avanzada

La consulta, creación, edición, publicación, retirada y realización de laboratorios pertenecen íntegramente a este nivel. Quedan fuera del alcance básico de Fase 3, según la decisión del alumno del 2 de octubre de 2026.

| Funcionalidad | Tipo de usuario | Descripción |
| --- | --- | --- |
| Laboratorios guiados | Anónimo, registrado y administrador | Consultar, realizar y administrar retos sobre escenarios preparados. Se proponen **Single Point of Failure**, **Cache Failure** y **Worker Recovery**. |
| Gamificación educativa | Registrado y administrador | Registrar objetivos, intentos y una puntuación sencilla. No se incluirá un ranking global. |
| Conceptos explorados | Registrado y administrador | Mostrar, al finalizar cada laboratorio, los conceptos asociados y reunir en el perfil, sin duplicados, los correspondientes a los intentos completados. |
| Profesor de IA (Lab Mentor) | Registrado y administrador | Explicar resultados, logs, dependencias y conceptos de la ejecución mediante una API externa consultiva. La IA no controlará Docker. |
| Control avanzado de recursos | Registrado y administrador | Estudiar límites dinámicos de CPU y memoria y otras mejoras de experimentación si el tiempo y la investigación lo permiten. |
