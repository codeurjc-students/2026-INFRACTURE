# Diseño de Infracture

Guía de marca de Infracture. Versión del 20 de octubre de 2026 con la identidad térmica, que sustituye a la inicial del 18 de octubre.

## Qué fija este documento y qué no

Este documento fija la **identidad** de Infracture: lo que debe ser igual en toda la aplicación para que se reconozca como un único producto. Son los colores y su significado, las familias tipográficas, las formas, el vocabulario de estados, la accesibilidad y el sistema de componentes. Todo lo que aparece aquí es obligatorio.

Este documento **no** fija la composición de las páginas: la estructura, la jerarquía, el layout, los tamaños de los títulos, el uso de imágenes o de recursos visuales. Cada página se diseña desde cero a partir de su trabajo y su contenido, siguiendo [Diseñar una página](#diseñar-una-página). Que una página ya diseñada use una estructura no la convierte en norma para las demás.

## Principios

1. **El fallo es el único momento llamativo.** La interfaz se comporta como una cámara térmica que vigila la arquitectura: en reposo todo está frío y neutro, y el calor (ámbar y después infrarrojo) aparece solo cuando algo se degrada o falla.
2. **El color solo indica estado.** El tipo de componente se reconoce por su icono y su nombre, nunca por un color propio. El único acento de la marca, Infrarrojo, es también el color del fallo: si algo es infrarrojo, se ha roto o lo vas a romper.
3. **La letra se fractura con el sistema.** Los titulares y lo que ha fallado usan la voz afilada de la tipografía; el resto de la interfaz, la voz roma.
4. **Texto llano.** Cada botón dice lo que hace, cada error explica cómo arreglarlo y cada estado vacío invita a actuar. Sin emoji ni textos de relleno.

Toda la interfaz está en inglés, pensada como una aplicación global. Las traducciones se decidirán más adelante.

## Color

Los colores se definen como tokens con significado. Los componentes usan los tokens, nunca valores sueltos. El tema sigue la preferencia del sistema (`prefers-color-scheme`); no hay selector manual por ahora.

La paleta se diseñó en OKLCH. Los neutros llevan un sesgo violeta muy bajo (tono 285), el frío de una imagen térmica, en lugar del gris azulado habitual en herramientas técnicas. El claro recuerda a la cerámica: gris frío de fondo y superficies casi blancas. El oscuro es una noche térmica: casi negro con un punto violeta, sin llegar al negro puro para que el texto claro no produzca halo.

| Token | Claro | Oscuro | Uso |
| --- | --- | --- | --- |
| `ground` | `#EDEDF2` | `#0F0F19` | Fondo de la aplicación y del lienzo |
| `surface` | `#FBFBFD` | `#181823` | Paneles, nodos, métricas, campos |
| `surface-selected` | `#E2E2E9` | `#252532` | Opción activa dentro de un control, hover, nivel frío de la escala térmica |
| `ink` | `#1A1A23` | `#F3F3F7` | Texto principal e iconos |
| `ink-muted` | `#595A64` | `#A6A7B0` | Texto secundario, etiquetas, estado pausado |
| `connection` | `#858590` | `#62626F` | Conexiones del lienzo en reposo |
| `action` | `ink` | `ink` | Fondo del botón principal; su texto usa `surface` |
| `healthy-text` | `#007654` | `#6BD2A8` | Texto e iconos de estado sano |
| `recovering-text` | `#7444B4` | `#BE9DF7` | Texto, iconos y borde de estado recuperando |
| `degraded-text` | `#9A5A00` | `#FBC456` | Texto e iconos de estado degradado |
| `failed-text` | `#CA0F4D` | `#FF6C86` | Texto e iconos de fallo y error |
| `degraded-fill` | `#F9B73F` | `#F9B73F` | Relleno del nodo y la conexión degradados |
| `failed-fill` | `#E01757` | `#E01757` | Infrarrojo: relleno del nodo fallido, conexión rota y acción de inyectar un fallo |
| `on-degraded` | `#1A1A23` | `#1A1A23` | Texto e iconos sobre `degraded-fill` |
| `on-failed` | `#FFFFFF` | `#FFFFFF` | Texto e iconos sobre `failed-fill` |

Contraste medido con la fórmula de WCAG 2.2, sobre `ground` de cada tema (claro / oscuro):

| Par | Contraste |
| --- | --- |
| `ink` | 14,8:1 / 17,2:1 |
| `ink-muted` | 5,86:1 / 7,96:1 |
| `connection` (gráfico) | 3,13:1 / 3,17:1 |
| `healthy-text` | 4,84:1 / 10,4:1 |
| `recovering-text` | 5,57:1 / 8,48:1 |
| `degraded-text` | 4,69:1 / 11,9:1 |
| `failed-text` | 4,88:1 / 7,02:1 |
| `on-degraded` sobre `degraded-fill` | 9,76:1 |
| `on-failed` sobre `failed-fill` | 4,74:1 |

Reglas:

- La acción principal usa la tinta. Infrarrojo solo es el color de un botón cuando ese botón provoca un fallo ("Inject fault").
- Infrarrojo aparece solo en un fallo, en la acción que lo provoca y en momentos de marca (landing, página de error). Nunca como decoración.
- Ningún otro color intenso fuera de los estados: ni colores por tipo de componente, ni degradados decorativos, ni brillos de fondo.
- Ningún estado depende solo del color: siempre lleva icono y palabra.
- Las variantes de un color (hover, tinte, transparencia) se derivan con `color-mix(in oklch, …)`, como indica la skill good-css.

Dentro de estas reglas, cada página decide cómo usa la paleta: qué tono domina, si usa el tema oscuro en una zona (`.theme-dark`) o cómo separa sus superficies.

### Escala térmica

Escala ordinal para magnitudes en visualizaciones: mapas de calor de latencia o errores e historiales de ejecuciones. Se inspira en la paleta "ironbow" de las cámaras térmicas, pero su nivel más caliente es Infrarrojo, para que "lo más caliente" signifique siempre lo mismo que el fallo.

| Nivel | Claro | Oscuro |
| --- | --- | --- |
| 0, frío | `surface-selected` | `surface-selected` |
| 1 | `#B9B0D6` | `#33285A` |
| 2 | `#7444B4` | `#5A2C97` |
| 3 | `#B20D8F` | `#B20D8F` |
| 4, caliente | `#E01757` | `#E01757` |

- Solo para magnitudes, nunca para estados ni para decorar.
- Se usa por niveles, en celdas o barras, no como degradado continuo.
- Siempre con una leyenda que diga qué es frío y qué es caliente.

## Tipografía

| Familia | Papel |
| --- | --- |
| Geologica | Familia única de titulares e interfaz. Variable en peso, Sharpness (`SHRP`) y Cursive (`CRSV`) |
| JetBrains Mono | Solo datos técnicos: tiempos, métricas, puertos, identificadores, etiquetas de conexión, configuración y logs |

Las dos tienen licencia OFL y se alojan en el propio proyecto con Fontsource (`@fontsource-variable/geologica/full.css`, que incluye todos los ejes, y `@fontsource-variable/jetbrains-mono`). No se añaden otras familias.

Geologica tiene dos voces:

- **Voz afilada**: `font-variation-settings: "SHRP" 100, "CRSV" 1`. Remates cortados en diagonal y `a` de un solo piso, como el logotipo. Se usa en titulares, frases de marca y el nombre de un componente que ha fallado.
- **Voz de interfaz**: `"SHRP" 0, "CRSV" 0`. Todo lo demás: títulos de panel, botones, etiquetas y textos.

Reglas:

- Las cifras de métricas y tiempos usan `font-variant-numeric: tabular-nums`.
- En JetBrains Mono se activa el cero con barra (`font-feature-settings: "zero"`) para distinguirlo de la O.
- 12 px es el tamaño mínimo de cualquier texto.
- Interletrado negativo solo a partir de 24 px. Nunca etiquetas en mayúsculas espaciadas.

Los tamaños, pesos e interletrados los decide cada página. Como referencia, las maquetas usan una escala de cuarta justa (×1,333) a partir de 15 px y pesos intermedios (560, 620, 680), posibles por ser una fuente variable. `app.css` ofrece utilidades ya usadas (`type-hero`, `type-display`, `type-page-title`, `type-title`, `type-metric`, `type-body`, `type-small`, `type-caption`) que se pueden reutilizar o no.

## Forma

Los radios forman parte de la identidad porque los comparten todos los componentes: 6 px en controles, 8 px en contenedores internos y 12 px en paneles, cards, diálogos y nodos. Sin formas de píldora.

Las superficies se separan por tono y por un borde de 1 px derivado de la tinta. Las sombras se reservan para lo que flota sobre la página: menús, popovers y diálogos.

Las conexiones del lienzo son trazos de 1,5 px en reposo y de 2,5 px cuando están degradadas o rotas.

El `outline` se reserva para el foco y la selección, para que nunca se confunda con un borde.

## Estados

Vocabulario común para nodos, conexiones, eventos y métricas:

| Estado | Color | Icono | Tratamiento en el lienzo |
| --- | --- | --- | --- |
| Sano | `healthy-text` | marca de verificación | Nodo en `surface`, sin relleno; conexión `connection` |
| Degradado | `degraded-fill` / `degraded-text` | triángulo de aviso | Nodo relleno en `degraded-fill` con texto `on-degraded`; conexión continua en `degraded-fill` |
| Fallo | `failed-fill` / `failed-text` | círculo con aspa | Nodo relleno en `failed-fill` con texto `on-failed` y nombre en voz afilada; conexión discontinua en `failed-fill` |
| Recuperando | `recovering-text` | flecha circular | Nodo en `surface` con borde discontinuo de 1,5 px en `recovering-text`; conexión `connection` |
| Pausado | `ink-muted` | pausa | Nodo en `surface` con texto `ink-muted`; sin color: un componente parado está frío |

Un elemento seleccionado lleva un `outline` de 2 px en `ink` separado 3 px.

## Accesibilidad e interacción

- Texto normal con contraste mínimo de 4,5:1 y texto grande (24 px o más) de 3:1, en ambos temas. Los gráficos necesarios para entender el contenido, como las conexiones, de 3:1.
- El foco siempre es visible: en los componentes de shadcn, su anillo con `--ring`; en lo propio, `:focus-visible` con `outline`.
- El movimiento solo explica un cambio causado por el usuario o por la ejecución. El cambio de temperatura de un nodo es el único momento orquestado (unos 400 ms, salida suave); las respuestas a acciones duran entre 120 y 200 ms. Se animan `transform`, `opacity` y colores, y siempre dentro de `prefers-reduced-motion: no-preference`. Con movimiento reducido, como mucho un cambio de opacidad.
- El hover solo en dispositivos con puntero (`@media (hover: hover) and (pointer: fine)`). Todo lo pulsable tiene estado `:active`.
- Toda página funciona a 375 px de ancho y con el texto ampliado.
- Los botones de icono llevan `aria-label`.

## Sistema de componentes

La interfaz se construye con **Tailwind CSS** y **shadcn/ui** como registro de componentes. Los controles habituales (botones, campos, selects, pestañas, diálogos, menús, avisos) son los de shadcn, aceptados con su diseño por defecto y personalizados solo mediante los tokens globales.

### Inicialización

Se ejecuta dentro de `frontend/`, con la plantilla de React Router ya detectada:

- **Base**: Base UI (`--base base`), la opción por defecto de shadcn. Radix solo si hay un motivo concreto.
- **Estilo**: `vega`. Sus controles usan `rounded-md` y sus cards `rounded-xl`, así que los radios de este documento se aplican sin tocar los componentes. `maia` se descartó porque usa formas de píldora.
- **Iconos**: Lucide (`lucide-react`).

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
| `--primary-foreground` | `surface` |
| `--destructive` | `failed-text` |
| `--ring` | `ink` |

Los tokens sin equivalente en shadcn (`connection`, `healthy-text`, `recovering-text`, `degraded-fill`, `degraded-text`, `failed-fill`, `on-degraded`, `on-failed` y los niveles `heat-1` a `heat-4` de la escala térmica) se declaran como variables propias y se registran en `@theme inline` (por ejemplo, `--color-failed-fill`), para usarlos como `bg-failed-fill`.

- **Modo oscuro**: cada token se define una sola vez con `light-dark(claro, oscuro)` y `:root` declara `color-scheme: light dark`. La clase `.theme-dark` fuerza el tema oscuro en una zona concreta. La variante `dark:` de Tailwind se activa con `@media (prefers-color-scheme: dark)` o dentro de `.theme-dark`, para que los componentes de shadcn también la respeten.
- **Radios**: se fijan uno a uno en `@theme inline`, sin derivarlos de `--radius`: `--radius-md: 6px` (elementos de menú), `--radius-lg: 6px` (botones, campos, pestañas) y `--radius-xl: 12px` (cards y diálogos). El resto se queda como lo trae shadcn.
- **Fuentes**: `--font-sans`, `--font-heading` y `--font-display` apuntan a Geologica y `--font-mono` a JetBrains Mono. La voz afilada la ponen la clase `font-display` y las utilidades de titular (`type-hero`, `type-headline`), que fijan sus `font-variation-settings` en un solo sitio; nunca se repiten los valores en cada componente. `font-mono` activa el cero con barra.
- **Escala tipográfica**: como utilidades `@utility type-*`, no como claves `--text-*`, para que no choquen con las clases de color.
- Foco como lo trae shadcn. El color de `--border` y `--input` se deriva de `ink` con `color-mix` (14 % y 24 % en claro, 10 % y 15 % en oscuro).

### Usar los componentes

- Se usan las variantes que ya existen (`variant`, `size`) antes que estilos propios. `className` se usa para layout.
- Si un componente se modifica, se editan sus variantes `cva` y se mantienen los nombres de variante.
- Las alturas de control cambian juntas: si cambia la del botón, también la de campos, selects, pestañas y toggles.
- Animaciones: primero las transiciones de CSS y las utilidades que shadcn ya trae. Una librería como Motion se añade solo cuando una interacción concreta lo necesite.

### Formularios

Son comportamiento, no composición, y se aplican igual en todas las páginas:

- **Etiqueta visible** en cada campo, nunca solo el placeholder. Si el campo tiene una regla que no se adivina, una ayuda dice la regla real, por ejemplo "3 to 20 letters, numbers or underscores.". Si hay error, el error sustituye a la ayuda y explica cómo arreglarlo.
- **Límites**: cada campo lleva `maxLength` con el mismo límite que la entidad del backend. Las reglas complejas, como la política de contraseñas, solo las valida el servidor.
- **Contraseña**: siempre con botón para mostrarla u ocultarla (`InputGroup` de shadcn, con `aria-label` y `aria-pressed`).
- **Envío**: `<Form method="post">` de React Router, nunca un `<form>` sin método, que mandaría los campos en la URL. Mientras se envía, el botón principal se desactiva y muestra `Spinner` y un texto en gerundio ("Signing in…").
- **Mensaje de estado**: componente `Alert` con icono, título y detalle.

## Diseñar una página

La composición de cada página se diseña desde cero:

1. Partir del trabajo de la página y de su contenido real: qué viene a hacer el usuario y qué tiene que ver primero.
2. Mirar como **referencia** el mock de la Fase 1 (`../infracture-ui-prototype`, capturas en `output/playwright/v2`) para pantallas, navegación, contenido y anatomía del nodo, y las [maquetas de la identidad térmica](#herramientas) para ver cómo se aplican color, tipografía y estados. Ninguno de los dos es una plantilla: su estructura se puede seguir, cambiar o descartar.
3. No copiar por defecto la estructura de otra página de Infracture. Si dos páginas acaban pareciéndose, que sea porque su trabajo es parecido.
4. Proponer la composición como prototipo antes de implementarla, usando las skills de diseño.
5. Comprobar que cumple este documento: color solo para estado, Infrarrojo solo para el fallo, familias, voces y radios, accesibilidad y componentes de shadcn.

## Decisiones abiertas

- Tema de las páginas de acceso: hoy fuerzan el oscuro (`.theme-dark`) mientras el resto sigue al sistema.
- Uso del isotipo en momentos de marca, por ejemplo dejando ver Infrarrojo a través de su grieta.
- Presencia y forma del Lab Mentor (el mock usaba una mascota).

## Herramientas

- **frontend-design**: composición y dirección visual de cada página, incluida su lista de patrones genéricos que conviene evitar.
- **shadcn**: añadir, componer y adaptar componentes del registro.
- **good-css**: antes de escribir o revisar CSS.
- **Skills de Emil Kowalski**: componentes, interacción y movimiento (`animate`, `review-animations`); `break-ui` para probar cada pantalla con datos extremos.
- **taste-skill**: landing y páginas de presentación.
- **Ajustar la paleta**: [OKLCH Color Picker](https://oklch.com/) para retocar un token, [Harmonizer](https://evilmartians.com/opensource/harmonizer) y [Leonardo](https://leonardocolor.io/) para generar escalas con contraste constante. Tras cualquier cambio se vuelve a medir el contraste de la tabla de [Color](#color).
- Propuesta de la identidad térmica, con auditoría y referentes (privada): https://claude.ai/artifact/5yNnayNzeAB6Y3MAtKQizH
- Maquetas de pantallas con la identidad térmica, en claro y oscuro (privadas): https://claude.ai/artifact/NHDL3uHf4KC1pWaquru8Cx
