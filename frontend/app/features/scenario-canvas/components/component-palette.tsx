import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import { MAX_COMPONENTS } from "../graph-reducer";
import ComponentIcon from "./component-icon";

// Drag data type: the canvas only accepts drops that carry a template key with this type.
export const TEMPLATE_MIME = "application/x-infracture-template";

// Families shown in the list. A type not listed here goes to "Other".
const GROUPS = [
  { name: "Services", types: ["HTTP_SERVICE", "WORKER"] },
  { name: "Data stores", types: ["POSTGRESQL", "REDIS"] },
  { name: "Messaging", types: ["RABBITMQ"] },
];

// Splits the templates into their families, leaving out empty ones.
function grouped(templates: ComponentTemplateDTO[]) {
  const known = GROUPS.flatMap((group) => group.types);
  return [
    ...GROUPS.map((group) => ({
      name: group.name,
      templates: templates.filter((template) =>
        group.types.includes(template.type),
      ),
    })),
    {
      name: "Other",
      templates: templates.filter((template) => !known.includes(template.type)),
    },
  ].filter((group) => group.templates.length > 0);
}

// The component list: click (or Enter) adds a template, dragging drops it where you want.
// It stops adding at the component limit.
export default function ComponentPalette({
  templates,
  count,
  onAdd,
}: {
  templates: ComponentTemplateDTO[];
  count: number;
  onAdd: (template: ComponentTemplateDTO) => void;
}) {
  const full = count >= MAX_COMPONENTS;

  return (
    <section
      aria-labelledby="palette-heading"
      className="flex min-h-0 flex-1 flex-col gap-3 p-3 lg:p-4"
    >
      <div className="flex items-baseline justify-between gap-2 px-1">
        <h2 id="palette-heading" className="text-sm font-semibold">
          Components
        </h2>
        <span className="font-mono text-xs text-muted-foreground tabular-nums">
          {`${count}/${MAX_COMPONENTS}`}
        </span>
      </div>
      <div className="-mx-1 flex gap-1 overflow-x-auto px-1 pb-1 lg:min-h-0 lg:flex-col lg:gap-5 lg:overflow-y-auto lg:pb-0">
        {grouped(templates).map((group) => (
          <section
            key={group.name}
            aria-label={group.name}
            className="flex shrink-0 flex-col gap-1"
          >
            <h3 className="px-1.5 type-caption text-muted-foreground max-lg:hidden">
              {group.name}
            </h3>
            <ul className="flex gap-1 lg:flex-col">
              {group.templates.map((template) => (
                <li key={template.key} className="shrink-0">
                  <button
                    type="button"
                    draggable={!full}
                    disabled={full}
                    aria-label={`Add ${template.name}`}
                    onClick={() => onAdd(template)}
                    // The canvas reads this key when the template is dropped.
                    onDragStart={(event) => {
                      event.dataTransfer.setData(TEMPLATE_MIME, template.key);
                      event.dataTransfer.effectAllowed = "copy";
                    }}
                    className="flex min-h-11 w-full cursor-grab items-center gap-2.5 rounded-lg border border-transparent p-1.5 pr-3 text-left text-sm active:cursor-grabbing active:bg-accent disabled:cursor-not-allowed disabled:opacity-50 max-lg:border-border max-lg:bg-card active:scale-[0.98] pointer-fine:hover:bg-accent motion-safe:transition-[background-color,scale] motion-safe:duration-150"
                  >
                    <ComponentIcon
                      type={template.type}
                      className="size-7 rounded-md"
                    />
                    <span className="truncate">{template.name}</span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
      <p className="mt-auto px-1 type-caption text-muted-foreground max-lg:hidden">
        {full
          ? `This scenario has reached its limit of ${MAX_COMPONENTS} components.`
          : "Click to add, or drag onto the canvas."}
      </p>
    </section>
  );
}
