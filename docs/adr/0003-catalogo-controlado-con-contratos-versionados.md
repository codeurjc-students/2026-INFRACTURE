---
status: accepted, parcialmente sustituido por el ADR 0010
date: 2026-10-15
---

# Catálogo controlado con contratos versionados

## Contexto

El catálogo debe permitir variantes de componentes y evolucionar sin cambiar de forma retroactiva los escenarios ni su historial. Una ficha administrable y el comportamiento ejecutable tienen ciclos de cambio distintos.

## Decisión

Separar `ComponentType`, `ComponentTemplate` y `Template contract`. El backend reconoce los tipos y las versiones de contrato ejecutables. El administrador gestiona fichas con metadatos y valores iniciales dentro de esos contratos.

Cada `Component` conserva el contrato concreto y su configuración. Cambiar los valores iniciales de una ficha afecta a nodos nuevos. Deshabilitar una ficha y bloquear una versión de contrato son acciones distintas: la primera controla nuevas incorporaciones; la segunda controla nuevos `Run`. Las ejecuciones ya reservadas conservan el contrato de su snapshot.

## Motivo y consecuencias

Aceptar imágenes o comandos libres daría más flexibilidad, pero impediría garantizar qué configuración, conexiones, salud y evidencias sabe gestionar Infracture. Resolver siempre el contrato o los valores actuales del catálogo alteraría el significado de diseños e historias existentes.

Se acepta mantener contratos explícitos y versiones ejecutables reconocidas a cambio de controlar la validación y preservar el significado de cada experimento. Incorporar nuevos comportamientos exige desarrollo y verificación; editar una ficha no publica código ejecutable.

## Aplicación

El 30 de octubre de 2026, el [ADR 0010](0010-una-sola-version-ejecutable-del-contrato.md) sustituyó la parte de versiones ejecutables y su bloqueo: solo existe la versión actual de cada contrato y los datos guardados se migran. El catálogo controlado y la separación entre tipo, ficha y contrato se mantienen.

Este ADR registra acuerdos de diseño previos; al registrarlo, los contratos versionados y su administración siguen pendientes de implementación. Las reglas detalladas y sus criterios permanecen en [Grafo, catálogo y contratos de la spec](../PHASE_3_SPEC.md#grafo-catálogo-y-contratos). El [glosario](../../GLOSSARY.md) define los términos y la [arquitectura de ejecución](../EXECUTION_ARCHITECTURE.md#4-el-catálogo-controlado) explica su uso.
