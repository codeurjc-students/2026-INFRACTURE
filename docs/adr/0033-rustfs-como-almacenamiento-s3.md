---
status: accepted
date: 2026-11-24
---

# RustFS como almacenamiento S3 de las imágenes

## Contexto

El [ADR 0001](0001-minio-para-almacenamiento-de-imagenes.md) eligió MinIO para guardar avatares, portadas e iconos detrás de una abstracción propia, con PostgreSQL guardando solo la clave de cada objeto. Con el tutor se acordó seguir con MinIO aunque el proyecto estuviera archivado, siempre que se pudiera usar.

Al empezar #76 se comprobó que no se puede instalar como el resto de la plataforma:

- el repositorio `minio/minio` está archivado y su README dice que ya no se mantiene;
- la edición comunitaria solo se distribuye como código fuente desde octubre de 2025;
- `docker pull minio/minio` falla con cualquier etiqueta, `quay.io/minio/minio` exige autenticación y `bitnami/minio` ya no existe.

Infracture se instala con un Compose publicado y sin compilar nada en la máquina del usuario, así que hace falta otra imagen.

## Decisión

- **RustFS con una versión fija** (`rustfs/rustfs:1.0.6`) en el servicio `platform-storage` de Compose. Es un servidor compatible con S3, con licencia Apache-2.0.
- **Un nodo con un disco**, en el volumen `platform-storage-data`. Las funciones de clúster de RustFS (pools, reequilibrio, replicación) no se usan.
- **Solo el backend lo usa.** Habla con él por la API S3 con el SDK de AWS. Los puertos de la API (9000) y de la consola (9002 en el host) solo escuchan en `127.0.0.1`. La consola sirve para inspeccionar en desarrollo.
- **Se mantiene el resto del ADR 0001:** abstracción propia, claves generadas por el backend y solo la clave en PostgreSQL. Nada fuera de la configuración depende de que el servidor sea RustFS.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| `bitnamilegacy/minio:2025.7.23` | Es MinIO, pero congelado y sin parches de seguridad. |
| Compilar MinIO desde el repositorio archivado | Habría que compilar Go al instalar o publicar y mantener una imagen propia, sin parches. |
| `pgsty/silo`, fork de MinIO mantenido por Pigsty | Era la opción más continuista: código de MinIO con parches. El alumno prefirió RustFS por su comunidad y su recorrido a futuro. |
| SeaweedFS | Maduro, pero con más piezas de las que necesita un único bucket. |
| Garage | No tiene consola web y necesita pasos de arranque extra. |
| Guardar las imágenes en PostgreSQL | Ya se descartó en el ADR 0001. |

Se acepta que RustFS es joven: la 1.0 salió el 16 de septiembre de 2026 y su repositorio publica avisos de seguridad recientes, algunos graves. Para limitar el riesgo, la versión es fija y se actualiza a propósito, el servicio no sale de la máquina y solo el backend tiene credenciales. Como el backend solo usa la API S3 estándar, cambiar a otro servidor compatible (Silo, MinIO o Amazon S3) se reduce a cambiar la imagen, la URL y las credenciales.

## Aplicación

El servicio `platform-storage` está en [`compose.yaml`](../../compose.yaml). La integración del backend, la validación de imágenes y la limpieza de objetos llegan con #76.

## Referencias

- [Repositorio archivado de MinIO](https://github.com/minio/minio).
- [RustFS](https://github.com/rustfs/rustfs) y su [`docker-compose.yml`](https://github.com/rustfs/rustfs/blob/main/docker-compose.yml).
- [Avisos de seguridad de RustFS](https://github.com/rustfs/rustfs/security/advisories).
- [PGSTY Silo](https://github.com/pgsty/silo).
- [AWS SDK for Java 2.x: Amazon S3](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html).
