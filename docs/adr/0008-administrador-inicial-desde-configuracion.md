---
status: accepted
date: 2026-10-17
---

# Administrador inicial desde configuración

## Contexto

La aplicación necesita un único administrador independiente de los datos de ejemplo. El registro público siempre crea usuarios ordinarios. Reiniciar la aplicación no debe restablecer las credenciales del administrador.

## Decisión

Usar `AdminInitializer`, un `ApplicationRunner`, para solicitar la creación del administrador al arrancar. Si ya existe un usuario con rol `ADMIN`, no modificarlo. La creación comparte con el registro la normalización, validación y generación del hash Argon2id, pero asigna el rol desde el servidor.

Leer `infracture.admin.username`, `infracture.admin.email`, `infracture.admin.display-name` e `infracture.admin.password` desde configuración externa. Se admite el `.env` de la raíz al arrancar desde `backend/`, o las variables `INFRACTURE_ADMIN_USERNAME`, `INFRACTURE_ADMIN_EMAIL`, `INFRACTURE_ADMIN_DISPLAY_NAME` e `INFRACTURE_ADMIN_PASSWORD`. El fichero local queda ignorado por Git y se proporciona una plantilla sin contraseña en `.env.example`.

Spring Boot importa `../.env[.properties]` desde `application.properties`, sin scripts ni dependencias adicionales. El formato compartido es `KEY=value`, sin `export` ni comillas envolventes; Spring interpreta el fichero como propiedades Java, no como código de shell. `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` configuran Compose y los valores predeterminados del datasource. Las variables `SPRING_DATASOURCE_*` permiten sobrescribir la conexión del backend.

La primera creación requiere datos válidos y una contraseña que cumpla la política existente. No se incluye una contraseña predeterminada para la aplicación. Las pruebas usan configuración propia y bases de datos aisladas.

## Integridad y protección

La migración V5 añade un índice único limitado al rol `ADMIN`. La base de datos impide un segundo administrador, sin limitar el número de usuarios ordinarios.

La regla `User.checkCanDeactivateOrDelete()` rechaza operaciones destructivas sobre el administrador y permite las de usuarios ordinarios. Las futuras operaciones administrativas de desactivación y borrado deberán invocarla; la edición del perfil no utiliza esta restricción. La integración de esas operaciones se verifica en las issues #113 y #117.

## Consecuencias

El arranque inicial falla si falta configuración válida. Una vez creado el administrador, cambiar o retirar las credenciales de inicialización no cambia su cuenta ni su contraseña. La inicialización no promueve cuentas ordinarias existentes.

## Implementación

- [AdminInitializer](../../backend/src/main/java/es/codeurjc/infracture/identity/application/AdminInitializer.java).
- [UserService](../../backend/src/main/java/es/codeurjc/infracture/identity/application/UserService.java).
- [Migración V5](../../backend/src/main/resources/db/migration/V5__enforce_single_administrator.sql).
- [Pruebas de inicialización](../../backend/src/test/java/es/codeurjc/infracture/identity/persistence/AdminInitializerIntegrationTests.java).
