# Diseño de Infracture

Base visual común de toda la aplicación. Versión inicial del 18 de octubre de 2026.

Este documento fija los cimientos: principios, tokens, reglas y las pocas piezas que se repiten en todas partes. El diseño concreto de cada página (layout, cards, listas, tablas, estados vacíos) se decide al diseñar esa página. Cuando una decisión de página se repite en dos o más páginas, se añade aquí como patrón.

El mock de Fase 1 (`../infracture-ui-prototype`) sigue siendo la referencia de pantallas, navegación y anatomía del nodo. Su estética no: se sustituye por la de este documento.

## Principios

1. **El fallo es el único momento llamativo.** La interfaz es neutra y tranquila. Cuando se inyecta un fallo, el componente afectado y su conexión son lo único con color intenso.
2. **El color solo indica estado.** El tipo de componente se reconoce por su icono y su nombre, nunca por un color propio.
3. **Tono y espacio, no líneas.** Las superficies se separan con escalones de tono y huecos. Sin bordes decorativos ni sombras en reposo.
4. **Texto llano.** Cada botón dice lo que hace, cada error explica cómo arreglarlo y cada estado vacío invita a actuar.

Toda la interfaz está en inglés, pensada como una aplicación global. Las traducciones se decidirán más adelante.

## Color

Los colores se definen como tokens con significado. Los componentes usan los tokens, nunca valores sueltos. El tema sigue la preferencia del sistema (`prefers-color-scheme`); no hay selector manual por ahora.

| Token | Claro | Oscuro | Uso |
| --- | --- | --- | --- |
| `ground` | `#E3E6E2` | `#0D1114` | Fondo de la aplicación y del lienzo |
| `surface` | `#F1F3F0` | `#161C20` | Paneles, nodos, métricas |
| `surface-selected` | `#F1F3F0` sobre `ground` | `#222A30` | Opción activa dentro de un control |
| `ink` | `#1E2830` | `#EEF1EF` | Texto principal e iconos |
| `ink-muted` | `#46535D` | `#9AA5AC` | Texto secundario, etiquetas |
| `connection` | `#8C979E` | `#3A464E` | Conexiones del lienzo en reposo |
| `action` | `ink` | `ink` | Fondo del botón principal; su texto usa `surface` en claro y `ground` en oscuro |
| `degraded-fill` | `#E9A93A` | `#E9A93A` | Relleno del nodo y la conexión degradados |
| `degraded-text` | `#8A5A00` | `#F0B954` | Texto e iconos de estado degradado |
| `failed-text` | `#A82B16` | `#F2836B` | Texto e iconos de fallo y error |
| `healthy-text` | `#2E7A4E` | `#6CC18F` | Texto e iconos de estado sano |
| `on-degraded` | `ink` | `ground` | Texto e iconos sobre `degraded-fill` |

El fondo oscuro es un pizarra casi negro, de sala de control, sin llegar al negro puro: así el ámbar del fallo destaca al máximo, el texto claro no produce halo y las superficies se separan por escalones de tono (`ground`, `surface`, `surface-selected`).

Reglas:

- No hay color de marca. La acción principal usa la tinta.
- Ningún estado depende solo del color: siempre lleva icono y palabra.
- Texto normal con contraste mínimo de 4,5:1 y texto grande (24 px o más) de 3:1, en ambos temas.
- Las variantes de un color (hover, tinte, transparencia) se derivan con `color-mix(in oklch, …)`, como indica la skill good-css.

## Tipografía

| Familia | Uso |
| --- | --- |
| Mona Sans | Solo la frase de impacto (`type-hero`), con anchura expandida al 112 %, peso 760 e interletrado del −4 % |
| Atkinson Hyperlegible Next | El resto de la interfaz: títulos, botones, etiquetas y textos |
| Paper Mono | Solo datos técnicos: tiempos, métricas, puertos, identificadores, etiquetas de conexión, configuración y logs |

Las tres tienen licencia OFL. Las cifras de métricas y tiempos usan `font-variant-numeric: tabular-nums`. En Paper Mono se activa el cero con barra (`font-feature-settings: "zero"`) para distinguirlo de la O.

Escala de tamaños:

| Tamaño | Uso |
| --- | --- |
| 12 px | Mínimo absoluto: datos auxiliares, tipo de componente, etiquetas de conexión |
| 13 px | Texto secundario, etiquetas de campo, logs |
| 14–15 px | Texto de interfaz, botones, nombres |
| 17 px | Títulos de panel |
| 28–30 px | Valores de métrica |
| 36 px | Títulos de las páginas de acceso (`type-display`, Atkinson a peso 500, interletrado del −3 %; 32 px en ventanas de menos de 720 px de alto) |
| 40–104 px | Frase principal de las páginas de acceso y la landing (`type-hero`, Mona Sans, interlineado 0,92). Su tamaño depende del ancho de su contenedor (`13cqi`), no de la ventana |

Los títulos de página usan 24 px con peso 650 (`type-page-title`), fijados en las páginas de acceso.

- Mayúsculas solo al inicio de frase. Nunca etiquetas en mayúsculas espaciadas.
- Sin tracking negativo extremo en los títulos.

## Espaciado y forma

- Escala de espaciado: 4, 8, 12, 16, 20 y 24 px. Los huecos entre bloques son mayores que los huecos dentro de un bloque.
- Las superficies se colocan como bloques sobre `ground`, separados por 8 px.
- Radios según jerarquía: 6 px en controles, 8 px en contenedores internos y 12 px en paneles y nodos. Sin formas de píldora.
- El contorno (`outline`) solo se usa para el foco y la selección.

## Estructura general

- **Barra superior**: marca, ruta actual como texto (por ejemplo, `Canvases / Checkout under pressure`), estado de la ejecución y acciones de la pantalla.
- **Espacio de trabajo** (canvas y ejecución): lienzo a la izquierda, panel de acciones a la derecha y telemetría debajo. Los nodos conservan su posición al pasar del canvas a la ejecución.
- **Lienzo**: sin rejilla de fondo; las guías de alineación aparecen solo al arrastrar. Conexiones ortogonales con etiqueta del protocolo.
- **Landing**: experiencia aparte, diseñada con taste-skill a partir de este documento.
- Toda página funciona a 375 px de ancho. Cada página decide cómo se reorganiza.

## Estados

Vocabulario común para nodos, conexiones, eventos y métricas:

| Estado | Color | Icono | Tratamiento en el lienzo |
| --- | --- | --- | --- |
| Sano | `healthy-text` | marca de verificación | Nodo en `surface`, conexión `connection` |
| Degradado | `degraded-fill` / `degraded-text` | triángulo de aviso | Nodo relleno en ámbar, conexión discontinua en ámbar |
| Fallo | `failed-text` | círculo con aspa | Por definir con la primera vista que muestre un fallo |
| Pausado | Por definir | pausa | Por definir |
| Recuperando | Por definir | flecha circular | Por definir |

Un nodo seleccionado lleva un `outline` de 2 px en `ink` separado 3 px.

## Componentes base

Los controles habituales (botones, campos, selects, pestañas, diálogos, menús, avisos) son los de shadcn, con sus tamaños y variantes. Sobre ellos, este documento solo fija:

- **Botón principal**: variante por defecto de shadcn, que toma el color `action`. Uno por zona como máximo.
- **Botón de icono**: siempre con `aria-label`.
- **Campo de formulario**: etiqueta visible encima, nunca solo el placeholder. Debajo, una ayuda que dice la regla real del campo, por ejemplo "3 to 20 letters, numbers or underscores.". Si hay error, el error sustituye a la ayuda y explica cómo arreglarlo, no solo qué falla.
- **Límites**: cada campo lleva `maxLength` con el mismo límite que la entidad del backend. Las reglas complejas, como la política de contraseñas, solo las valida el servidor.
- **Contraseña**: siempre con botón para mostrarla u ocultarla (`InputGroup` de shadcn, con `aria-label` y `aria-pressed`).
- **Envío**: `<Form method="post">` de React Router, nunca un `<form>` sin método, que mandaría los campos en la URL. Mientras se envía, el botón principal se desactiva y muestra `Spinner` y un texto en gerundio ("Signing in…").
- **Sin scroll en formularios cortos**: un formulario de acceso o de alta cabe entero en una ventana de portátil (unos 650 px de alto útil). En ventanas de menos de 720 px de alto se reducen el título y el espacio entre campos. La ayuda solo aparece en los campos con una regla que no se adivina.
- **Errores que aparecen tras enviar**: entran con la clase `enter-soft` (opacidad y 4 px de desplazamiento, 200 ms). Con movimiento reducido, solo opacidad.
- **Mensaje de estado**: componente `Alert` con icono, título y detalle del estado.

Componente propio de Infracture, sin equivalente en shadcn:

- **Nodo**: icono del tipo en un cuadrado de 40 px, tipo (12 px), nombre (15 px), barra de capacidad y métrica en Paper Mono. Sobre `surface`, sin borde ni sombra; en estado degradado, relleno `degraded-fill`.

## Sistema de componentes

La interfaz se construye con **Tailwind CSS** (ya instalado en `frontend/`) y **shadcn/ui** como registro de componentes. shadcn es el estándar de la interfaz: sus componentes se aceptan con su diseño por defecto y solo se personalizan mediante los tokens globales. Un cambio dentro de un componente se decide al diseñar una página, y solo cuando choque claramente con los principios.

### Inicialización

Se ejecuta dentro de `frontend/`, con la plantilla de React Router ya detectada:

- **Base**: Base UI (`--base base`), la opción por defecto de shadcn. Radix solo si hay un motivo concreto.
- **Estilo**: `vega`, el estilo clásico de shadcn. Sus controles usan `rounded-md` y sus cards `rounded-xl`, así que los radios fijados abajo se aplican sin tocar los componentes. `maia` se descartó porque usa formas de píldora (`rounded-4xl`). El estilo fija la geometría de los componentes; los tokens fijan colores, fuentes y radios.
- **Iconos**: Lucide (`lucide-react`), elegido en la inicialización.

### Tokens

Todo se define en `frontend/app/app.css`, el único archivo de estilos globales. Los valores se copian en hex tal como aparecen en este documento; las variantes se derivan con `color-mix(in oklch, …)`.

| Variable shadcn | Token |
| --- | --- |
| `--background` | `ground` |
| `--foreground`, `--card-foreground`, `--popover-foreground`, `--secondary-foreground`, `--accent-foreground` | `ink` |
| `--card`, `--popover`, `--secondary` | `surface` |
| `--muted` | `ground` |
| `--muted-foreground` | `ink-muted` |
| `--accent` | `surface-selected` |
| `--primary` | `action` |
| `--primary-foreground` | `surface` en claro, `ground` en oscuro |
| `--destructive` | `failed-text` |
| `--ring` | `ink` |

Los tokens sin equivalente en shadcn (`connection`, `degraded-fill`, `degraded-text`, `healthy-text`) se declaran como variables propias y se registran en `@theme inline` (por ejemplo, `--color-degraded-fill`), para usarlos como `bg-degraded-fill`.

- **Modo oscuro**: cada token se define una sola vez con `light-dark(claro, oscuro)` y `:root` declara `color-scheme: light dark`, así que el tema sigue la preferencia del sistema. La clase `.theme-dark` fuerza el tema oscuro en una zona concreta; las páginas de acceso la usan entera. La variante `dark:` de Tailwind se activa con `@media (prefers-color-scheme: dark)` o dentro de `.theme-dark`, para que los componentes de shadcn también la respeten.
- **Radios**: se fijan uno a uno en `@theme inline`, sin derivarlos de `--radius`: `--radius-md: 6px` (elementos de menú), `--radius-lg: 6px` (botones, campos, pestañas) y `--radius-xl: 12px` (cards y diálogos). El resto se queda como lo trae shadcn.
- **Fuentes**: `--font-sans` apunta a Atkinson Hyperlegible Next, `--font-display` a Mona Sans y `--font-mono` a Paper Mono, todas alojadas en el propio proyecto. `--font-heading` usa la misma fuente que `--font-sans`.
- **Escala tipográfica**: como utilidades `@utility type-*`, no como claves `--text-*`, para que no choquen con las clases de color.
- Bordes, sombras y foco se quedan como los trae shadcn. Solo cambia el color de `--border` y `--input`, que se deriva de `ink` con `color-mix` (14 % y 24 % en claro, 10 % y 15 % en oscuro) para que los campos se vean sobre `ground` y sobre `surface`.

### Usar los componentes

- Se usan las variantes que ya existen (`variant`, `size`) antes que estilos propios. `className` se usa solo para layout.
- Si un componente se modifica, se editan sus variantes `cva` y se mantienen los nombres de variante.
- Las alturas de control cambian juntas: si cambia la del botón, también la de campos, selects, pestañas y toggles.

Animaciones: primero las transiciones de CSS y las utilidades que shadcn ya trae para entrar y salir. Una librería como Motion se añade solo cuando una interacción concreta lo necesite.

## Interacción y movimiento

- El foco siempre es visible: en los componentes de shadcn, su anillo con `--ring`; en lo propio, `:focus-visible` con `outline`.
- El hover solo en dispositivos con puntero (`@media (hover: hover) and (pointer: fine)`). Todo lo pulsable tiene estado `:active`.
- El movimiento solo explica un cambio causado por el usuario o por la ejecución. Se animan `transform` y `opacity`, y siempre dentro de `prefers-reduced-motion: no-preference`.
- Las decisiones de movimiento se toman con las skills de Emil Kowalski (`animate`, `review-animations`).

## Qué no hacemos

Se aplica a lo que diseñamos nosotros: páginas, composición y componentes propios. Los componentes de shadcn mantienen su diseño estándar aunque coincidan en algo con esta lista.

- Degradados decorativos, halos, brillos y efecto cristal (`backdrop-filter`).
- Bordes de 1 px para separar y sombras en elementos en reposo.
- Formas de píldora en botones, badges o contenedores.
- Monospace fuera de los datos técnicos.
- Etiquetas pequeñas sobre los títulos, mayúsculas espaciadas y numeraciones 01/02/03 que no sean secuencias reales.
- Datos unidos con puntos medios (`A · B · C`) y flechas añadidas al texto de botones o enlaces.
- Una palabra resaltada dentro de un titular.
- Un color de marca brillante sobre fondo oscuro, o crema con terracota. El único color intenso es el del estado.
- Emoji y textos de relleno.

## Decisiones abiertas

- Colores de fallo (relleno), pausado y recuperando.
- Contraste medido con herramienta de los tokens en ambos temas.
- Presencia y forma del Lab Mentor (el mock usaba una mascota).

## Herramientas

- **frontend-design**: decisiones de identidad y diseño de cada página.
- **shadcn**: añadir, componer y adaptar componentes del registro.
- **good-css**: antes de escribir o revisar CSS.
- **Skills de Emil Kowalski**: componentes, interacción y movimiento; `break-ui` para probar cada pantalla con datos extremos.
- **taste-skill**: solo para la landing.
- Lienzo de comparación en Claude Design (privado): https://claude.ai/artifact/VHMZXN9e9ryACUAUHcTnFY
