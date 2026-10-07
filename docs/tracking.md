# Seguimiento

El trabajo se organiza en el [GitHub Project **2026-INFRACTURE**](https://github.com/orgs/codeurjc-students/projects/51). El tablero permite consultar las tareas de cada fase y su estado actual sin mantener una lista paralela en esta página.

Cada tarea de desarrollo se concreta en un *issue* con objetivo, alcance, criterios de aceptación, dependencias y forma de verificación. El trabajo se realiza en una rama y se integra mediante una *pull request* vinculada al issue. En la revisión se documentan los cambios, las pruebas ejecutadas y las limitaciones; los controles de CI comprueban el resultado antes de incorporarlo a `main`.

El [`CHANGELOG.md`](../CHANGELOG.md) resume los cambios relevantes entregados en cada versión. El [registro de uso de IA](AI_USAGE.md#criterio-de-registro) conserva las aportaciones materiales de IA al desarrollo, agrupadas por finalidad. Las decisiones de diseño se consultan en la spec, el glosario y los ADR; las tareas y su estado, en GitHub.

## Changelog y notas de versión

El changelog sirve a quienes usan, instalan o mantienen Infracture. Cada entrada explica un cambio ya incorporado y su efecto: una función, un error corregido, una incompatibilidad, una mejora de seguridad o un cambio significativo en instalación, configuración, calidad o documentación de uso/desarrollo. Una modificación interna merece entrada solo si aporta un efecto relevante para esas personas.

Se sigue [Keep a Changelog](https://keepachangelog.com/en/1.1.0/): cambios seleccionados por versión, categorías homogéneas y versiones más recientes primero. Usar únicamente categorías con contenido: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed` y `Security`. Mantener `Unreleased` arriba para los cambios incorporados que aún no se han publicado.

Redactar una viñeta breve por cambio significativo; reunir los ajustes que entregan el mismo resultado. Explicar las incompatibilidades y los pasos de migración cuando existan. Un issue o PR puede enlazar evidencia, pero su título no sustituye la explicación. Planes, decisiones todavía no implementadas, creación/reorganización de tickets, actividad de IA, ajustes personales, limpieza de auxiliares y operativa Git rutinaria quedan fuera. Actualizar estas reglas o depurar los registros tampoco constituye una novedad de la aplicación.

### Publicación de una versión

1. En la preparación autorizada de la release, contrastar `Unreleased` con el código, las pruebas y los cambios incluidos en el commit de entrega. Retirar lo que no forme parte de esa versión y destacar incompatibilidades o migraciones.
2. Confirmar versión y fecha de publicación. Trasladar las entradas a `## [VERSIÓN] - AAAA-MM-DD` y dejar `Unreleased` para el siguiente desarrollo. La sección documenta la versión entregada; no se inventan versiones ni fechas para cerrar una fase. Si la publicación se aplaza, ajustar la fecha antes de fijar el tag.
3. Integrar esa preparación mediante PR y usar el commit correspondiente para el tag/release aprobados. En Fase 3 se conserva la convención académica acordada: tag Git/release `0.1`, proyectos e imagen `0.1.0`, alias de imagen y Compose OCI `0.1`; el encabezado del changelog identifica `0.1.0` y enlaza el tag `0.1`. El desarrollo posterior cambia las versiones en otra PR según la spec. Este procedimiento no altera las exigencias de la guía ni resuelve sus inconsistencias por su cuenta.
4. Preparar las notas oficiales de GitHub a partir de esa sección: resumen de la versión, cambios relevantes y, cuando corresponda, instrucciones de actualización o limitaciones. Enlazar el tag y la comparación con la versión anterior; para la primera versión, enlazar su tag. Añadir los enlaces de versión/comparación al changelog solo cuando correspondan a referencias reales.
5. Revisar y publicar con autorización del alumno. Las [notas automáticas de GitHub](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes) pueden servir como borrador para detectar PR omitidas; su listado no sustituye el resumen revisado. El [procedimiento de GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository) permite preparar un borrador antes de publicarlo. Mantener las notas y el changelog coherentes; corregir errores históricos de forma explícita sin presentar cambios posteriores como parte de una versión anterior.

## Seguimiento de Fase 3

Conservar las fuentes iniciales —objetivos, metodología, análisis, funcionalidades y autores— e identificarlas al actualizar la documentación, distinguiendo el diseño inicial del resultado implementado. Mantener la spec local, el glosario y los ADRs en sus ubicaciones existentes. La [spec de Fase 3 en GitHub](https://github.com/codeurjc-students/2026-INFRACTURE/issues/60) recoge el orden de trabajo y enlaza las tareas; cada tarea incorporará enlaces a cambios y evidencias reales conforme se completen, sin inventar fechas, horas o resultados.

La preparación documental existente no se repite como una nueva issue: T02 se ha retirado tras revisión. Al completar una aportación se valora si corresponde actualizar seguimiento, changelog o el tema existente de uso de IA; no se exige una entrada por tarea. El alumno elige trabajo manual, acompañado o delegado y realiza el commit después de revisar; la preparación o el estado de una issue no autoriza esas acciones por sí solos. La [issue #132](https://github.com/codeurjc-students/2026-INFRACTURE/issues/132) audita al final la conservación de fuentes, los registros y la trazabilidad.

### Pendientes académicos C11

- **Calendario:** confirmar la fecha concreta con el alumno o tutor; la guía indica «15 de diciembre» sin año. Sigue pendiente y no se deduce el año de la fecha actual.
- **Despliegue externo:** confirmar con el alumno o tutor su tratamiento en la documentación académica. Sigue pendiente; no añade un despliegue cloud al alcance local aprobado.

Registrar aquí las conclusiones y su confirmación cuando ocurran. La [issue #129](https://github.com/codeurjc-students/2026-INFRACTURE/issues/129) conserva C11 y debe referenciar la conclusión antes de cerrar la documentación correspondiente; el trabajo independiente puede avanzar. La issue #132 comprueba al final que los pendientes se han resuelto y documentado. No se trasladan al cierre final decisiones que necesite antes una tarea dependiente.
