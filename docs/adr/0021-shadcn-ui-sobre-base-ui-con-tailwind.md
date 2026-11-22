---
status: accepted
date: 2026-10-18
---

# shadcn/ui sobre Base UI con Tailwind

## Contexto

Antes de diseñar el login y el registro hacía falta una base visual común para toda la aplicación. Se necesitaban botones, campos, selects, diálogos y avisos accesibles con teclado y lector de pantalla, con modo claro y oscuro, y con los tokens de color y tipografía de [`DESIGN.md`](../../DESIGN.md). La propuesta de la Fase 1 nombraba Tailwind CSS y shadcn/ui sobre Radix Primitives (entrada AI-2026-07-28-004 del [registro de uso de IA](../AI_USAGE.md)). Al inicializar shadcn en octubre, su CLI ya ofrecía como opción por defecto Base UI, otra librería de primitivas sin estilo.

El lienzo usa React Flow ([ADR 0014](0014-react-flow-para-el-lienzo.md)), con nodos que son componentes React. Por eso conviene que los controles de los nodos y los del resto de páginas salgan del mismo sistema.

## Decisión

- **Tailwind CSS 4** para los estilos. Todos los tokens están en [`app/app.css`](../../frontend/app/app.css), el único fichero de estilos globales, con `light-dark()` para los dos modos.
- **shadcn/ui** como registro de componentes. Los componentes se copian al repositorio, en [`app/components/ui`](../../frontend/app/components/ui), y se aceptan con su diseño por defecto; solo cambian los tokens.
- **Base UI como primitivas** (`style: base-vega` en [`components.json`](../../frontend/components.json)). Radix se usaría solo si un componente concreto lo necesitara.
- **Estilo `vega` e iconos de Lucide.** Los radios de `vega` coinciden con los de `DESIGN.md`. `maia` se descartó porque usa formas de píldora.

## Motivo y consecuencias

| Alternativa | Por qué no |
| --- | --- |
| shadcn/ui sobre Radix (propuesta de la Fase 1) | Funcionaría igual. Base UI es la opción por defecto de shadcn desde que se instaló, y la mantiene parte del equipo que creó Radix. Elegir la opción por defecto evita configurar a mano cada componente que se añada con el CLI. |
| Una librería de componentes cerrada (MUI, Chakra UI, Ant Design) | Trae su propio sistema de estilos y temas. Ajustarla a los tokens de `DESIGN.md` obliga a sobrescribir estilos, y su código no está en el repositorio para adaptarlo. |
| Componentes propios desde cero | Rehacer el foco, el teclado y ARIA de selects, menús y diálogos lleva mucho trabajo y es fácil equivocarse. Las primitivas ya lo resuelven. |
| CSS Modules o CSS-in-JS en lugar de Tailwind | shadcn está escrito con clases de Tailwind. Con otro sistema, cada componente copiado habría que traducirlo. |

Se acepta que los componentes copiados son código propio: si shadcn los mejora, hay que traer los cambios a mano. También se acepta depender de una librería joven (Base UI 1.x). A cambio, cada control se puede leer y adaptar en el repositorio, y su accesibilidad la aportan las primitivas, no código hecho a mano.

## Aplicación

Concreta ID02 de la [spec](../PHASE_3_SPEC.md#arquitectura-y-vocabulario). Se inicializó el 18 de octubre de 2026 (entrada AI-2026-10-18-061 del registro de IA) y entró en el commit `7f915f9`. Las reglas de uso están en la sección «Sistema de componentes» de [`DESIGN.md`](../../DESIGN.md). Las dependencias son `@base-ui/react`, `tailwindcss` y `lucide-react` en [`frontend/package.json`](../../frontend/package.json). Las pruebas de rutas de [`frontend/test/routes`](../../frontend/test/routes) y los recorridos de Playwright de [`frontend/test/system`](../../frontend/test/system) buscan los controles por rol y nombre accesible, así que comprueban también su accesibilidad.

## Referencias

- [shadcn/ui: documentación](https://ui.shadcn.com/docs).
- [Base UI: documentación](https://base-ui.com/react/overview/quick-start).
- [Tailwind CSS: documentación](https://tailwindcss.com/docs).
- [WAI-ARIA Authoring Practices Guide](https://www.w3.org/WAI/ARIA/apg/).
