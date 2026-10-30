---
status: accepted
date: 2026-10-30
---

# Una sola versión ejecutable del contrato

## Contexto

El [ADR 0003](0003-catalogo-controlado-con-contratos-versionados.md) previó que varias versiones de un `Template contract` convivieran. Cada `Component` guardaría la suya, el backend sabría validar y compilar todas, y el administrador podría bloquear una versión para nuevos `Run`.

Mantener varias versiones vivas multiplica el trabajo de cada pieza: el validador, el compilador, las imágenes Docker y las pruebas tendrían que conocer todas las versiones que sigan guardadas en algún escenario. Además, la imagen Docker formaba parte del contrato, así que corregir un error de la imagen de demostración obligaba a publicar una versión nueva aunque el usuario no notara ningún cambio.

## Decisión

- **Una versión ejecutable.** El backend solo conoce la versión actual de cada contrato. No hay versiones antiguas ejecutables ni acción para bloquear una versión.
- **Migrar lo guardado.** Un cambio incompatible del contrato llega con una migración de Flyway que actualiza el `jsonb` de los `Component` y de las `ComponentTemplate` afectados. Lo que no se pueda convertir se marca y produce un error de validación que bloquea `Run` hasta que el propietario lo corrija.
- **El historial no se migra.** El `Execution snapshot` conserva la versión del contrato y la configuración con las que se ejecutó. Es un registro, no un diseño editable.
- **La imagen no es parte de la versión.** La versión cambia solo cuando cambia lo que el usuario puede configurar: perfiles, parámetros, límites, puertos o capacidades. La imagen y su digest pertenecen a la release de Infracture y quedan anotados en el snapshot.
- **Valores iniciales en la ficha.** El contrato define qué parámetros existen y sus límites. La `ComponentTemplate` guarda la configuración inicial (perfil y parámetros) que copia cada `Component` nuevo. Dos fichas del mismo tipo pueden empezar con valores distintos.
- **Parámetros enteros.** Todos los parámetros del catálogo son enteros. Un valor decimal se rechaza al guardar, no se trunca.
- **Siempre hay un perfil.** Todo `ComponentType` tiene al menos un perfil. Los tipos sin variantes usan uno llamado `DEFAULT`.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Varias versiones ejecutables (ADR 0003) | Cada versión viva es código, imagen y pruebas que mantener para siempre. Para una instancia local con un solo usuario por escenario, el beneficio no compensa. |
| Versión ligada a la imagen | Un arreglo interno de la imagen obligaría a migrar escenarios sin que cambie nada visible. |
| No versionar nada | El snapshot no podría explicar qué contrato se usó en una ejecución antigua. |

Es el mismo enfoque que siguen Terraform (`StateUpgraders`) o Kubernetes (conversión de `CustomResourceDefinition`): un solo esquema activo y una conversión explícita de los datos guardados.

Se acepta escribir una migración por cada cambio incompatible. A cambio, el resto del sistema solo trata con una versión. Bloquear una ficha sigue siendo posible: deshabilitarla impide añadir nodos nuevos con ella.

## Aplicación

Sustituye la parte del [ADR 0003](0003-catalogo-controlado-con-contratos-versionados.md) sobre versiones ejecutables y su bloqueo; el catálogo controlado se mantiene. Afecta a ID13–ID16 y US067/US118 de la [spec](../PHASE_3_SPEC.md#grafo-catálogo-y-contratos). La [arquitectura de ejecución](../EXECUTION_ARCHITECTURE.md#4-el-catálogo-controlado) explica el modelo completo.
