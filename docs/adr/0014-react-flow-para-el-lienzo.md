---
status: accepted
date: 2026-10-31
---

# React Flow para el lienzo

## Contexto

El lienzo del escenario es la pantalla central de Infracture: en él se colocan los nodos, se configuran con formularios y, desde #80, se conectan por sus puertos. La spec ya nombraba React Flow como elección (ID02, U01), pero sin dejar escrito por qué ni frente a qué alternativas. Antes de instalarla en #79 se compararon las librerías de lienzo disponibles contra lo que necesita Infracture:

- nodos tipados que sean componentes React, con el mismo sistema visual que el resto de la aplicación (shadcn/ui y Tailwind);
- puertos con nombre por nodo y validación al soltar una flecha, porque cada puerto pide una capacidad ([ADR 0011](0011-puertos-con-nombre-que-piden-capacidades.md));
- estado controlado desde fuera, porque el grafo vive en el estado de la página y se guarda entero con su revisión ([ADR 0009](0009-guardado-del-grafo-completo-con-revision-esperada.md));
- teclado, foco y pantallas táctiles;
- modo solo lectura para los grafos públicos (#83);
- una licencia que permita distribuir Infracture como imagen instalable, sin clave de producción ni marca de agua;
- mantenimiento activo.

El tamaño no es un criterio: un escenario tiene como mucho 50 nodos.

## Decisión

Usar **React Flow** (`@xyflow/react`, licencia MIT) para el lienzo del escenario y para la vista de solo lectura de los grafos públicos.

- Cada tipo de componente se dibuja con un nodo propio de React.
- Cada puerto del perfil es un `Handle` con su nombre como `id`, y `isValidConnection` consulta el contrato para aceptar solo destinos que ofrezcan la capacidad pedida.
- El estado de los nodos lo controla la página (`nodes` y `onNodesChange`); React Flow dibuja y comunica los cambios, pero no decide permisos ni reglas de validación.

## Motivo y consecuencias

Datos consultados en npm el 31 de octubre de 2026.

| Alternativa | Licencia | Por qué no |
| --- | --- | --- |
| GoJS | Comercial | Exige licencia de pago para producción. |
| tldraw | Propia, con clave | Exige clave de licencia para producción. Además es una pizarra infinita, no un editor de grafos con puertos. |
| JointJS (`@joint/core`) | MPL-2.0; JointJS+ comercial | Muy completa en diagramas, pero sus nodos no son componentes React nativos y mantiene su propio modelo de datos, que habría que sincronizar con el nuestro. |
| AntV X6 | MIT | Mismo problema de modelo propio y nodos React mediante un envoltorio; buena parte de la documentación y la comunidad están en chino. |
| Rete.js | MIT | Orientada a editores de flujo de datos que se ejecutan en el navegador; sin versiones desde junio de 2025. |
| maxGraph (sucesor de mxGraph) | Apache-2.0 | Sigue en versión 0.x y su API es imperativa, ajena a React. |
| Excalidraw | MIT | Pizarra de dibujo a mano alzada: no ofrece nodos con formularios ni puertos. |
| Cytoscape.js | MIT | Pensada para visualizar y analizar grafos; dibuja en canvas y sus nodos no son componentes editables. |
| react-diagrams (projectstorm) | MIT | Sin versiones desde febrero de 2024. |

React Flow es la única que cumple todos los requisitos a la vez sin trabajo extra, y es con diferencia la más usada y mantenida del grupo, lo que reduce el riesgo de quedarse sin documentación o sin soporte.

Se acepta depender de una librería específica de React: si el frontend cambiara de framework, el lienzo tendría que reescribirse. Las funciones de pago de React Flow (React Flow Pro) son ejemplos y plantillas, no se necesitan.

Se revisaría la decisión si un requisito futuro pidiera miles de nodos con renderizado en WebGL o diagramas formales muy especializados, como UML o BPMN. Ninguno está en el alcance de 0.1.0.

## Aplicación

Se instala en el frontend de #79, que dibuja los nodos con su inspector. #80 añade los puertos como `Handle` y la validación al conectar, y #83 reutiliza el lienzo en modo solo lectura. Concreta ID02 de la [spec](../PHASE_3_SPEC.md) y U01 del [alcance](../PHASE_3_SCOPE.md).
