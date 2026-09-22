# Registro de uso de herramientas de IA

Este documento registra el uso de herramientas de inteligencia artificial durante la concepción y el desarrollo de Infracture. Las interacciones relacionadas se agrupan por tema y finalidad para conservar la trazabilidad sin convertir el documento en una transcripción de cada mensaje.

El periodo cubierto actualmente comprende desde el **27 de julio de 2026** hasta el **20 de septiembre de 2026**. El orden de las entradas es principalmente temático; cuando un mismo tema se trabajó en varias sesiones, se indica un intervalo de fechas.

El contenido generado por IA se ha utilizado como apoyo para investigar, comparar alternativas, estructurar decisiones y redactar documentación. El alumno es responsable de revisar, comprender, corregir y validar todas las propuestas antes de incorporarlas al proyecto.

## Herramienta y configuración general

- **Herramienta:** OpenAI Codex, aplicación de escritorio.
- **Versión de la aplicación declarada al iniciar el registro:** 26.731.11130.
- **Configuración principal:** modo agéntico con GPT-5.6 Sol y nivel de razonamiento `high`.
- **Configuración secundaria:** GPT-5.6 Luna, nivel de razonamiento `max` y modo `fast`.
- **Criterio de uso:** GPT-5.6 Sol se utilizó principalmente para investigación, análisis, arquitectura, evaluación de alternativas y toma de decisiones. GPT-5.6 Luna se utilizó en algunas tareas centradas en redacción, reorganización y edición extensa de documentación.
- **Herramientas auxiliares:** búsqueda web, lectura y extracción de PDF, generación de imágenes y diagramas, edición de ficheros locales y comprobaciones sobre la documentación generada.

Cuando una entrada no indique una excepción, se entiende que se utilizó la configuración principal. La configuración secundaria se identifica expresamente en las entradas en las que intervino.

## Criterio de registro

Cada entrada temática incluye:

- Fecha o periodo de trabajo.
- Fase del proyecto.
- Objetivo y contexto proporcionado a la IA.
- Forma de uso y herramientas auxiliares.
- Decisiones o resultados obtenidos.
- Revisión y decisiones adoptadas por el alumno.

Este documento se mantiene como un registro vivo. Debe revisarse durante el trabajo y actualizarse en cuanto exista una entrada relevante: una nueva finalidad de uso de IA, una decisión o resultado material, un cambio de modelo o configuración, una herramienta auxiliar significativa o una intervención que deba conservarse para la trazabilidad académica. Las correcciones menores de una misma finalidad pueden agruparse en su entrada temática; no es necesario registrar cada mensaje por separado.

Las fechas iniciales se han comprobado con las marcas temporales del historial de la tarea: ideación y primeras propuestas el 27 de julio, aclaración de la ejecución local y en la nube el 28 de julio, fichas formales y trabajo de marca el 31 de julio, y análisis de la guía v4 y documentación de la Fase 1 el 1 de agosto.

## AI-2026-07-27-001 - Investigación de mercado e ideación del producto

- **Fecha o periodo:** 27 de julio de 2026.
- **Fase:** exploración inicial y selección de la temática.
- **Objetivo:** encontrar una temática de TFG tecnológicamente atractiva y adecuada para una aplicación web con React y Spring Boot, que incorporase inteligencia artificial e infraestructura interesante y tuviese capacidad para resolver un problema real.
- **Contexto aportado por el alumno:** interés general por la tecnología, los sistemas distribuidos, la infraestructura y las aplicaciones visuales e interactivas; preferencia por una experiencia de producto completa frente a un panel meramente informativo; exclusión de temáticas jurídicas y ecológicas; monetización deseable, pero subordinada al interés técnico y formativo.
- **Modelo y configuración predominante:** GPT-5.6 Sol, razonamiento `high`, modo agéntico.
- **Forma de uso:** investigación web sobre tendencias, productos existentes, necesidades de usuarios y posibles oportunidades B2B y B2C; generación y comparación iterativa de conceptos de producto.
- **Alternativas estudiadas:** Scam Cycle, TestGuild, Creator Flow, AgeLab, BuildQuest, HomeLab Canvas y otras propuestas relacionadas con tecnología, aprendizaje, colaboración y experimentación.
- **Resultado:** se identificaron como opciones especialmente atractivas TestGuild, BuildQuest y HomeLab Canvas. HomeLab Canvas pasó a ser la primera opción porque combinaba aprendizaje práctico, sistemas distribuidos, visualización, Docker, observabilidad, fallos controlados e infraestructura.
- **Revisión del alumno:** el alumno descartó Creator Flow, mantuvo algunas alternativas como reserva y seleccionó HomeLab Canvas como propuesta prioritaria, pendiente inicialmente de validar su viabilidad con el tutor.

## AI-2026-07-27-002 - Evolución del concepto, experiencia visual y marca Infracture

- **Fecha o periodo:** del 27 al 31 de julio de 2026. Las propuestas visuales y la selección de HomeLab Canvas comenzaron el 27 de julio; el proceso de selección del nombre y la elección de Infracture se desarrollaron el 31 de julio.
- **Fase:** conceptualización del producto.
- **Objetivo:** transformar la idea inicial de HomeLab Canvas en una aplicación coherente, visual y reconocible, y encontrar una identidad de marca adecuada.
- **Contexto aportado por el alumno:** deseo de construir una aplicación usable y atractiva, con múltiples interacciones y sensación de producto real; interés en aprender arquitectura distribuida mediante experimentos prácticos; preferencia por una marca tecnológica con personalidad propia.
- **Modelos y configuración:** GPT-5.6 Sol con razonamiento `high` para el concepto y la arquitectura; GPT-5.6 Luna con razonamiento `max` y modo `fast` en parte de la redacción extensa y preparación documental.
- **Forma de uso:** refinamiento iterativo del funcionamiento, creación conceptual de pantallas, descripción de flujos, elaboración de arquitecturas visuales y exploración de nombres y lemas.
- **Resultado funcional:** plataforma para diseñar arquitecturas mediante componentes controlados, ejecutarlas, introducir fallos y estudiar estados, logs, métricas y recuperación.
- **Resultado visual:** propuesta de una interfaz centrada en un lienzo de arquitectura, controles de ejecución, observabilidad y experiencias guiadas, evitando limitar el producto a un panel convencional.
- **Resultado de marca:** después de explorar nombres como HomeLab Canvas, Stackfall, Resilio y otras variantes, se seleccionó **Infracture** como nombre del producto.
- **Lema de referencia:** `Build it. Break it. Understand it.`
- **Material generado:** dossier conceptual en PDF, diagramas de arquitectura y propuestas visuales de alto nivel utilizadas como apoyo para presentar la idea al tutor.
- **Revisión del alumno:** el alumno seleccionó personalmente el nombre Infracture y confirmó que la identidad debía transmitir construcción, fallo, análisis e infraestructura.

## AI-2026-07-28-003 - Análisis académico y separación de los dos TFG

- **Fecha o periodo:** del 28 de julio al 1 de agosto de 2026. La explicación de la ejecución local y la primera separación se trabajaron el 28 de julio; las dos fichas formales se prepararon el 31 de julio; la guía v4 y las decisiones posteriores a la reunión se analizaron el 1 de agosto.
- **Fase:** Fase 1 - Definición de funcionalidades y alcance académico.
- **Objetivo:** adaptar la idea a los requisitos académicos y separar con claridad el trabajo correspondiente a cada uno de los dos TFG del doble grado.
- **Ficheros de contexto:** `Desarrollo de una aplicación web como TFG v3.pdf`, `Desarrollo de una aplicación web como TFG v4.pdf`, dossier conceptual y decisiones transmitidas por el alumno después de las reuniones con el tutor.
- **Modelo y configuración predominante:** GPT-5.6 Sol, razonamiento `high`, modo agéntico.
- **Forma de uso:** lectura de las guías, comparación de versiones, identificación de requisitos obligatorios y optativos, análisis de alternativas de separación y elaboración de arquitecturas para ejecución local y evolución en la nube.
- **Herramientas auxiliares:** extracción y revisión local de PDF, diagramas Mermaid y edición documental.
- **Resultado sobre la guía:** identificación de requisitos relacionados con React, Spring Boot, base de datos, imágenes, pruebas, cobertura, CI/CD, migraciones, despliegue y registro del uso de IA.
- **Separación acordada:**
  - **TFG1 - Infracture Local:** desarrollo íntegro de la aplicación y ejecución de los experimentos sobre Docker en un único equipo.
  - **TFG2 - Infracture Cloud:** evolución y despliegue en AWS, cuya arquitectura y selección concreta de servicios se definirán en el segundo TFG.
- **Decisiones asociadas:** Infracture Local deberá disponer de su propia integración continua y entrega continua de versiones empaquetadas; el segundo TFG no puede utilizarse para completar funcionalidades pendientes de la aplicación local.
- **Alternativas estudiadas para el segundo TFG:** servicios nativos de AWS, Kubernetes, exportación declarativa y CloudFormation. Estas opciones se conservaron como material de análisis, no como alcance del README de Infracture Local.
- **Revisión del alumno y del tutor:** el tutor dio el visto bueno a la propuesta general, indicó que el primer TFG debía ejecutarse completamente en local y planteó AWS para el segundo. El alumno confirmó esta división.

## AI-2026-07-28-004 - Arquitectura, tecnologías, contratos y riesgos técnicos

- **Fecha o periodo:** del 28 de julio al 3 de agosto de 2026. La arquitectura de ejecución Docker local comenzó a detallarse el 28 de julio y se consolidó con la selección del adaptador de Docker, el mecanismo de latencia y los contratos iniciales del catálogo.
- **Fase:** Fase 1 - Análisis inicial.
- **Objetivo:** diseñar una arquitectura realizable para Infracture Local y detectar los elementos que necesitan validación temprana.
- **Modelo y configuración predominante:** GPT-5.6 Sol, razonamiento `high`, modo agéntico.
- **Forma de uso:** análisis arquitectónico, comparación de tecnologías, diseño de responsabilidades y elaboración de diagramas de componentes y flujos.
- **Arquitectura propuesta:** SPA en React, API REST en Spring Boot, arquitectura de monolito modular, persistencia en MySQL, motor de ejecución local desacoplado, Docker Engine, redes aisladas por ejecución y Docker Compose para levantar la plataforma.
- **Almacenamiento de archivos:** MinIO como almacenamiento local compatible con la API S3 para avatares, portadas e iconos, accesible mediante una abstracción propia.
- **Tiempo real:** Server-Sent Events para comunicar estados, eventos y logs desde el backend al navegador.
- **Observabilidad:** estados, logs, métricas básicas, gráficos temporales, historial persistente y algoritmo de análisis de impacto sobre el grafo del escenario.
- **Seguridad:** Spring Security, autenticación con correo y contraseña, tokens JWT, autorización por roles y propiedad, catálogo controlado, límites de recursos y limpieza de contenedores y redes. Los proveedores OAuth externos se estudiaron inicialmente y fueron descartados para reducir el alcance.
- **Calidad:** pruebas unitarias, pruebas de integración entre el backend y la base de datos con Testcontainers, pruebas de integración entre el frontend y la API, pruebas de sistema y cobertura mínima exigida del 70 %.
- **Automatización:** GitHub Actions para integración continua, análisis estático, construcción y entrega continua de imágenes Docker y paquetes versionados.
- **Decisiones técnicas cerradas:** Flyway para las migraciones; Playwright para las pruebas E2E; `docker-java` detrás de una interfaz propia para acceder a Docker Engine; Toxiproxy para la inyección de latencia; y Tailwind CSS, shadcn/ui sobre Radix Primitives, React Flow, Motion for React, Lucide React y variables CSS como conjunto tecnológico de interfaz y estilo.
- **Selecciones aplazadas:** librería de gráficos, con Recharts como candidato; herramienta de análisis estático, con SonarQube y SonarQube Cloud como candidatos; y API, proveedor y modelo del profesor de IA.
- **Contratos del catálogo:** definición del comportamiento, configuración segura, conexiones compatibles, comprobaciones de salud y evidencias observables de HTTP Service, Worker, Load Generator, MySQL, Redis y RabbitMQ. Se estableció que el backend generará la configuración de red, credenciales, puertos y nombres técnicos sin aceptar comandos ni imágenes arbitrarias de los usuarios.
- **Perfiles y capacidades:** separación entre perfiles de comportamiento del HTTP Service, perfiles del Worker y perfiles de carga. Los perfiles declaran requisitos y capacidades para que el backend pueda validar sus combinaciones antes de crear recursos.
- **Flujo de ejecución:** diferenciación entre el plano de control y el plano de datos; definición del ciclo de validación, compilación, arranque, salud, carga, fallos, observabilidad, parada y limpieza; y separación de `ScenarioValidator` y `DependencyImpactAnalyzer` como responsabilidades distintas sobre el mismo grafo.
- **Generación de carga:** definición de operaciones repetidas, selección ponderada, flujos con estado, fases de carga y semilla reproducible. El Load Generator solo conoce la API pública del HTTP Service y no sus dependencias internas.
- **Documento resultante:** creación de [`docs/EXECUTION_ARCHITECTURE.md`](docs/EXECUTION_ARCHITECTURE.md) como anexo técnico para presentar al tutor y ampliación resumida del apartado de contratos de [`README.md`](README.md).
- **Riesgos principales:** traducción del grafo a recursos ejecutables, aislamiento y limpieza segura, latencia controlada, transmisión continua de información, consumo de recursos y aprendizaje simultáneo de las tecnologías.
- **Medida propuesta:** construir al comienzo un prototipo vertical que cree una red, levante componentes, recopile logs, aplique fallos, introduzca latencia y elimine todos los recursos.
- **Revisión del alumno:** el alumno concedió prioridad a MinIO, los seis componentes, la pausa, la reanudación y la latencia. Confirmó MinIO local, aceptó Flyway por recomendación técnica, delegó la elección entre Playwright y Selenium y aprobó el conjunto visual basado en Tailwind CSS, shadcn/ui y Motion. Posteriormente, confirmó `docker-java` y Toxiproxy, manteniendo únicamente la librería de gráficos, la herramienta de análisis estático y la integración concreta de IA como selecciones aplazadas.

## AI-2026-08-01-005 - Definición funcional y social de Infracture Local

- **Fecha:** 1 de agosto de 2026.
- **Fase:** Fase 1 - Definición de funcionalidades.
- **Objetivo:** concretar los usuarios, permisos, recursos, catálogo, laboratorios, inteligencia artificial y funciones sociales del primer TFG.
- **Modelo y configuración predominante:** GPT-5.6 Sol, razonamiento `high`, modo agéntico.
- **Forma de uso:** rondas de preguntas de alcance acompañadas de recomendaciones, análisis de consecuencias y validación posterior por parte del alumno.
- **Público objetivo:** estudiantes y personas en formación, junto con desarrolladores con experiencia que quieran experimentar; prioridad educativa sin reducir el producto a una aplicación exclusivamente docente.
- **Usuarios y acceso:** usuarios anónimos, registrados y administradores; registro e inicio de sesión exclusivamente mediante correo y contraseña. Se descartó OAuth con GitHub y Google, así como cualquier vinculación de cuentas externas o integración funcional con repositorios.
- **Proyectos:** privados por defecto y publicables voluntariamente; consulta pública; clonación privada de proyectos públicos con referencia al autor original.
- **Capa social:** perfiles públicos, seguimiento entre usuarios y estrellas en proyectos. Las estrellas funcionan como marcador personal visible y señal de interés pública, no como puntuación o valoración numérica. Se seleccionaron las entidades asociativas explícitas `UserFollow` y `ProjectStar` para expresar restricciones, conservar la fecha de creación y permitir futuras ampliaciones de metadatos.
- **Catálogo controlado obligatorio:** HTTP Service, Worker, Load Generator, MySQL, Redis y RabbitMQ. Se descartaron imágenes y comandos arbitrarios aportados por usuarios.
- **Fallos controlados incluidos:** parada, reinicio, pausa, reanudación e introducción y retirada de latencia. La modificación dinámica de CPU o memoria se mantiene como funcionalidad avanzada recortable.
- **Concurrencia:** una única ejecución activa por instancia de Infracture Local, adecuada para limitar el consumo del equipo y mantener el foco en un laboratorio activo.
- **Laboratorios iniciales:** Single Point of Failure, Cache Failure y Worker Recovery. El alcance imprescindible incluye un laboratorio completamente funcional; completar los tres se considera una ampliación recortable.
- **Gamificación:** objetivos, puntos, pistas con reducción de bonificación y registro de intentos. El ranking global queda expresamente fuera de alcance.
- **Profesor de IA:** consumo de una API externa mediante una abstracción independiente del proveedor; explicación de resultados, logs y conceptos; prohibición de controlar Docker o ejecutar acciones autónomas.
- **Idiomas:** interfaz en inglés; documentación académica en español; código, commits y nombres técnicos en inglés.
- **Revisión del alumno:** todas estas decisiones fueron respondidas o confirmadas por el alumno. Los detalles internos de los componentes y laboratorios se refinarán mediante prototipos y durante el análisis técnico.

## AI-2026-08-01-006 - Priorización, calendario y evaluación de viabilidad

- **Fecha:** 1 de agosto de 2026.
- **Fase:** Fase 1 - Planificación.
- **Objetivo:** proteger la finalización de Infracture Local en enero de 2027 y distinguir el producto obligatorio de las ampliaciones recortables.
- **Contexto aportado por el alumno:** trabajo por las mañanas, dos asignaturas adicionales y posibilidad de dedicar tiempo al proyecto todos los días como rutina.
- **Modelo y configuración predominante:** GPT-5.6 Sol, razonamiento `high`, modo agéntico.
- **Forma de uso:** estimación cualitativa del esfuerzo, identificación de dependencias, separación entre alcance imprescindible y ampliaciones recortables, definición de exclusiones y propuesta de hitos mensuales.
- **Alcance imprescindible protegido:** aplicación React y Spring Boot, autenticación, proyectos, catálogo de seis componentes, motor Docker, una ejecución activa, SSE, métricas, fallos de parada, reinicio, pausa, reanudación y latencia, historial, algoritmo de impacto, un laboratorio, profesor de IA, MinIO, perfiles, seguimientos, estrellas, administración, pruebas, cobertura, CI/CD y documentación.
- **Ampliaciones recortables:** límites dinámicos de CPU o memoria, comparación avanzada, exportación e importación, clonación, tres laboratorios en lugar de uno, puntuación detallada y capacidades adicionales del profesor de IA.
- **Fuera de alcance confirmado:** ranking global, chat, comentarios, equipos, feed social, varias ejecuciones simultáneas, imágenes arbitrarias, integración con repositorios y control autónomo por parte de la IA.
- **Orden de recorte:** simplificar puntuación; reducir laboratorios; eliminar clonación; eliminar importación y exportación; limitar la IA; eliminar comparación avanzada; posponer límites dinámicos de recursos.
- **Calendario:** objetivo interno de cierre funcional el 15 de enero de 2027 y fecha provisional máxima el 31 de enero de 2027; enero debe reservarse principalmente para estabilización, correcciones y documentación.
- **Estimación de dedicación:** una rutina aproximada de 14 a 18 horas semanales se consideró compatible con el calendario, siempre que se congele el alcance y se validen pronto los riesgos técnicos.
- **Conclusión obtenida:** proyecto viable como aplicación académica completa, pero ambicioso y sin margen para ampliaciones continuas. La ausencia de una ejecución vertical funcional al terminar septiembre actuaría como señal de revisión inmediata del alcance.
- **Revisión del alumno:** el alumno confirmó el alcance imprescindible y señaló que el tutor realizará su propia valoración de viabilidad durante la revisión de la fase.

## AI-2026-08-01-007 - Creación y consolidación de la documentación de Fase 1

- **Fecha o periodo:** del 1 al 3 de agosto de 2026.
- **Fase:** Fase 1 - Documentación y preparación del seguimiento.
- **Objetivo:** convertir las decisiones acumuladas en documentación mantenible para su revisión académica y preparar su incorporación al repositorio oficial.
- **Modelos y configuración:** GPT-5.6 Sol con razonamiento `high` para comprobar coherencia y alcance; GPT-5.6 Luna con razonamiento `max` y modo `fast` en tareas de redacción, reorganización y compactación documental.
- **Forma de uso:** generación, edición y revisión cruzada de documentos Markdown; comprobación de consistencia entre alcance, prioridades, calendario y decisiones descartadas; y auditoría final de ortografía, puntuación, concordancia y terminología.
- **Ficheros creados o mantenidos:**
  - [`README.md`](README.md): definición funcional, análisis, arquitectura, alcance, planificación y requisitos del repositorio oficial.
  - [`docs/EXECUTION_ARCHITECTURE.md`](docs/EXECUTION_ARCHITECTURE.md): anexo técnico sobre contratos, conexiones, validación, generación de carga y ciclo de ejecución.
  - [`CHANGELOG.md`](CHANGELOG.md): cambios relevantes de la propuesta.
  - [`AI_USAGE.md`](AI_USAGE.md): registro agrupado del uso de herramientas de IA.
- **Fuentes y referencias utilizadas:** guía oficial del TFG y documentación oficial de tecnologías y productos comparados durante el estado del arte, entre ellos Docker, Portainer, GNS3, Killercoda, Play with Docker, Chaos Mesh y LitmusChaos.
- **Decisiones editoriales:** documentación académica en español; README limitado exclusivamente a Infracture Local; tres capturas principales acompañadas de un mapa completo de navegación, inventario de pantallas y documentación del prototipo.
- **Estado al cierre de esta entrada:** el repositorio oficial `codeurjc-students/2026-INFRACTURE` ya había sido creado por el profesor y clonado localmente. Contenía todavía el commit inicial, un README mínimo y la licencia Apache 2.0; la propuesta documental debía completarse antes de recibir las observaciones del tutor.
- **Trabajo pendiente al cierre de esta entrada:** incorporar README, CHANGELOG, AI_USAGE y recursos visuales al repositorio oficial; realizar la revisión final del alumno; y, después de recibir las observaciones del tutor, crear el GitHub Project, transformar las funcionalidades aprobadas en *issues*, aplicar correcciones y completar la rúbrica oficial.
- **Revisión del alumno:** el alumno solicitó compactar el registro de IA por temas, aportó la versión actual de Codex y especificó las configuraciones de modelo empleadas. El resultado deberá revisarse antes del primer commit documental.

## AI-2026-08-02-008 - Creación del primer boceto visual y prototipo navegable

- **Fecha o periodo:** del 1 al 2 de agosto de 2026.
- **Fase:** Fase 1 - Definición inicial de pantallas, navegación e identidad visual.
- **Objetivo:** elaborar un primer boceto de alta fidelidad que permitiera visualizar Infracture Local como producto completo, comprobar la separación entre sus experiencias principales y documentar las pantallas necesarias antes de desarrollar el frontend definitivo.
- **Contexto aportado por el alumno:** preferencia por una interfaz oscura, mayoritariamente monocromática, visualmente cuidada y alejada de los paneles genéricos; interés por la composición editorial de productos como Cursor y por el uso moderado de profundidad, movimiento y superficies translúcidas; necesidad de que los lienzos representaran las arquitecturas de manera especialmente visual; separación entre el canvas libre, los retos guiados y la administración.
- **Modelo y configuración predominante:** GPT-5.6 Sol, razonamiento `high`, modo agéntico.
- **Forma de uso:** análisis y refinamiento de la dirección artística; definición de tipografía, paleta, jerarquías, componentes, estados y patrones de navegación; creación inicial del sistema visual en Figma y traslado posterior a un prototipo navegable en React y TypeScript. La IA propuso estructuras y código que fueron revisados de forma iterativa mediante capturas reales.
- **Herramientas auxiliares:** complemento oficial de Figma, edición de ficheros locales, React, TypeScript, Vite, React Router, React Flow, Motion for React, Radix Primitives, Lucide React, CSS y Playwright para la inspección y generación de capturas en escritorio y móvil.
- **Referencias de diseño:** primeras propuestas realizadas en Figma y principios visuales observados en las páginas de Cursor y Framer. Estas referencias se utilizaron para estudiar ritmo, tipografía, densidad y presentación, sin reproducir literalmente su identidad.
- **Dirección visual obtenida:** sistema denominado **Obsidian Editorial + Instrument UI**, con Mona Sans para titulares, Geist Sans para la interfaz, Geist Mono para la información técnica, superficies negras y de color grafito, y colores específicos para distinguir los tipos de componente. El color se reservó para comunicar infraestructura y estados relevantes, evitando asociar globalmente los colores cian o violeta con la inteligencia artificial.
- **Pantallas representadas:** Landing, Discover, Canvases, Free Canvas, Execution, Challenges, Challenge Workspace, autenticación y registro, perfil de usuario y una sección de administración aislada. También se representaron paneles laterales, modales y cambios de estado necesarios para explicar los flujos principales.
- **Interacciones simuladas:** navegación entre rutas, cambio entre acceso y registro, apertura del perfil, vistas previas, selección de nodos, ejecución de un escenario, introducción y recuperación de latencia, explicación del Lab Mentor, progreso de un reto, presentación de los conceptos explorados al completarlo y consulta posterior de estos conceptos en el perfil.
- **Iteraciones solicitadas por el alumno:** reducción del número de pantallas inicialmente propuesto; sustitución de una paleta excesivamente asociada a productos de IA; separación completa entre el canvas libre y los retos; simplificación del aprendizaje mediante conceptos administrables asociados a los laboratorios y visibles en el perfil; eliminación de avisos verdes, etiquetas con forma de píldora y elementos circulares considerados genéricos; conversión del panel de pasos del reto a negro y grafito; incorporación de las pantallas de autenticación, registro y perfil; eliminación posterior de OAuth con Google y GitHub y rediseño minimalista de la autenticación sin topologías decorativas.
- **Validación realizada:** comprobación de rutas, navegación y diseño adaptativo; ejecución de `typecheck` y compilación; capturas de Playwright en 1440 × 1024 y 390 × 844; revisión de la consola y corrección visual de las pantallas maestras.
- **Ficheros principales:** prototipo visual independiente conservado fuera del repositorio académico y selección de capturas incorporadas a `docs/images/` como material de diseño de la Fase 1.
- **Limitación explícita:** este material constituye un primer boceto visual para la Fase 1. Utiliza datos estáticos y todavía no implementa un backend, Docker, persistencia, autenticación JWT real, autorización, SSE, MinIO ni una API de inteligencia artificial. OAuth externo queda expresamente fuera del alcance actual.
- **Revisión del alumno:** el alumno seleccionó y corrigió activamente la dirección visual, rechazó elementos que consideró genéricos o propios de interfaces generadas por IA y confirmó la estructura final del primer mapa de navegación. El prototipo servirá para elegir las capturas representativas que se incorporarán a la documentación de Fase 1; no obliga a reproducir cada detalle durante la implementación definitiva.

## AI-2026-08-07-009 - Revisión del alumno y consolidación tecnológica final

- **Fecha:** 7 de agosto de 2026.
- **Fase:** Fase 1 - Revisión final de la documentación.
- **Objetivo:** incorporar al README revisado por el alumno las tecnologías ya acordadas, concretar el uso de Grafana k6 y comprobar que el changelog y el registro de IA reflejaran el estado final de la propuesta.
- **Contexto aportado por el alumno:** el alumno revisó personalmente el README, realizó los cambios que consideró necesarios y solicitó conservarlos como fuente de verdad antes de completar las últimas referencias tecnológicas.
- **Modelo y configuración:** configuración principal descrita al inicio del documento.
- **Forma de uso:** comparación cruzada del README revisado, el anexo técnico, el checklist, el changelog y las entradas anteriores de este registro; incorporación localizada de las tecnologías ausentes; simplificación del changelog para convertirlo en un registro de versiones en lugar de un historial de conversaciones; y comprobación final de coherencia.
- **Decisiones consolidadas:** Grafana k6 como motor controlado del Load Generator; Vite y React Router para la SPA; Spring Data JPA para la persistencia; JUnit, Spring Boot Test, Mockito, REST Assured y Testcontainers para las pruebas del backend; Vitest y React Testing Library para las pruebas del frontend; Playwright para las pruebas de sistema; y GitHub Actions para la integración y la entrega continuas.
- **Resultado:** objetivos técnicos actualizados sin superar el límite de diez; anexo de ejecución y checklist sincronizados; changelog reducido a la especificación consolidada de la aplicación; y CHANGELOG y AI_USAGE actualizados hasta la fecha de la revisión.
- **Revisión del alumno:** la revisión personal del README se considera completada. Las selecciones que permanecen expresamente abiertas son la librería de gráficos, la herramienta de análisis estático y la API, el proveedor y el modelo del profesor de IA.

## AI-2026-08-09-010 - Revisión técnica posterior a la tutoría y decisiones para la Fase 2

- **Fecha:** 9 de agosto de 2026.
- **Fase:** transición de la Fase 1 a la Fase 2.
- **Objetivo:** revisar en profundidad la arquitectura y las tecnologías documentadas antes de iniciar la configuración e implementación de la aplicación.
- **Contexto aportado por el alumno:** Fase 1 terminada y reunión con el tutor completada; apoyo del tutor al uso de PostgreSQL y aceptación de Playwright; interés en cerrar la librería de gráficos y el mecanismo de análisis estático antes de comenzar la Fase 2.
- **Modelo y configuración:** configuración principal descrita al inicio del documento.
- **Forma de uso:** revisión cruzada del README, el anexo de ejecución, el PDF de la guía del TFG, los documentos técnicos auxiliares y el prototipo visual; contraste con la documentación vigente de las tecnologías; y evaluación de las consecuencias sobre persistencia, canvas, pruebas y CI.
- **Decisiones adoptadas:** PostgreSQL sustituye a MySQL tanto en la persistencia permanente de la plataforma como en el componente relacional de los escenarios; Recharts se utilizará para las gráficas de métricas; SonarQube Cloud realizará el análisis estático desde GitHub Actions; Playwright se mantiene como herramienta E2E ratificada por el tutor; y Spring Security con JWT se conserva para autenticación y autorización.
- **Criterio de modelado:** el dominio seguirá siendo relacional y normalizado; `jsonb` se reservará para snapshots inmutables, configuraciones variables y documentos controlados. La instancia PostgreSQL de la plataforma y las instancias efímeras del canvas permanecerán aisladas aunque compartan tecnología.
- **Ficheros actualizados:** `README.md`, `CHANGELOG.md`, `docs/EXECUTION_ARCHITECTURE.md` y `docs/TECHNOLOGY_DECISIONS_PHASE_2.md`, junto con los documentos auxiliares que conservan la trazabilidad de la Fase 1.
- **Decisión aplazada:** el almacenamiento de imágenes no se modifica hasta recibir la respuesta del tutor sobre MinIO y la alternativa de almacenarlas en PostgreSQL.
- **Revisión del alumno:** el alumno confirmó PostgreSQL para la plataforma y para los componentes del canvas, Recharts, SonarQube Cloud, Playwright y el mantenimiento de JWT, y pidió excluir por ahora cualquier cambio en el almacenamiento de imágenes.

## AI-2026-08-13-011 - Preparación operativa de la Fase 2

- **Fecha y fase:** 13-19 de agosto de 2026; inicio de la Fase 2.
- **Objetivo y uso:** convertir la guía académica, la rúbrica y la arquitectura aprobada en un backlog ejecutable, revisando el repositorio y documentación oficial para definir dependencias, criterios de aceptación y evidencias.
- **Resultado:** estructuración de los issues y sub-issues de Fase 2, configuración guiada de las vistas, campos y automatizaciones del GitHub Project, planificación de la vertical mínima, fijación de Java 21 LTS y Node.js 24 LTS mediante `.java-version` y `.nvmrc`, y creación de plantillas de issues y pull requests para conservar la relación con el parent issue, los criterios de aceptación y las evidencias. Las notas auxiliares se conservan en `.local-docs/`, excluida del control de versiones, y no forman parte de la documentación oficial.
- **Límites y revisión:** el alumno revisó las decisiones, creó y gestionó las ramas sin automatizar las operaciones Git, y ejecutó la configuración del Project. Al cerrar este bloque todavía no se había iniciado el código; SonarQube Cloud dependía del tutor y el almacenamiento de imágenes continuaba pendiente.

## AI-2026-09-01-012 - Criterio de mantenimiento continuo del registro de IA

- **Fecha:** 1 de septiembre de 2026.
- **Fase:** Fase 2 - Desarrollo y documentación continua.
- **Objetivo:** evitar que el registro de uso de IA quede desactualizado mientras avanza el proyecto.
- **Contexto aportado:** el alumno indicó que `AI_USAGE.md` debe actualizarse continuamente siempre que aparezca información relevante que deba conservarse.
- **Forma de uso:** revisión del estado del propio registro e incorporación de un criterio explícito de mantenimiento dentro del documento.
- **Resultado:** se establece que cada bloque de trabajo deberá valorar si genera una entrada necesaria y, en ese caso, actualizar `AI_USAGE.md` como parte de la misma tarea, agrupando las interacciones menores por finalidad.
- **Revisión del alumno:** el criterio procede de una instrucción directa del alumno. La recuperación posterior de esta entrada se ha contrastado con el historial de la sesión y con el estado del repositorio.

## AI-2026-09-01-013 - Creación del backend y persistencia con PostgreSQL y Flyway

- **Fecha o periodo:** 1 y 2 de septiembre de 2026.
- **Fase:** Fase 2 - Primera vertical del backend.
- **Objetivo:** crear la base ejecutable del backend de Infracture y conectarla a una base de datos PostgreSQL local reproducible.
- **Contexto aportado por el alumno:** Java 21 y Maven ya estaban instalados; el alumno no había utilizado antes PostgreSQL, DBeaver ni Flyway y pidió realizar el proceso de forma guiada, comprendiendo para qué servía cada herramienta antes de continuar.
- **Modelo y configuración:** configuración principal descrita al inicio del documento.
- **Forma de uso:** explicación interactiva de Spring Initializr, Maven Wrapper, dependencias de Spring Boot, perfiles de configuración, PostgreSQL, Docker Compose, DBeaver, JPA y migraciones Flyway. La IA propuso comandos, revisó las salidas aportadas por el alumno, ayudó a diagnosticar los errores de entorno y creó o ajustó ficheros concretos cuando el alumno lo solicitó expresamente.
- **Herramientas auxiliares:** Spring Initializr, Java 21, Maven y Maven Wrapper, `jenv`, Docker Desktop, Docker Compose, PostgreSQL 18.6 Alpine, DBeaver, Flyway, Spring Data JPA, Spring Boot Actuator, Bean Validation y Spring Boot DevTools.
- **Decisiones técnicas:** el backend se ubica en `backend/`; PostgreSQL se ejecuta como servicio de infraestructura en Docker Compose y no como parte del contenedor de la aplicación; Maven gestiona las librerías Java y Flyway, por lo que no es necesaria una instalación independiente de Flyway; Hibernate valida el esquema mediante `ddl-auto=validate` y Flyway conserva su evolución mediante migraciones versionadas.
- **Configuración reproducible:** se fijó Java 21 para el repositorio mediante `jenv` y `.java-version`; el servicio `platform-postgres` utiliza una imagen fijada, variables con valores locales por defecto, publicación exclusiva en `127.0.0.1`, volumen persistente y comprobación de salud. La aplicación admite sobrescribir por variables de entorno la URL y credenciales de conexión.
- **Ficheros principales:** `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/src/main/resources/application.properties`, `backend/src/main/resources/db/migration/V1__initialize_platform_database.sql`, `backend/src/test/java/es/codeurjc/infracture/InfractureBackendApplicationTests.java` y `compose.yaml`.
- **Resultado y verificación:** el alumno descargó y colocó el proyecto generado, revisó los `.gitignore`, configuró `jenv`, levantó PostgreSQL, comprobó su estado y accedió visualmente desde DBeaver. Maven compiló el backend, el contexto de Spring arrancó conectado a PostgreSQL y Flyway validó y aplicó la migración inicial.
- **Revisión del alumno:** el alumno ejecutó los comandos, compartió sus salidas, confirmó la conexión desde DBeaver y decidió aplazar un perfil específico de configuración al no ser necesario todavía. La rama y las operaciones Git permanecieron bajo su control explícito.

## AI-2026-09-02-014 - Implementación del catálogo de componentes y su API de lectura

- **Fecha:** 2 de septiembre de 2026.
- **Fase:** Fase 2 - Catálogo controlado de componentes.
- **Objetivo:** implementar conjuntamente los issues del modelo persistente `ComponentTemplate`, sus seis datos iniciales y el endpoint público de lectura del catálogo.
- **Contexto aportado por el alumno:** el alumno quiso desarrollar personalmente la estructura y las clases, solicitando explicaciones sobre la organización por funcionalidad, la clave estable del catálogo, los identificadores de base de datos, las migraciones acumulativas de Flyway, `GenerationType.IDENTITY`, repositorios, servicios, streams, DTO, MapStruct e inyección de dependencias.
- **Modelo y configuración:** configuración principal descrita al inicio del documento.
- **Forma de uso:** acompañamiento paso a paso y revisión del código creado por el alumno. La IA redactó la migración `V2`, añadió la configuración de MapStruct al `pom.xml`, realizó correcciones menores de formato y ejecutó las comprobaciones de compilación, arranque y API solicitadas. No se utilizaron las skills metodológicas instaladas porque el alumno pidió no invocarlas automáticamente.
- **Diseño implementado:** paquete funcional `catalog` con entidad JPA, enum de tipos, repositorio, servicio, DTO, mapper y controlador. La clave textual identifica cada plantilla de manera estable fuera de la base de datos; el `id` numérico `IDENTITY` queda como identidad interna. El servicio devuelve solo plantillas habilitadas y las ordena de forma estable según el enum.
- **Persistencia:** `V2__create_component_template_catalogue.sql` crea `component_template` con restricciones de clave, tipo y valores permitidos, e introduce de forma idempotente HTTP Service, Worker, Load Generator, PostgreSQL, Redis y RabbitMQ.
- **API:** `GET /api/v1/component-templates` devuelve únicamente `key`, `name` y `type` mediante `ComponentTemplateDTO`; MapStruct genera el mapper como componente de Spring y el controlador utiliza inyección por constructor.
- **Herramientas auxiliares:** Maven Wrapper, Flyway, PostgreSQL, Hibernate/JPA, MapStruct 1.6.3, Spring MVC, `curl`, `javap` y el servidor de lenguaje Java de VS Code.
- **Resultado y verificación:** `./mvnw clean verify` compiló las ocho clases del backend y superó la prueba de contexto; Flyway validó dos migraciones y dejó el esquema `public` en la versión 2; el arranque posterior respondió `HTTP 200` con los seis componentes esperados, en orden estable y sin exponer `id` ni `enabled`. El alumno repitió y confirmó manualmente la consulta del endpoint.
- **Alcance de las pruebas:** no se añadieron todavía pruebas específicas con Mockito o Testcontainers porque están previstas en issues posteriores; la verificación actual cubre compilación, contexto Spring, migraciones reales y comprobación manual del contrato HTTP.
- **Ficheros principales:** `backend/pom.xml`, `backend/src/main/java/es/codeurjc/infracture/catalog/` y `backend/src/main/resources/db/migration/V2__create_component_template_catalogue.sql`.
- **Revisión del alumno:** el alumno decidió agrupar los dos issues en una misma rama, eligió MapStruct y el nombre visual `ComponentTemplateDTO`, creó las clases siguiendo la guía, confirmó el resultado JSON y mantuvo el control de las decisiones y de todas las operaciones Git.

## AI-2026-09-03-015 - Documentación y exportación reproducible de OpenAPI

- **Fecha:** 3 de septiembre de 2026.
- **Fase:** Fase 2 - Contrato de la API mínima.
- **Objetivo:** completar el issue de OpenAPI documentando el endpoint del catálogo y generando especificaciones YAML y HTML versionables y reproducibles.
- **Contexto aportado por el alumno:** el alumno pidió terminar el último issue del bloque actual, correspondiente a OpenAPI. Se mantuvo el alcance mínimo de Fase 2 y el control explícito del alumno sobre las operaciones Git.
- **Modelo y configuración:** configuración principal descrita al inicio del documento.
- **Forma de uso:** revisión del código, del plan de Fase 2 y del issue P2-09 en GitHub, selección de las versiones compatibles ya disponibles para Spring Boot 4 y configuración de un perfil Maven que arranca la aplicación, descarga la especificación viva y genera la documentación HTML. Se añadieron inicialmente anotaciones descriptivas y una prueba MVC contractual; ambas se retiraron después por indicación del alumno al no formar parte del alcance solicitado. No se invocaron skills metodológicas instaladas.
- **Herramientas auxiliares:** Springdoc OpenAPI 3.0.0, Swagger UI, Springdoc OpenAPI Maven Plugin 1.5, OpenAPI Generator 7.17.0, Maven Wrapper y Java 21.
- **Resultado:** la API expone `/v3/api-docs`, `/v3/api-docs.yaml` y `/swagger-ui.html`; `GET /api/v1/component-templates` queda descrito con su respuesta `200`, su colección y el esquema público `ComponentTemplateDTO`, y declara explícitamente `application/json` como representación producida. El perfil `openapi` actualiza `docs/api/api-docs.yaml` y `docs/api/api-docs.html` mediante `./mvnw verify -Popenapi`. La compilación y el test general finalizaron correctamente. El alumno ejecutó la exportación final dos veces con PostgreSQL disponible y comprobó que tanto el YAML como el HTML conservaron hashes idénticos.
- **Ficheros principales:** `backend/pom.xml`, `backend/src/main/java/es/codeurjc/infracture/catalog/api/ComponentTemplateController.java`, `backend/src/main/resources/application.properties`, `backend/README.md` y `docs/api/`.
- **Revisión del alumno:** el alumno revisó el alcance del issue, pidió retirar las anotaciones y la prueba contractual que no consideró necesarias, confirmó la declaración explícita de JSON, ejecutó la verificación reproducible con PostgreSQL y autorizó la publicación mediante pull request y el cierre del issue.

## AI-2026-09-03-016 - Estabilización de la compilación Java en VS Code

- **Fecha:** 3 de septiembre de 2026.
- **Fase:** Fase 2 - Entorno de desarrollo reproducible.
- **Objetivo:** impedir que la compilación automática del workspace sobrescriba las clases generadas por Maven y provoque fallos intermitentes al arrancar el backend.
- **Contexto aportado por el alumno:** durante la exportación de OpenAPI, el alumno identificó que el error de `target/` estaba relacionado con el workspace y pidió corregir la rotura recurrente.
- **Modelo y configuración:** configuración principal descrita al inicio del documento.
- **Forma de uso:** aplicación de un bucle de diagnóstico que ejecutó `mvn clean compile`, inspeccionó el bytecode generado con `javap` y detectó si aparecía `Unresolved compilation problems`. Se revisaron los procesos y metadatos del Java Language Server y se contrastó la opción de configuración con la documentación oficial de la extensión. Se utilizó la skill `diagnosing-bugs` como guía metodológica.
- **Herramientas auxiliares:** Maven Wrapper, `javap`, `ps`, Eclipse JDT Language Server y la extensión Language Support for Java by Red Hat.
- **Resultado:** se confirmó que JDT Auto Build recompilaba `target/generated-sources/annotations` después de Maven y reemplazaba `ComponentTemplateMapperImpl.class` con bytecode inválido. Se añadió localmente `.vscode/settings.json` con `java.autobuild.enabled=false`, dejando Maven como compilador del proyecto. El bucle mínimo permaneció verde y `./mvnw clean verify -Popenapi` completó correctamente con PostgreSQL, conservando los hashes reproducibles de YAML y HTML. La configuración de VS Code quedó excluida del repositorio para no imponer una preferencia local al resto del equipo.
- **Ficheros principales:** `.gitignore` y `.vscode/settings.json` (configuración local ignorada).
- **Revisión del alumno:** el alumno relacionó el fallo con el workspace, autorizó su corrección y decidió excluir `.vscode/` del control de versiones antes de publicar.

## AI-2026-09-03-017 - Organización interna del módulo de catálogo

- **Fecha:** 3 de septiembre de 2026.
- **Fase:** Fase 2 - Estructura del backend.
- **Objetivo:** preparar el módulo `catalog` para crecer sin mezclar su interfaz HTTP, casos de uso, dominio y persistencia.
- **Forma de uso:** análisis de la estructura existente con la skill `codebase-design` y aplicación de una reorganización exclusivamente estructural, conservando el comportamiento y los cambios locales de OpenAPI que ya estaban en curso.
- **Resultado:** las clases del catálogo se distribuyeron en los paquetes internos `api`, `application`, `domain` y `persistence`. Se actualizaron las declaraciones de paquete e imports sin introducir nuevas abstracciones ni modificar el contrato `GET /api/v1/component-templates`.
- **Ficheros principales:** `backend/src/main/java/es/codeurjc/infracture/catalog/`.
- **Revisión del alumno:** el alumno planteó y autorizó expresamente la reorganización y posteriormente autorizó su publicación junto con el trabajo local previo.

## AI-2026-09-03-018 - Guía de contribución para personas y agentes

- **Fecha:** 3 de septiembre de 2026.
- **Fase:** Fase 2 - Documentación del flujo de contribución.
- **Objetivo:** crear una guía breve y específica del repositorio que permita realizar cambios coherentes y verificables.
- **Contexto aportado por el alumno:** se solicitó un `AGENTS.md` de 200 a 400 palabras con la estructura del proyecto, comandos de desarrollo, estilo, pruebas y normas de commits y pull requests.
- **Modelo y configuración:** configuración principal descrita al inicio del documento.
- **Forma de uso:** inspección de la estructura versionada, Maven, Docker Compose, el código Java, las pruebas, las plantillas de GitHub y el historial de commits; redacción posterior mediante la skill `writing-for-agents`.
- **Resultado:** se añadió `AGENTS.md` con los comandos comprobables del backend, las convenciones observadas, los criterios para pruebas y pull requests, y las reglas ya acordadas de mantenimiento de este registro y control humano de las operaciones Git.
- **Revisión del alumno:** pendiente de revisión antes de incorporar los cambios al historial Git.

## AI-2026-09-05-019 - Revisión del modelo de clases y relaciones

- **Fecha:** 5 de septiembre de 2026.
- **Fase:** revisión de la base de arquitectura antes del desarrollo funcional.
- **Objetivo:** evaluar la definición de clases y relaciones del README, detectar carencias y proponer una arquitectura mantenible y desacoplada.
- **Contexto aportado:** el alumno solicitó una revisión de alto nivel y autorizó utilizar las skills pertinentes.
- **Modelo y configuración:** revisión inicial con un agente basado en GPT-6 y continuación documental con GPT-5; no se atribuye esta sesión a la configuración histórica de GPT-5.6 Sol.
- **Forma de uso:** aplicación de las skills `domain-modeling` y `codebase-design`, junto con las pautas Mermaid de `figma-generate-diagram`; lectura del README oficial del repositorio, de la arquitectura de ejecución, del catálogo Java y de su migración, y contraste de criterios con fuentes primarias de Spring Modulith, PostgreSQL, Docker y Martin Fowler.
- **Herramientas auxiliares:** lectura y comparación de archivos, búsqueda web y comprobación documental con `git diff --check`.
- **Resultado:** informe auxiliar `.local-docs/CLASS_MODEL_REVIEW.md` con hallazgos priorizados. Tras la revisión del alumno se aceptaron y documentaron los cinco primeros puntos: identidades históricas `ExecutedComponent` y `ExecutedConnection`, registro `FaultAction`, control del grafo y de su revisión por `Scenario`, revisiones inmutables de laboratorios y contratos de dominio tipados. Se actualizó el README oficial, se amplió la guía local `.local-docs/SCENARIO_EXECUTION_MODEL.md` con diagramas sencillos, se añadió el glosario `CONTEXT.md` y se ajustó `docs/EXECUTION_ARCHITECTURE.md`. Los archivos de `.local-docs/` están excluidos del control de versiones y no son fuentes oficiales. Los cambios realizados durante esta revisión sobre el antiguo README externo se revirtieron al indicar el alumno que dejó de ser una fuente del proyecto. La regla quedó incorporada a `AGENTS.md`. No se modificaron el modelo Java ni las migraciones porque estas áreas todavía no están implementadas.
- **Verificación:** `git diff --check`, comprobación de enlaces locales y de bloques Mermaid equilibrados. No se ejecutó la suite del backend ni se atribuyó cobertura a funcionalidades aún no implementadas.
- **Revisión del alumno:** el alumno confirmó que comprendía y aceptaba los puntos 1 a 5, solicitó aplicarlos y pidió mantener el README breve y con su formato actual. Posteriormente autorizó crear una rama específica, organizar el trabajo en dos commits y abrir una pull request.

## AI-2026-09-05-020 - Base reproducible del frontend

- **Fecha:** 5 de septiembre de 2026.
- **Fase:** Fase 2 - Vertical mínima del frontend.
- **Objetivo:** crear el proyecto React y TypeScript y preparar sus controles básicos de calidad.
- **Contexto aportado por el alumno:** el alumno creó la rama y el proyecto con React Router Framework, decidió mantener ese modo sin renderizado en servidor y ejecutó manualmente la configuración inicial de ESLint.
- **Modelo y configuración:** configuración principal descrita al inicio del documento.
- **Forma de uso:** explicación comparada de los modos de React Router, revisión del proyecto anterior del alumno como referencia, diagnóstico del conflicto entre ESLint 10 y los plugins de React todavía limitados a ESLint 9, y configuración de Vitest como ejecutor de pruebas del frontend. Se mantuvo ESLint 10, que es la versión con soporte, con las reglas recomendadas de JavaScript y TypeScript; las reglas específicas de React se aplazaron hasta disponer de plugins compatibles.
- **Herramientas auxiliares:** React Router 8, React 19, TypeScript, Vite 8, ESLint 10 y Vitest.
- **Resultado:** el frontend queda configurado como SPA mediante `ssr: false`, con scripts reproducibles de lint, comprobación de tipos, pruebas y construcción. El comando de pruebas acepta temporalmente que no existan casos hasta implementar la prueba unitaria prevista en P2-16.
- **Ficheros principales:** `frontend/package.json`, `frontend/package-lock.json`, `frontend/react-router.config.ts` y `frontend/eslint.config.js`.
- **Revisión del alumno:** el alumno ejecutó el scaffold, activó el modo SPA, configuró ESLint, corrigió el import sin uso detectado y autorizó añadir Vitest.

## AI-2026-09-06-021 - Inicio del cliente tipado y proxy de desarrollo

- **Fecha:** 6 de septiembre de 2026.
- **Fase:** Fase 2 - Vertical mínima del frontend.
- **Objetivo:** preparar el acceso del frontend al endpoint real del catálogo sin acoplar los componentes React al transporte HTTP.
- **Contexto aportado por el alumno:** el alumno propuso la organización por `api`, `model` y `components`, eligió denominar `service` al límite HTTP y creó personalmente el modelo TypeScript y el servicio inicial.
- **Forma de uso:** revisión guiada de las responsabilidades de rutas, componentes, modelo y acceso a la API; comprobación del contrato público existente; y configuración solicitada del proxy de desarrollo de Vite.
- **Herramientas auxiliares:** React Router, TypeScript, Vite, ESLint y los comandos `npm run typecheck`, `npm run lint` y `npm run build`.
- **Resultado:** `vite.config.ts` reenvía las peticiones con prefijo `/api` al destino configurable mediante `VITE_API_BASE_URL`, con `http://localhost:8080` como valor predeterminado. `.env.example` documenta la variable y el servicio utiliza la ruta relativa `/api/v1/component-templates`. El servicio devuelve el modelo TypeScript del contrato público, rechaza respuestas HTTP no exitosas y transforma los fallos de red en un error controlado. La comprobación de tipos, el lint, la construcción y `git diff --check` finalizaron correctamente; las pruebas automatizadas permanecen separadas en los issues posteriores previstos para pruebas del frontend.
- **Ficheros principales:** `frontend/vite.config.ts`, `frontend/.env.example` y `frontend/app/features/component-catalogue/`.
- **Revisión del alumno:** el alumno definió la estructura, creó el modelo y el servicio, añadió el control de errores HTTP y de red y revisó la función del proxy y de los archivos de entorno antes de preparar el primer commit.

## AI-2026-09-06-022 - Visualización del catálogo real en la página inicial

- **Fecha:** 6 de septiembre de 2026.
- **Fase:** Fase 2 - Vertical mínima del frontend.
- **Objetivo:** completar el issue P2-12 mostrando en React las plantillas persistidas que devuelve el backend y representando explícitamente los estados de carga, éxito y error.
- **Contexto aportado por el alumno:** el alumno creó la primera versión de `ComponentCatalogue` con un `clientLoader` y solicitó completar el código necesario para el issue.
- **Forma de uso:** revisión de la separación entre rutas y componentes; traslado del `clientLoader` a la ruta inicial; creación de un componente visual tipado y sin transporte HTTP; implementación de estados accesibles de carga y error; retirada de la pantalla inicial del scaffold; y verificación en navegador real. Se aplicaron las skills `vercel-react-best-practices`, `frontend-design` y `playwright`.
- **Herramientas auxiliares:** React Router en modo SPA, React, TypeScript, Tailwind CSS, Vite, Playwright CLI, Spring Boot, PostgreSQL mediante Docker Compose y Java 21.
- **Resultado:** la ruta inicial carga el catálogo mediante `getComponentTemplates`, muestra un fallback durante la hidratación, deriva los fallos a una vista controlada y entrega seis plantillas reales a `ComponentCatalogue`. Cada entrada presenta `name`, `type` y `key`; también existe un estado vacío. La ejecución real confirmó HTTP Service, Worker, Load Generator, PostgreSQL, Redis y RabbitMQ, y al detener Spring Boot apareció el mensaje de indisponibilidad esperado. La comprobación detectó que React Router SPA solo permite `HydrateFallback` en la ruta raíz, por lo que el fallback se ubicó en `root.tsx`.
- **Verificación:** `npm run typecheck`, `npm run lint`, `npm test`, `npm run build` y `git diff --check` finalizaron correctamente. Vitest sigue sin casos porque las pruebas del componente pertenecen al issue P2-16.
- **Ficheros principales:** `frontend/app/root.tsx`, `frontend/app/routes/home.tsx` y `frontend/app/features/component-catalogue/components/component-catalogue.tsx`.
- **Revisión del alumno:** pendiente de revisar visualmente el resultado y autorizar su incorporación al historial Git.

## AI-2026-09-07-023 - Hoja de ruta de pruebas de la Fase 2

- **Fecha:** 7 de septiembre de 2026.
- **Fase:** Fase 2 - Pruebas automáticas y cobertura.
- **Objetivo:** convertir el issue padre P2-08 y sus siete sub-issues en una guía gradual que permita aprender y completar la pirámide de pruebas sin mezclar herramientas ni responsabilidades.
- **Contexto aportado por el alumno:** el alumno indicó que todavía no domina las pruebas y pidió contrastar el issue y la guía PDF del TFG antes de proponer instalaciones, orden de trabajo y criterios de cierre.
- **Modelo y configuración:** configuración principal descrita al inicio del documento.
- **Forma de uso:** lectura del issue #8 y de los issues #19 a #25, inspección del código y dependencias actuales, extracción y revisión visual de las páginas relevantes del PDF del TFG, comprobación de versiones locales y contraste con documentación oficial de Spring Boot, Testcontainers, Testing Library, Vitest y Playwright. Se utilizó la skill `pdf` para revisar el documento académico.
- **Herramientas auxiliares:** GitHub CLI, Maven Wrapper, npm, Docker, `pdfplumber`, Poppler y documentación oficial de las herramientas de prueba.
- **Resultado:** se creó la hoja de ruta con el modelo mental de cada nivel, inventario de herramientas ya disponibles y pendientes, orden P2-13/P2-16/P2-14/P2-15/P2-17/P2-18/P2-19, comandos objetivo, criterios de finalización y una política inicial recomendada del 70 % de líneas por aplicación. Posteriormente se trasladó a `.local-docs/PHASE_2_TESTING_ROADMAP.md` como material local de aprendizaje no versionado. No se instalaron dependencias ni se implementaron pruebas en este bloque.
- **Revisión del alumno:** pendiente de revisión antes de comenzar el issue P2-13.

## AI-2026-09-12-024 - Primera prueba unitaria del servicio backend

- **Fecha:** 12 de septiembre de 2026.
- **Fase:** Fase 2 - Pruebas automáticas del backend.
- **Objetivo:** implementar el issue P2-13 sobre la regla de negocio del catálogo y utilizarlo como introducción práctica a JUnit, Mockito, AssertJ y Arrange-Act-Assert.
- **Contexto aportado por el alumno:** después de revisar la hoja de ruta, el significado de una prueba unitaria de servicio y la función de Mockito, el alumno autorizó implementar los casos acordados sobre `ComponentTemplateService#getEnabledTemplates()`.
- **Forma de uso:** aplicación guiada de TDD sobre el límite público del servicio, utilizando el servicio real y un doble de `ComponentTemplateRepository`. Se comprobó la sensibilidad del caso principal retirando temporalmente la ordenación, observando el fallo esperado y restaurando la implementación existente.
- **Herramientas auxiliares:** Java 21, Maven Wrapper, JUnit Jupiter, Mockito, Byte Buddy y AssertJ. Durante el diagnóstico se probó temporalmente el generador clásico de mocks para aislar una restricción de autoacoplamiento de agentes; con acceso completo se confirmó que el motor normal de Mockito funciona y se retiró aquella extensión especial.
- **Resultado:** `ComponentTemplateServiceTests` comprueba que una respuesta desordenada del repositorio se devuelve ordenada por `ComponentType`, conserva campos representativos, consulta una vez el repositorio y devuelve una lista vacía cuando no hay plantillas habilitadas. La prueba no arranca Spring, JDBC, Flyway ni Docker y no requirió nuevas dependencias. El comando focalizado superó 2 pruebas y la suite completa superó 3 pruebas con PostgreSQL local disponible. Para eliminar el aviso de autoacoplamiento en Java 21 y mantener compatibilidad futura, el `pom.xml` resuelve el JAR de Mockito mediante `maven-dependency-plugin` y lo carga explícitamente como `-javaagent` en Maven Surefire, preservando `argLine` para su futura convivencia con JaCoCo. Se añadió `.local-docs/MOCKITO_UNIT_TEST_GUIDE.md` como explicación local no versionada de la estructura, métodos, flujo y límites del test.
- **Ficheros principales:** `backend/pom.xml` y `backend/src/test/java/es/codeurjc/infracture/catalog/application/ComponentTemplateServiceTests.java`; como apoyo local ignorado, `.local-docs/MOCKITO_UNIT_TEST_GUIDE.md` y `.local-docs/PHASE_2_TESTING_ROADMAP.md`.
- **Revisión del alumno:** el alumno revisó la explicación del test y autorizó crear la rama `test/component-template-service-tests`, preparar el commit, publicarla y abrir la PR; el merge y el cierre manual del issue siguen pendientes de autorización separada.

## AI-2026-09-12-025 - Prueba unitaria de la ruta del catálogo en React

- **Fecha:** 12 de septiembre de 2026.
- **Fase:** Fase 2 - Pruebas automáticas del frontend.
- **Objetivo:** implementar el issue P2-16 comprobando los estados observables de carga, éxito y error de la vista del catálogo.
- **Contexto aportado por el alumno:** tras aclarar la diferencia entre el DOM virtual de React y un DOM simulado para pruebas, el alumno autorizó crear una rama específica e instalar las dependencias necesarias.
- **Forma de uso:** revisión del modo SPA y de la documentación instalada de React Router; configuración de Vitest con `jsdom`; composición de la ruta real mediante un router en memoria; y sustitución exclusiva del servicio HTTP por un doble controlable.
- **Herramientas auxiliares:** React Router, Vitest, React Testing Library, DOM Testing Library, `jest-dom` y `jsdom`.
- **Resultado:** la prueba comprueba que el estado de carga aparece antes de resolverse la petición, que los datos devueltos se muestran y que un rechazo activa el límite de error accesible. Las aserciones consultan roles y texto visibles. El comando de pruebas deja de aceptar una suite vacía mediante `--passWithNoTests`. Por decisión del alumno, las pruebas se agrupan bajo `frontend/test/` para mantener separado el código de producción; se añadió además una guía local de aprendizaje sobre Vitest y el flujo completo del test.
- **Verificación:** `npm test` superó 3 pruebas; `npm run typecheck`, `npm run lint`, `npm run build` y `git diff --check` finalizaron correctamente.
- **Ficheros principales:** `frontend/vitest.config.ts`, `frontend/test/setup.ts`, `frontend/test/routes/home.test.tsx`, `frontend/package.json` y `frontend/package-lock.json`; como apoyo local ignorado, `.local-docs/VITEST_FRONTEND_TEST_GUIDE.md`.
- **Revisión del alumno:** el alumno pidió separar las pruebas en `frontend/test/`, revisó la explicación de Vitest y autorizó preparar el commit, publicar la rama y abrir la pull request. El merge y el cierre del issue permanecen pendientes de autorización separada.

## AI-2026-09-16-026 - Ratificación de MinIO y migración a Java 25 LTS

- **Fecha:** 16 de septiembre de 2026.
- **Fase:** Fase 2 - Consolidación de decisiones tecnológicas y actualización del entorno.
- **Objetivo:** registrar el acuerdo alcanzado con el tutor sobre el almacenamiento de imágenes y actualizar el backend a la versión LTS de Java elegida para continuar el desarrollo.
- **Contexto aportado por el alumno:** tras la reunión, el tutor confirmó que se mantiene MinIO al no existir actualmente una alternativa que justifique cambiar la arquitectura prevista y recomendó migrar de Java 21 LTS a Java 25 LTS mientras el proyecto todavía se encuentra en una etapa temprana.
- **Forma de uso:** revisión cruzada del README, la arquitectura, la configuración Maven, el pin local del JDK y la documentación operativa; contraste de Java 25 con la hoja de ruta oficial de Oracle y con los requisitos de Spring Boot 4.1.1; registro de la decisión de MinIO mediante un ADR aceptado; y actualización y comprobación del entorno Java.
- **Herramientas auxiliares:** Eclipse Temurin 25.0.4.1, Homebrew, `jenv`, Maven Wrapper, Docker Compose, PostgreSQL y documentación oficial de Oracle y Spring Boot.
- **Resultado:** MinIO queda ratificado como almacenamiento local compatible con S3 para avatares, portadas e iconos, mientras PostgreSQL conserva únicamente las claves de objeto. El repositorio fija Java 25.0.4.1 en `.java-version`, Maven compila con `release 25` y la documentación activa exige Java 25 LTS. Temurin 25.0.4.1 se instaló y registró en `jenv`; la suite completa del backend compiló con esa versión y superó sus 3 pruebas con PostgreSQL. La instalación anterior de OpenJDK 25.0.2 se retiró después de verificar la actualización y no se implementó todavía la integración con MinIO.
- **Ficheros principales:** `.java-version`, `backend/pom.xml`, `backend/README.md`, `README.md`, `CHANGELOG.md`, `AGENTS.md` y `docs/adr/0001-minio-para-almacenamiento-de-imagenes.md`.
- **Revisión del alumno:** el alumno comunicó directamente ambas decisiones acordadas con el tutor y solicitó documentarlas y aplicar la migración.

## AI-2026-09-16-027 - Pruebas de integración del catálogo con Testcontainers

- **Fecha:** 16 de septiembre de 2026.
- **Fase:** Fase 2 - Pruebas automáticas del backend.
- **Objetivo:** implementar el issue P2-14 verificando la integración entre la lógica de negocio, JPA, Flyway y el catálogo contra un PostgreSQL real y desechable.
- **Contexto aportado por el alumno:** tras cerrar las pruebas unitarias P2-13 y P2-16, el alumno pidió identificar e implementar el siguiente issue de la hoja de ruta.
- **Forma de uso:** aplicación guiada de TDD sobre la interfaz pública de `ComponentTemplateService`; comprobación inicial de que la prueba no compilaba sin Testcontainers; incorporación de las dependencias mínimas; conexión automática mediante `@ServiceConnection`; comprobación roja de que consultar directamente el repositorio no aplica el orden de negocio; ejecución repetida con un contenedor nuevo; y aislamiento de cada caso mediante transacciones revertidas.
- **Herramientas auxiliares:** Java 25, Maven Wrapper, Spring Boot Test, Spring Data JPA, Flyway, Testcontainers 2.0.5, PostgreSQL 18.6 y Docker Desktop.
- **Resultado:** `ComponentTemplateRepositoryTests` inicia automáticamente PostgreSQL 18.6, deja que Flyway aplique V1 y V2 sobre un esquema vacío, comprueba las seis plantillas sembradas y verifica que `ComponentTemplateService#getEnabledTemplates()` obtiene del repositorio real únicamente los datos habilitados y los ordena por tipo. La anterior comprobación vacía de contexto se sustituyó por esta prueba, que también arranca el contexto completo. Dos ejecuciones consecutivas utilizaron contenedores y puertos distintos; la ejecución focalizada superó 2 pruebas y `./mvnw verify` superó las 4 pruebas del backend.
- **Ficheros principales:** `backend/pom.xml` y `backend/src/test/java/es/codeurjc/infracture/catalog/persistence/ComponentTemplateRepositoryTests.java`; se retiró `backend/src/test/java/es/codeurjc/infracture/InfractureBackendApplicationTests.java`.
- **Revisión del alumno:** el alumno autorizó la implementación y, posteriormente, la creación de la rama de pruebas, el commit, la publicación y la apertura del pull request.

## AI-2026-09-16-028 - Reestructuración del changelog y reglas de mantenimiento

- **Fecha:** 16 de septiembre de 2026.
- **Fase:** Fase 2 - Documentación y trazabilidad continua.
- **Objetivo:** poner al día el changelog, distinguir los cambios incorporados al repositorio de las funcionalidades planificadas y conservar criterios uniformes de mantenimiento y nombrado de ramas.
- **Contexto aportado por el alumno:** el alumno indicó que todavía no existe una versión publicada de la aplicación, decidió mantener todos los cambios bajo `Unreleased` hasta una fase posterior y pidió registrar como reglas permanentes la actualización pertinente del changelog y el formato de las ramas.
- **Forma de uso:** revisión del historial Git y de la documentación activa, clasificación de las entregas de las Fases 1 y 2, reescritura de las entradas conforme a las categorías de Keep a Changelog y contraste de los nombres de ramas recientes. Se aplicó la skill `ponytail` para evitar duplicar el README y conservar únicamente reglas relevantes.
- **Resultado:** `CHANGELOG.md` conserva una única sección `Unreleased`, resume la Fase 1 como documentación, incorpora las entregas reales del backend, frontend, API y pruebas, e identifica expresamente las tecnologías decididas cuya integración todavía está pendiente. `AGENTS.md` exige revisar el changelog en cada bloque de trabajo y fija las ramas como `<type>/<short-kebab-case-description>` en inglés, sin prefijo `codex/`.
- **Ficheros principales:** `CHANGELOG.md`, `AGENTS.md` y `AI_USAGE.md`.
- **Verificación:** revisión del diff, contraste con los commits no mergeados y comprobación mediante `git diff --check`; no se ejecutaron suites de código porque el cambio afecta únicamente a documentación.
- **Revisión del alumno:** el alumno solicitó explícitamente la reestructuración completa, confirmó que `Unreleased` debe mantenerse mientras no exista una versión publicada y pidió convertir la actualización pertinente del changelog y el formato de las ramas en instrucciones permanentes para los agentes.

## AI-2026-09-17-029 - Prueba de sistema HTTP del catálogo con REST Assured

- **Fecha:** 17 de septiembre de 2026.
- **Fase:** Fase 2 - Pruebas automáticas del backend.
- **Objetivo:** implementar el issue P2-15 comprobando el endpoint del catálogo como un sistema en ejecución mediante una petición HTTP real.
- **Contexto aportado por el alumno:** una vez fusionadas las pruebas de integración P2-14, el alumno pidió identificar el siguiente issue de la fase y autorizó implementar P2-15.
- **Forma de uso:** aplicación de TDD sobre la costura pública `GET /api/v1/component-templates`; creación inicial de una prueba que no compilaba sin REST Assured; incorporación exclusiva de la dependencia de test necesaria; arranque de Spring Boot en un puerto aleatorio; reutilización del patrón Testcontainers y `@ServiceConnection`; y validación del estado, tipo de contenido, cantidad, orden estable y campos representativos del JSON. Se aplicaron las skills `tdd` y `ponytail` para conservar la prueba en la interfaz HTTP y evitar clases base o configuración adicional sin necesidad actual.
- **Herramientas auxiliares:** Java 25, Maven Wrapper, Spring Boot Test, REST Assured 6.0.1, Testcontainers 2.0.5, PostgreSQL 18.6, Flyway y Docker Desktop.
- **Resultado:** `ComponentTemplateApiSystemTests` arranca Tomcat y PostgreSQL en puertos aleatorios, aplica las dos migraciones sobre un esquema vacío y comprueba por HTTP las seis plantillas en su orden contractual. La ejecución focalizada superó 1 prueba y `./mvnw verify` superó las 5 pruebas del backend sin modificar código de producción.
- **Ficheros principales:** `backend/pom.xml`, `backend/src/test/java/es/codeurjc/infracture/catalog/api/ComponentTemplateApiSystemTests.java`, `CHANGELOG.md` y `AI_USAGE.md`.
- **Revisión del alumno:** el alumno revisó el funcionamiento de REST Assured, Flyway y el aislamiento entre los PostgreSQL temporales, confirmó mantener Testcontainers y autorizó crear la rama, el commit, la publicación y el pull request. Posteriormente fusionó el pull request y solicitó dejar la rama local limpia.

## AI-2026-09-19-030 - Integración real entre el frontend y el backend

- **Fecha:** 19 de septiembre de 2026.
- **Fase:** Fase 2 - Pruebas de integración del sistema.
- **Objetivo:** implementar el issue P2-17 y demostrar que un dato persistido en PostgreSQL atraviesa Flyway, Spring Boot, el endpoint HTTP, el cliente TypeScript y la vista React sin sustituir el servidor por dobles de prueba.
- **Contexto aportado por el alumno:** tras incorporar P2-14 y P2-15, el alumno pidió identificar e implementar el siguiente issue de la fase de pruebas y confirmó la costura pública propuesta para P2-17.
- **Modelo y configuración:** configuración activa de Codex para esta sesión.
- **Forma de uso:** aplicación de TDD sobre `clientLoader` y la vista pública del catálogo; comprobación roja inicial del cliente con el servidor ausente; reutilización del soporte Testcontainers ya instalado; creación de una aplicación de prueba en el classpath de test según el patrón oficial de Spring Boot; arranque coordinado mediante un script Bash; y separación de las suites unitarias y de integración de Vitest. Se aplicaron las skills `tdd`, `ponytail`, `react-router` y `vercel-react-best-practices`.
- **Herramientas auxiliares:** Java 25, Maven Wrapper, Spring Boot `test-run`, Testcontainers 2.0.5, PostgreSQL 18.6, Flyway, Docker Desktop, Node.js 24, Vitest, jsdom y React Testing Library.
- **Resultado:** `scripts/test-client-server-integration.sh` arranca Spring Boot en el perfil `integration-test` con un PostgreSQL real y desechable, espera a que Flyway y el backend estén disponibles, ejecuta el cliente HTTP sin mocks y comprueba que la plantilla sembrada `HTTP Service` llega a la vista React. El cliente conserva la ruta `/api/v1/component-templates` y la resuelve contra el origen actual, manteniendo el proxy de Vite en desarrollo. Los fallos de arranque, red, contrato y renderizado producen mensajes diferenciados. La prueba integrada superó 1 caso y eliminó el contenedor al finalizar; `./mvnw verify` superó las 5 pruebas del backend, `npm test` superó las 3 pruebas unitarias del frontend y también finalizaron correctamente la comprobación de tipos, el lint, la construcción y la validación sintáctica del script. Como apoyo al aprendizaje se creó `.local-docs/CLIENT_SERVER_INTEGRATION_TEST_GUIDE.md`, que documenta el flujo completo, el reparto de responsabilidades, la reproducción manual, el diagnóstico y la futura reutilización en CI.
- **Ficheros principales:** `backend/src/test/java/es/codeurjc/infracture/TestInfractureBackendApplication.java`, `backend/src/test/resources/application-integration-test.properties`, `frontend/test/integration/component-catalogue.integration.test.tsx`, `frontend/vitest.integration.config.ts`, `frontend/vitest.config.ts`, `frontend/app/features/component-catalogue/api/component-template-service.ts`, `frontend/package.json` y `scripts/test-client-server-integration.sh`; como apoyo local ignorado, `.local-docs/CLIENT_SERVER_INTEGRATION_TEST_GUIDE.md`.
- **Revisión del alumno:** el alumno confirmó la costura pública antes de escribir la prueba y solicitó implementar el issue; los cambios permanecen sin preparar ni publicar para su revisión.

## AI-2026-09-19-031 - Guía técnica de las pruebas con REST Assured

- **Fecha:** 19 de septiembre de 2026.
- **Fase:** Fase 2 - Documentación de las pruebas automáticas.
- **Objetivo:** documentar de forma autocontenida la prueba REST Assured implementada para que el alumno pueda comprender su clasificación, alcance, arquitectura, configuración y ciclo de vida.
- **Contexto aportado por el alumno:** después de revisar interactivamente REST Assured, los puertos aleatorios, Testcontainers, Flyway y el aislamiento de las bases de datos, el alumno pidió reunir toda la explicación en un fichero Markdown.
- **Forma de uso:** revisión del código de la prueba de sistema, las pruebas unitarias y de persistencia, la configuración Maven, las migraciones y el flujo de producción; contraste de los conceptos con la documentación oficial de REST Assured, Spring Boot, Testcontainers y Flyway; y redacción de una guía centrada en las decisiones reales del repositorio.
- **Herramientas auxiliares:** REST Assured 6.0.1, Spring Boot Test, JUnit 5, Hamcrest, Testcontainers 2.0.5, PostgreSQL 18.6, Flyway y documentación oficial.
- **Resultado:** `.local-docs/PRUEBAS_REST_ASSURED.md` explica por qué el caso es una prueba de sistema de API y no una E2E completa, el recorrido HTTP hasta PostgreSQL, cada anotación y aserción, el origen de los datos, los dos puertos dinámicos, la independencia entre contenedores, la alternativa de usar bases persistentes, los límites de cobertura, los comandos de ejecución y el diagnóstico de fallos habituales. Al ser material de aprendizaje, queda fuera de la documentación estable y del control de versiones.
- **Ficheros principales:** `.local-docs/PRUEBAS_REST_ASSURED.md` y `AI_USAGE.md`.
- **Verificación:** revisión cruzada con el código incorporado, comprobación de que el documento está bajo la exclusión de `.local-docs/` y validación de formato mediante `git diff --check`; no se ejecutaron suites porque los cambios de este bloque son exclusivamente documentales.
- **Revisión del alumno:** el alumno solicitó expresamente crear la guía; los cambios quedan sin preparar ni publicar para su revisión.

## AI-2026-09-20-032 - Prueba de sistema del catálogo en Chromium

- **Fecha:** 20 de septiembre de 2026.
- **Fase:** Fase 2 - Pruebas automáticas del sistema.
- **Objetivo:** implementar el issue P2-18 y verificar desde Chromium que la aplicación completa muestra el catálogo persistido que entrega el backend real.
- **Contexto aportado por el alumno:** después de fusionar P2-15 y P2-17, el alumno pidió localizar, analizar e implementar el siguiente issue pendiente de la hoja de ruta.
- **Modelo y configuración:** configuración activa de Codex para esta sesión.
- **Forma de uso:** localización del siguiente issue mediante GitHub CLI; aplicación de TDD sobre la interfaz visible de la SPA; contraste de la configuración con la documentación oficial de Playwright; reutilización del script existente de desarrollo para coordinar PostgreSQL, Spring Boot y Vite; separación explícita entre las suites de Vitest y Playwright; y comprobación deliberada de un fallo temporal para validar los artefactos de diagnóstico. Se aplicaron las skills `tdd`, `ponytail` y `playwright`.
- **Herramientas auxiliares:** Playwright Test 1.63.0, Chromium 153, React Router, Vite, Spring Boot, Flyway, PostgreSQL 18.6, Docker Compose, Java 25 y Node.js 24.
- **Resultado:** `npm run test:system` arranca la aplicación real mediante `scripts/start-dev.sh`, abre Chromium y comprueba por roles accesibles las seis plantillas sembradas por Flyway sin interceptar la respuesta del backend. La configuración limita la prueba a Chromium, conserva captura y traza cuando falla, genera un informe HTML y utiliza un único worker en CI. Vitest excluye la suite de sistema y ESLint ignora los informes generados. La ejecución local y la ejecución con `CI=true` superaron 1 caso y detuvieron los procesos y el contenedor iniciados al finalizar; una expectativa temporalmente incorrecta confirmó la creación de la captura y la traza antes de restaurar el caso válido.
- **Ficheros principales:** `frontend/playwright.config.ts`, `frontend/test/system/component-catalogue.spec.ts`, `frontend/package.json`, `frontend/package-lock.json`, `frontend/vitest.config.ts`, `frontend/eslint.config.js`, `frontend/.gitignore`, `CHANGELOG.md` y `AI_USAGE.md`.
- **Revisión del alumno:** el alumno solicitó la implementación; los cambios permanecen sin preparar ni publicar para su revisión.

## AI-2026-09-20-033 - Guía técnica de las pruebas con Playwright

- **Fecha:** 20 de septiembre de 2026.
- **Fase:** Fase 2 - Documentación de las pruebas automáticas.
- **Objetivo:** documentar de forma autocontenida la prueba Playwright implementada para que el alumno pueda comprender su clasificación, arquitectura, configuración, ciclo de vida, ejecución y diagnóstico.
- **Contexto aportado por el alumno:** después de implementar P2-18 y aclarar el significado de CI, el alumno pidió reunir en un Markdown local toda la información sobre Playwright, el motivo de cada decisión y el funcionamiento de lo incorporado.
- **Modelo y configuración:** configuración activa de Codex para esta sesión.
- **Forma de uso:** revisión del issue, la configuración Playwright, la especificación de sistema, el script coordinador, el proxy de Vite, el recorrido React y las migraciones; contraste con la documentación oficial de Playwright; y redacción de una guía centrada en las decisiones y límites reales del repositorio. Se aplicaron las skills `playwright` y `ponytail` para comprobar los conceptos operativos y evitar añadir infraestructura ajena al objetivo documental.
- **Herramientas auxiliares:** Playwright Test 1.63.0, Chromium, GitHub CLI, documentación oficial de Playwright y herramientas locales de inspección del repositorio.
- **Resultado:** `.local-docs/PRUEBAS_PLAYWRIGHT.md` explica el recorrido completo desde Chromium hasta PostgreSQL, la función de cada opción de `playwright.config.ts`, el arranque y la limpieza de servicios, las aserciones accesibles con espera automática, la ausencia de mocks HTTP, las diferencias entre ejecución local y `CI=true`, los informes y artefactos de fallo, los comandos de depuración, los límites de cobertura y los criterios para ampliar la suite. La guía aclara expresamente que el test utiliza el PostgreSQL persistente de Docker Compose, no Testcontainers, y que la variable `CI` no crea todavía un workflow de GitHub Actions. Al ser material local de aprendizaje, queda excluida del control de versiones. También se corrigió una frase obsoleta del changelog que todavía presentaba la integración de Playwright como pendiente pese a estar ya incorporada.
- **Ficheros principales:** `.local-docs/PRUEBAS_PLAYWRIGHT.md`, `CHANGELOG.md` y `AI_USAGE.md`.
- **Verificación:** revisión cruzada de la guía con el código y el issue actuales, comprobación de los enlaces oficiales, validación de que `.local-docs/` continúa ignorado y comprobación de formato mediante `git diff --check`; no se ejecutaron suites de código porque este bloque es exclusivamente documental.
- **Revisión del alumno:** pendiente de revisar la guía; los cambios permanecen sin preparar ni publicar.

## AI-2026-09-20-034 - Cobertura reproducible del backend y del frontend

- **Fecha:** 20 de septiembre de 2026.
- **Fase:** Fase 2 - Pruebas automáticas y cobertura.
- **Objetivo:** implementar el issue P2-19 generando informes de cobertura reproducibles y aplicando una puerta inicial explícita tanto al backend como al frontend.
- **Contexto aportado por el alumno:** después de fusionar P2-18 y limpiar su rama local, el alumno pidió continuar con el siguiente issue pendiente del parent P2.
- **Modelo y configuración:** configuración activa de Codex para esta sesión.
- **Forma de uso:** revalidación del issue mediante GitHub CLI; inspección de las suites existentes y de la política del 70 % ya documentada; contraste con la documentación oficial de JaCoCo y Vitest; configuración de ambos proveedores; medición inicial; incorporación de pruebas focalizadas para responsabilidades reales que no estaban cubiertas; y elevación temporal de ambos umbrales al 99 % para demostrar que los builds fallan cuando no se cumple la puerta. Se aplicó la skill `ponytail` para limitar el cambio a la cobertura local reutilizable por CI, sin adelantar workflows ni SonarQube.
- **Herramientas auxiliares:** JaCoCo Maven Plugin 0.8.15, Vitest 5.0.0, `@vitest/coverage-v8` 5.0.0, Maven Wrapper, Java 25, Node.js 24, Docker Desktop y Testcontainers.
- **Resultado:** `./mvnw verify` genera JaCoCo HTML y XML y exige un 70 % global de líneas; `npm run test:coverage` mide explícitamente todo el TypeScript y TSX de producción bajo `app/`, genera salida de terminal, HTML y LCOV y exige el mismo umbral de forma independiente. No se configuraron exclusiones propias. Se añadieron pruebas del cliente HTTP para éxito, error HTTP y fallo de conexión, además de pruebas del límite global de errores y de los recursos tipográficos. La cobertura observada fue del 90,74 % de líneas en backend y del 88,88 % en frontend; con un umbral temporal del 99 %, ambos comandos fallaron antes de restaurar el 70 %.
- **Ficheros principales:** `backend/pom.xml`, `frontend/package.json`, `frontend/package-lock.json`, `frontend/vitest.config.ts`, `frontend/test/features/component-catalogue/api/component-template-service.test.ts`, `frontend/test/root.test.tsx`, `frontend/.gitignore`, `frontend/eslint.config.js`, `CHANGELOG.md` y `AI_USAGE.md`.
- **Revisión del alumno:** pendiente de revisar la implementación; los cambios permanecen sin preparar ni publicar.

## AI-2026-09-20-035 - Guía técnica de cobertura con JaCoCo y Vitest

- **Fecha:** 20 de septiembre de 2026.
- **Fase:** Fase 2 - Documentación de las pruebas automáticas.
- **Objetivo:** documentar de forma autocontenida la cobertura implementada en P2-19 para que el alumno pueda comprender sus métricas, herramientas, configuración, informes, umbrales y futura integración con CI y SonarQube Cloud.
- **Contexto aportado por el alumno:** después de implementar P2-19 y preguntar por la visualización posterior en SonarQube Cloud, el alumno pidió reunir toda la información en una guía local siguiendo la estructura didáctica de las anteriores guías de pruebas.
- **Forma de uso:** revisión de la configuración JaCoCo y Vitest, de las pruebas que alimentan cada informe y de los resultados de las verificaciones; contraste con la documentación oficial de JaCoCo, Vitest y SonarQube Cloud; y redacción de una guía ligada a los archivos, comandos, decisiones y límites reales del repositorio. Se aplicó la skill `ponytail` para mantener fuera de la guía cualquier workflow o configuración SonarQube todavía no implementados.
- **Herramientas auxiliares:** JaCoCo Maven Plugin 0.8.15, Vitest 5.0.0, proveedor V8, informes HTML, XML y LCOV, documentación oficial y herramientas locales de inspección del repositorio.
- **Resultado:** `.local-docs/COBERTURA_JACOCO_VITEST.md` explica la diferencia entre ejecutar tests y medir cobertura, las métricas principales, el recorrido interno de JaCoCo y V8, la convivencia de los agentes JaCoCo y Mockito, cada opción incorporada, las pruebas frontend añadidas, la comprobación temporal al 99 %, la lectura de informes, el criterio de exclusiones, los comandos, el diagnóstico y la futura importación en SonarQube Cloud. La guía aclara que P2-19 genera artefactos reproducibles, pero todavía no configura CI ni publica resultados en SonarQube. Al ser material de aprendizaje, queda excluida del control de versiones.
- **Ficheros principales:** `.local-docs/COBERTURA_JACOCO_VITEST.md` y `AI_USAGE.md`.
- **Verificación:** revisión cruzada con el código actual, comprobación de enlaces oficiales, confirmación mediante `git check-ignore` de que la guía permanece local y validación de formato mediante `git diff --check`.
- **Revisión del alumno:** pendiente de revisar la guía; los cambios de P2-19 se publicarán en una rama y pull request autorizadas, mientras que el Markdown local no formará parte del commit.

## AI-2026-09-21-036 - Alineación de GitHub Flow e inicio del CI básico

- **Fecha:** 21 de septiembre de 2026.
- **Fase:** Fase 2 - Integración continua.
- **Objetivo:** implementar el CI básico del issue P2-20 y alinear la convención permanente de ramas con el proceso indicado por la guía académica del TFG.
- **Contexto aportado por el alumno:** el alumno aportó los apartados de GitHub Flow e integración continua de la guía, indicó que `main` debe permanecer estable, que los cambios deben integrarse mediante pull requests y que las ramas deben usar nombres cortos y descriptivos en inglés sin el esquema obligatorio `<type>/...`. Para este issue eligió expresamente `add-ci-workflow` y confirmó que el control básico debe ejecutarse con cada push a una rama de trabajo, reservando el control completo y la protección de `main` para P2-21.
- **Modelo y configuración:** configuración activa de Codex para esta sesión.
- **Forma de uso:** revisión textual y visual de las páginas relevantes del PDF del TFG, contraste con los issues P2-20 y P2-21 y con los comandos reales del repositorio, explicación incremental del modelo de GitHub Actions e implementación mínima del workflow. Se aplicaron las skills `pdf`, `writing-for-agents` y `ponytail`.
- **Herramientas auxiliares:** Git, GitHub CLI, Poppler, `pypdf`, Maven Wrapper, npm y herramientas locales de inspección del repositorio.
- **Ficheros de contexto:** `Desarrollo de una aplicación web como TFG v4.pdf`, `.github/workflows/development-ci.yml`, `.local-docs/GITHUB_ACTIONS_DEVELOPMENT_CI_GUIDE.md`, `.java-version`, `.nvmrc`, `backend/pom.xml`, `frontend/package.json`, `frontend/package-lock.json`, `AGENTS.md`, `CHANGELOG.md` y `AI_USAGE.md`.
- **Resultado:** se creó desde `main` la rama local `add-ci-workflow`; `AGENTS.md` adopta GitHub Flow, elimina el prefijo obligatorio por tipo y conserva la integración mediante pull requests. El workflow `Development CI` se activa con cada push a una rama distinta de `main`; prepara las versiones fijadas de Java y Node.js, usa las cachés oficiales de Maven y npm sin omitir los lockfiles y muestra por separado los resultados básicos de backend y frontend. También se redactó una guía educativa local e ignorada por Git que explica GitHub Actions, el workflow línea por línea, sus límites y la futura separación respecto a P2-21.
- **Verificación:** `./mvnw -Dtest=ComponentTemplateServiceTests test` finalizó con 2 tests correctos; `npm ci`, `npm run lint`, `npm run typecheck`, `npm test` y `npm run build` finalizaron correctamente, con 9 tests de frontend superados. También se validaron la sintaxis YAML, `git diff --check`, los enlaces locales de la guía y que `.local-docs/` permanece excluida mediante `.gitignore`.
- **Revisión del alumno:** el alumno revisó el diseño del workflow básico y autorizó preparar, confirmar y publicar los cambios en la rama `add-ci-workflow`.

## AI-2026-09-22-037 - Implementación local del CI completo

- **Fecha:** 22 de septiembre de 2026.
- **Fase:** Fase 2 - Integración continua.
- **Objetivo:** implementar el workflow completo del issue P2-21 para ejecutar toda la pirámide de pruebas en pull requests dirigidas a `main` y conservar diagnósticos útiles cuando falle una suite.
- **Contexto aportado por el alumno:** después de completar P2-20, el alumno decidió continuar con el workflow completo, pidió revisar si era adecuado reutilizar los scripts locales desde GitHub Actions y autorizó crear el workflow una vez explicada su estructura.
- **Modelo y configuración:** configuración activa de Codex para esta sesión.
- **Forma de uso:** inspección del issue P2-21, de las suites backend y frontend, de los scripts de integración y desarrollo y de la configuración de Playwright; diseño explicado antes de editar; comprobación de la versión vigente de `actions/upload-artifact`; creación y validación local del workflow.
- **Herramientas auxiliares:** Git, GitHub CLI, Maven Wrapper, Docker, Testcontainers, npm, Vitest, JaCoCo, Playwright y Chromium.
- **Ficheros principales:** `.github/workflows/pull-request-ci.yml`, `.local-docs/GITHUB_ACTIONS_DEVELOPMENT_CI_GUIDE.md`, `CHANGELOG.md` y `AI_USAGE.md`.
- **Resultado:** `Pull Request CI` se activa en pull requests hacia `main` y expone cuatro jobs independientes: backend completo, frontend completo, integración cliente-servidor y sistema en Chromium. Los jobs publican durante siete días informes, cobertura, registros o trazas cuando fallan. La protección de `main`, la publicación del workflow y la comprobación real de sus checks permanecen pendientes de los siguientes pasos del issue.
- **Verificación:** sintaxis YAML válida y `git diff --check` correcto; `./mvnw verify` superó 5 tests y el umbral JaCoCo; el frontend superó lint, typecheck, 9 tests, el umbral Vitest y el build; la integración cliente-servidor superó 1 test; Playwright superó 1 prueba en Chromium y limpió la infraestructura temporal.
- **Revisión del alumno:** pendiente de revisar la implementación; los cambios permanecen sin preparar ni publicar en la rama `add-full-ci-workflow`.

## Plantilla para nuevas entradas

Las nuevas entradas deberán agrupar interacciones que persigan una misma finalidad. No será necesario crear una entrada distinta para cada pregunta o corrección menor.

### AI-AAAA-MM-DD-NNN - Tema o bloque de trabajo

- **Fecha o periodo:**
- **Fase:**
- **Objetivo:**
- **Contexto aportado:**
- **Modelo y configuración:**
- **Forma de uso:**
- **Herramientas auxiliares:**
- **Ficheros de contexto:**
- **Resultado:**
- **Revisión del alumno:**
