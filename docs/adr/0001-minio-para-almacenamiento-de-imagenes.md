---
status: accepted
date: 2026-09-16
---

# MinIO para el almacenamiento local de imágenes

Tras la revisión con el tutor, se mantiene MinIO como almacenamiento local compatible con la API S3 para avatares, portadas e iconos de las plantillas. La alternativa de guardar las imágenes directamente en PostgreSQL se descarta porque no ofrece una ventaja suficiente para cambiar la arquitectura prevista: la base de datos conservará únicamente la clave del objeto y el backend accederá a MinIO mediante una abstracción propia.
