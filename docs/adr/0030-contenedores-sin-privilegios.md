---
status: accepted
date: 2026-11-06
---

# Contenedores sin privilegios para la plataforma y el experimento

## Contexto

Infracture se instala en el ordenador del usuario y crea contenedores en su Docker. El [ADR 0013](0013-motor-de-ejecucion-con-docker-compose.md) ya limita qué puede pedir el backend a Docker, mediante `docker-proxy`. Pero el backend sigue siendo un proceso expuesto a la red que guarda contraseñas de la base de datos. Los nodos del experimento ejecutan imágenes y parámetros que el usuario elige dentro del catálogo, y el plan incluye texto que escribe el usuario, como el nombre de un nodo.

ID23 de la [spec](../PHASE_3_SPEC.md#ejecución-observación-y-eliminación) exige separar la plataforma del experimento, sin privilegios, montajes ni puertos elegidos por el usuario y sin acceso del experimento al motor o a los secretos de la plataforma. Hacía falta decidir cómo se aplica eso en cada contenedor.

## Decisión

- **El backend no corre como root.** La imagen ([`docker/Dockerfile`](../../docker/Dockerfile)) crea el usuario y el grupo `infracture` y ejecuta la JVM con `USER infracture`.
- **El contenedor del backend está cerrado** ([`compose.yaml`](../../compose.yaml)): `read_only: true` con `tmpfs` solo en `/tmp` (donde el CLI de Docker escribe su configuración, `DOCKER_CONFIG=/tmp/docker`), `cap_drop: [ALL]`, `no-new-privileges:true` y el puerto publicado solo en `127.0.0.1`. No monta el socket de Docker.
- **El proxy tiene las mismas restricciones, salvo el usuario.** `docker-proxy` corre como root porque el socket de la máquina pertenece a root, pero con sistema de ficheros de solo lectura, sin capacidades, con `no-new-privileges`, el socket montado en solo lectura y sin puerto publicado.
- **Todos los nodos del experimento pasan por `confined()`** en [`ComposeRuntimeEngine`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ComposeRuntimeEngine.java), al traducir el plan a Compose: la red de su ejecución, sus etiquetas, `read_only` con `tmpfs` en `/tmp`, `cap_drop: [ALL]`, `no-new-privileges:true` y los límites del plan (hoy 0,5 CPU, 384 MB y 200 procesos, fijados en [`PlanCompiler`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/PlanCompiler.java)). El fichero nunca incluye `ports`, `volumes`, `privileged`, `secrets` ni `network_mode`.
- **Un driver puede fijar el comando de su imagen.** El plan admite un `command` que elige el driver del tipo, siempre el mismo y sin texto del usuario. Ningún driver lo usa todavía; los de P07 lo necesitan para algunas imágenes oficiales.
- **El texto del plan se pasa literal a Compose.** Compose sustituiría `${NOMBRE}` por variables del entorno del backend, que incluye contraseñas. El motor escribe cada `$` como `$$`, así que un nodo llamado `${POSTGRES_PASSWORD}` recibe ese texto y no el valor.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| Usar los valores por defecto de Docker (root, todas las capacidades por defecto, escritura) | Un fallo en el backend o en una imagen del experimento daría a un atacante permisos de root dentro del contenedor, que podría usar para intentar salir de él. |
| Docker sin root (*rootless*) o `userns-remap` | Se configuran en el motor de Docker de la máquina, que pertenece al usuario, no a Infracture. Tampoco se comportan igual en Docker Desktop y en Linux. |
| Perfiles propios de seccomp o AppArmor | Dependen del sistema operativo y del motor, y los predeterminados de Docker ya bloquean las llamadas peligrosas. Quitar capacidades y prohibir escalar privilegios cubre el resto de forma portable. |
| Imagen *distroless* para el backend | El motor necesita el CLI de Docker con Compose dentro de la imagen. Alpine con `apk add docker-cli docker-cli-compose` lo instala con paquetes mantenidos. |
| Dejar que el usuario añada puertos o volúmenes a un nodo | Abriría el experimento a la red de la máquina o a sus ficheros. El backend ya llega a los nodos uniéndose a la red de la ejecución. |
| Escapar solo los campos de texto conocidos | Basta con olvidar un campo nuevo para reabrir la fuga. Escapar todo el plan al pasarlo a Compose lo cubre entero. |

Se acepta que el proxy siga corriendo como root, y que, aun con el proxy, el acceso al socket da mucho poder sobre la máquina ([ADR 0013](0013-motor-de-ejecucion-con-docker-compose.md)). También se acepta que una imagen del catálogo que necesite escribir fuera de `/tmp` o más de 384 MB obligará a cambiar estas reglas comunes, porque hoy `confined()` las impone a todos los nodos por igual. A cambio, cada contenedor tiene lo mínimo para funcionar, el experimento no ve el motor ni los secretos de la plataforma, y las medidas no dependen de que un nodo acapare la CPU.

## Aplicación

La imagen y el contenedor del backend se añadieron en el commit `ef6047a`, y el plan endurecido en `c20b1ba` (#86). El escape de `$` es el commit `c0b17b4`, en `literal` de [`ComposeRuntimeEngine`](../../backend/src/main/java/es/codeurjc/infracture/execution/application/ComposeRuntimeEngine.java). Lo comprueban [`ComposeRenderTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ComposeRenderTests.java) (`rendersAStatelessServiceAsTheSameComposeFile`, `givesExperimentsNoPortsVolumesPrivilegesOrSecrets`) y [`ComposeRuntimeEngineIntegrationTests`](../../backend/src/test/java/es/codeurjc/infracture/execution/application/ComposeRuntimeEngineIntegrationTests.java) (`passesTextWithDollarSignsToTheContainerAsWritten`). La prueba de navegador de [`frontend/test/execution`](../../frontend/test/execution) ejecuta un experimento real sobre la plataforma contenedorizada con estas restricciones. El plan y el proxy están descritos en las secciones [9.3](../EXECUTION_ARCHITECTURE.md#93-el-executionplan) y [9.6](../EXECUTION_ARCHITECTURE.md#96-acceso-a-docker-desde-el-backend-contenedorizado) de la arquitectura de ejecución.

## Referencias

- [Docker: seguridad del motor](https://docs.docker.com/engine/security/).
- [OWASP: Docker Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html).
- [Docker Compose: referencia de servicios](https://docs.docker.com/reference/compose-file/services/).
- [Docker Compose: interpolación](https://docs.docker.com/reference/compose-file/interpolation/).
- [Dockerfile: instrucción `USER`](https://docs.docker.com/reference/dockerfile/#user).
