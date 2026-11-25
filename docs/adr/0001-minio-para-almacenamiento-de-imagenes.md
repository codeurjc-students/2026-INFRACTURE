---
status: accepted, parcialmente sustituido por el ADR 0033
date: 2026-09-16
---

# MinIO para el almacenamiento local de imágenes

Tras la revisión con el tutor, se mantiene MinIO como almacenamiento local compatible con la API S3 para avatares, portadas e iconos de las plantillas. La alternativa de guardar las imágenes directamente en PostgreSQL se descarta porque no ofrece una ventaja suficiente para cambiar la arquitectura prevista: la base de datos conservará únicamente la clave del objeto y el backend accederá a MinIO mediante una abstracción propia.

El 24 de noviembre de 2026, el [ADR 0033](0033-rustfs-como-almacenamiento-s3.md) sustituyó MinIO por RustFS porque MinIO dejó de publicar imágenes. El almacenamiento compatible con S3, la abstracción propia y las claves en PostgreSQL se mantienen.
