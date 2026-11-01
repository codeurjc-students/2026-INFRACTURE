import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";

export function template(
  key: string,
  name: string,
  type: string,
  profile = "DEFAULT",
  parameters: {
    name: string;
    min: number;
    max: number;
    initial: number;
  }[] = [],
): ComponentTemplateDTO {
  return {
    key,
    name,
    type,
    contract: {
      version: 1,
      profiles: [
        {
          name: profile,
          parameters: parameters.map(({ name, min, max }) => ({
            name,
            min,
            max,
          })),
        },
      ],
    },
    initialConfiguration: {
      profile,
      parameters: Object.fromEntries(
        parameters.map(({ name, initial }) => [name, initial]),
      ),
    },
  };
}

export const httpService = template(
  "http-service",
  "HTTP Service",
  "HTTP_SERVICE",
  "STATELESS",
  [{ name: "responseDelayMs", min: 0, max: 5000, initial: 0 }],
);

export const redis = template("redis", "Redis", "REDIS");
