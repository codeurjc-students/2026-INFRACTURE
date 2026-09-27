# Estado del arte

Para definir el alcance y detectar posibles mejoras se han estudiado aplicaciones con funcionalidades similares. El análisis preliminar incluye cuatro referencias:

| Aplicación o familia | Qué ofrece | Mejora que se estudiará en Infracture |
| --- | --- | --- |
| [Docker Desktop](https://docs.docker.com/desktop/) y [Portainer](https://docs.portainer.io/) | Gestión visual de contenedores, redes, logs y recursos. | Orientar la interfaz a experimentos reproducibles y al análisis de fallos, no solo a la administración de contenedores. |
| [GNS3](https://docs.gns3.com/docs) | Diseño y ejecución visual de topologías de red. | Aplicar una interacción basada en nodos y conexiones a servicios de software ejecutados en Docker. |
| [Killercoda](https://killercoda.com/about) y [Play with Docker](https://training.play-with-docker.com/) | Laboratorios guiados y entornos temporales para aprender. | Combinar retos guiados con la posibilidad de crear y guardar escenarios propios. |
| [Chaos Mesh](https://chaos-mesh.org/docs/) y [LitmusChaos](https://docs.litmuschaos.io/) | Inyección de fallos y experimentos de resiliencia, principalmente en Kubernetes. | Ofrecer una primera experiencia local, segura y comprensible sobre un catálogo acotado de contenedores. |

El estudio muestra que las herramientas de administración, los laboratorios educativos y las plataformas de *chaos engineering* suelen resolver problemas distintos. Infracture propone unir sus ideas principales en un flujo local: **diseñar, ejecutar, romper y entender**. La diferenciación estará en conectar el canvas visual, los fallos controlados, las evidencias de ejecución y el aprendizaje guiado dentro de la misma aplicación.
