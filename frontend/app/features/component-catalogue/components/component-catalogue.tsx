import type { ComponentTemplateDTO } from "../model/ComponentTemplateDTO";

interface ComponentCatalogueProps {
  componentTemplates: ComponentTemplateDTO[];
}

export default function ComponentCatalogue({
  componentTemplates,
}: ComponentCatalogueProps) {
  return (
    <section aria-labelledby="component-catalogue-heading">
      <header className="mb-8 border-b border-gray-200 pb-6 dark:border-gray-800">
        <p className="mb-2 font-mono text-sm font-medium uppercase tracking-wider text-blue-700 dark:text-blue-300">
          Infrastructure building blocks
        </p>
        <h1
          id="component-catalogue-heading"
          className="text-3xl font-semibold tracking-tight text-gray-950 dark:text-white"
        >
          Component catalogue
        </h1>
        <p className="mt-3 max-w-2xl text-gray-600 dark:text-gray-300">
          Choose from the component templates currently available in the
          platform catalogue.
        </p>
      </header>

      {componentTemplates.length === 0 ? (
        <p className="rounded-lg border border-dashed border-gray-300 p-6 text-gray-600 dark:border-gray-700 dark:text-gray-300">
          No component templates are currently available.
        </p>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {componentTemplates.map((template) => (
            <li
              key={template.key}
              className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900"
            >
              <h2 className="text-lg font-semibold text-gray-950 dark:text-white">
                {template.name}
              </h2>
              <dl className="mt-4 space-y-3 text-sm">
                <div>
                  <dt className="text-gray-500 dark:text-gray-400">Type</dt>
                  <dd className="mt-1 font-medium text-gray-800 dark:text-gray-200">
                    {template.type}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500 dark:text-gray-400">Key</dt>
                  <dd className="mt-1 font-mono text-gray-800 dark:text-gray-200">
                    {template.key}
                  </dd>
                </div>
              </dl>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
