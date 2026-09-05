# Registro de uso de herramientas de IA

Este documento registra el uso de herramientas de inteligencia artificial durante la concepción y el desarrollo de Infracture. Las interacciones relacionadas se agrupan por tema y finalidad para conservar la trazabilidad sin convertir el documento en una transcripción de cada mensaje.

El periodo cubierto actualmente comprende desde el **27 de julio de 2026** hasta el **5 de septiembre de 2026**. El orden de las entradas es principalmente temático; cuando un mismo tema se trabajó en varias sesiones, se indica un intervalo de fechas.

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
