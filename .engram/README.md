# Memoria auxiliar de Infracture

Esta carpeta conserva la configuración del proyecto y las exportaciones de
Engram para recuperar contexto entre equipos y sesiones. No sustituye la
documentación, los issues, el código ni las pruebas. El registro académico
legible del uso de IA está en [AI_USAGE.md](../docs/AI_USAGE.md).

La base de datos de trabajo permanece en `~/.engram/engram.db`. Git conserva
`config.json` y, cuando haya recuerdos exportados, `manifest.json` y los
fragmentos comprimidos de `chunks/`. No editar esos fragmentos a mano.

## Guardar contexto en el repositorio

Desde la raíz del repositorio, después de guardar recuerdos en Engram:

```bash
engram sync --project 2026-infracture
engram sync --status
```

Revisar los archivos generados antes de incluirlos en el commit. Para leer un
fragmento en macOS o Linux:

```bash
gzip -dc .engram/chunks/NOMBRE_DEL_FRAGMENTO.jsonl.gz
```

Sustituir el nombre por el archivo generado. La exportación no hace staging,
commit ni push: el alumno controla esos pasos. No usar `--all`, que exportaría
otros proyectos. La etiqueta `personal` tampoco excluye recuerdos de la
exportación: guardar aquí solo contexto del TFG destinado a compartirse.

## Recuperar contexto en otro equipo

Con Engram instalado y el repositorio actualizado, desde su raíz:

```bash
engram sync --import
engram context
```

El plugin de Codex también intenta importar al iniciar una sesión si existe
el manifiesto. Tras recuperar memoria, contrastarla con la documentación,
el estado de Git, el código y las pruebas actuales, especialmente después
de cambios manuales.

## Verificación de la configuración

El 3 de octubre de 2026 se verificó el guardado desde Codex con su sesión
registrada, la búsqueda del recuerdo y su exportación a `chunks/` y
`manifest.json`. La importación se comprobó en una base temporal vacía,
recuperando el recuerdo sin modificar la base de trabajo.

Tras esa comprobación, el alumno pidió dejar la memoria vacía: se eliminaron
los recuerdos, prompts, sesiones y exportaciones de prueba, conservando la
configuración.

Las exportaciones incluyen también prompts y metadatos de sesión: revisar
el contenido completo antes de publicarlo.

Referencia: [documentación de Engram — Git Sync](https://github.com/Gentleman-Programming/engram/blob/main/DOCS.md#git-sync).
