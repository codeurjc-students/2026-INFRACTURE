---
status: accepted
date: 2026-10-30
---

# Motor de ejecución con Docker Compose

## Contexto

La spec fijaba `docker-java` para crear los recursos de cada experimento y describía el arranque como una secuencia de trece pasos. Cada paso guardaba su progreso para poder recuperarse si el backend se caía. Además, faltaba decidir cómo llega el backend a Docker cuando él mismo se ejecuta en un contenedor de la distribución (C08, ID23).

Un experimento es justo lo que Docker Compose sabe describir y arrancar: servicios, una red, comprobaciones de salud, orden por dependencias y etiquetas. Escribir ese orquestador a mano con `docker-java` es reimplementar Compose.

## Decisión

- **Plan con forma Compose.** El backend compila el snapshot a un `ExecutionPlan` con la forma de un fichero Compose: servicios, red, salud, `depends_on` con `condition: service_healthy`, límites y etiquetas. El plan se guarda en el snapshot, sin secretos, como evidencia de lo que se ejecutó.
- **Aplicado con el CLI.** El plan se aplica con `docker compose -p <proyecto> up -d --wait` y se retira con `docker compose -p <proyecto> down -v`. Cada `Execution` es un proyecto Compose propio.
- **Un bucle de reconciliación.** `ExecutionReconciler` compara la fase deseada de cada ejecución con la real y actúa hasta igualarlas.
- **Una sola forma de terminar.** `Stop`, el límite de tiempo, un fallo y el borrado piden lo mismo: la fase `STOPPED`. El motor retira por etiqueta todo lo que pertenece a esa ejecución y lo repite hasta que no quede nada. No se guarda en qué paso iba el arranque, porque el [ADR 0005](0005-recuperacion-sin-reanudacion-automatica.md) ya excluye reanudarlo.
- **Acceso a Docker mediante un proxy.** El backend usa el Docker de la máquina a través de `docker-socket-proxy`. Solo el proxy monta `/var/run/docker.sock` y limita las operaciones permitidas. El backend recibe `DOCKER_HOST=tcp://docker-proxy:2375`. Los contenedores del experimento nunca tienen acceso a Docker.
- **Secretos fuera del plan guardado.** El plan guardado solo contiene referencias a credenciales. Las contraseñas temporales se generan al aplicarlo y no se guardan ni se muestran.
- **Comprobación previa.** P06 empieza con una prueba corta en macOS, Windows y Linux. Debe confirmar tres cosas: que el CLI funciona desde el backend contenedorizado, que el backend puede unirse a la red de cada ejecución y que la limpieza por etiqueta no toca otros contenedores. Si el CLI falla, el mismo plan se aplica con `docker-java`; solo cambia el adaptador.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| `docker-java` paso a paso (spec original) | Reimplementa el orden por salud, la espera y la limpieza que Compose ya resuelve. Se mantiene como alternativa del adaptador. |
| Kubernetes (k3s, kind) | Exige un clúster en el ordenador del usuario. Es desproporcionado para una instancia local con una sola ejecución activa. |
| Docker Swarm | Su modo *stack* no admite `depends_on` con espera de salud y necesita iniciar un *swarm*. |
| Testcontainers | Está pensado para pruebas y para la vida de la JVM que lo lanza, no para recursos que sobreviven a un reinicio del backend. |
| Docker in Docker | Necesita `--privileged`, que también equivale a ser administrador de la máquina. Además duplica el motor y la caché de imágenes, y deja las redes del experimento fuera del alcance del backend. |
| Montar el socket directamente en el backend | Daría al backend acceso total a Docker. El proxy reduce lo que puede pedir. |

Se acepta depender del binario `docker compose` dentro de la imagen del backend y que el socket, aun con proxy, equivalga a mucho poder sobre la máquina. Por eso la limpieza filtra siempre por las etiquetas de Infracture. A cambio, el motor es pequeño, el plan se puede leer y probar sin Docker, y en el futuro podría traducirse a otros destinos, como Kubernetes, sin cambiar el modelo.

## Aplicación

Sustituye la elección de `docker-java` en ID22 de la [spec](../PHASE_3_SPEC.md#ejecución-observación-y-eliminación) y concreta ID23 y C08. Mantiene el [ADR 0004](0004-ejecuciones-ligadas-a-una-revision-inmutable.md) y el [ADR 0005](0005-recuperacion-sin-reanudacion-automatica.md). El diseño del motor está en la [arquitectura de ejecución](../EXECUTION_ARCHITECTURE.md#9-el-motor-de-ejecución).
