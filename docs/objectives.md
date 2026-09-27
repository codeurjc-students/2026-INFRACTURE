# Objetivos

## Objetivos funcionales

El objetivo funcional es ofrecer un laboratorio web local que permita pasar de una arquitectura declarativa a una ejecución observable y repetible, aplicar fallos controlados y relacionar el resultado con las dependencias del sistema.

- Permitir consultar contenido público y registrarse o iniciar sesión con una cuenta propia.
- Permitir que los usuarios registrados creen proyectos privados, publiquen voluntariamente sus proyectos y consulten proyectos públicos.
- Permitir diseñar escenarios conectando componentes de un catálogo controlado.
- Validar y ejecutar los escenarios en Docker dentro del equipo local.
- Permitir detener, reiniciar, pausar, reanudar e introducir latencia controlada en los componentes compatibles.
- Mostrar estados, eventos, logs, métricas, gráficos e historial de ejecuciones.
- Permitir subir avatares y portadas, y conservar los resultados asociados al usuario y al proyecto.
- Ofrecer una comunidad de proyectos públicos con perfiles de usuario, estrellas, seguimiento y clonación privada.
- Ofrecer laboratorios guiados, objetivos, intentos, puntuación y un profesor de IA consultivo.
- Mostrar los conceptos explorados al completar un laboratorio y reunirlos, sin duplicados, en el perfil del usuario.

## Objetivos técnicos

El objetivo técnico es construir una aplicación web mantenible y reproducible, con una interfaz visual específica, una API REST y un motor de ejecución local seguro.

- Desarrollar la interfaz como una SPA con React, TypeScript, Vite y React Router, utilizando Tailwind CSS, shadcn/ui, React Flow, Motion for React, Lucide React y Recharts para las gráficas de métricas.
- Implementar con Java 25 LTS y Spring Boot una API REST versionada y una arquitectura de monolito modular.
- Persistir las entidades de dominio en PostgreSQL mediante Spring Data JPA, utilizar `jsonb` para snapshots y configuraciones variables cuando aporte valor, y versionar el esquema con migraciones de Flyway.
- Ejecutar la plataforma localmente con Docker Compose, controlar Docker Engine desde el backend mediante docker-java encapsulado detrás de una interfaz propia, implementar el Load Generator sobre Grafana k6 y gestionar la latencia reproducible con Toxiproxy a través de su API HTTP.
- Utilizar un catálogo cerrado de imágenes y configuraciones para evitar que el usuario introduzca comandos o imágenes arbitrarias.
- Utilizar MinIO localmente para almacenar avatares, portadas e iconos de las plantillas del catálogo.
- Utilizar Spring Security y JWT para autenticación y autorización, incluyendo control por roles y propiedad de los recursos.
- Utilizar Server-Sent Events (SSE) para actualizar estados, eventos y logs resumidos durante una ejecución.
- Integrar el profesor de IA mediante una API externa detrás de una interfaz independiente del proveedor; la API y el proveedor concretos quedan pendientes de selección.
- Aplicar una estrategia de pruebas con JUnit, Spring Boot Test, Mockito, REST Assured y Testcontainers en el backend; Vitest y React Testing Library en el frontend; y Playwright para las pruebas de sistema. Automatizar mediante GitHub Actions la integración y la entrega continuas, la cobertura mínima exigida, el análisis estático con SonarQube Cloud, la construcción y la publicación de imágenes Docker y paquetes versionados.
