import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import type { TemplateContractDTO } from "~/features/component-catalogue/model/TemplateContractDTO";
import type { WorkloadProfileDTO } from "~/features/component-catalogue/model/WorkloadProfileDTO";

type Profile = TemplateContractDTO["profiles"][number];

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
  capabilities: string[] = [],
  otherProfiles: Profile[] = [],
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
          ports: [],
          capabilities,
        },
        ...otherProfiles,
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

// Same ports and capabilities as the backend contract.
export const cacheAside: Profile = {
  name: "CACHE_ASIDE",
  parameters: [{ name: "responseDelayMs", min: 0, max: 5000 }],
  ports: [
    { name: "cache", label: "Cache", accepts: ["KEY_VALUE"], min: 1, max: 1 },
    {
      name: "database",
      label: "Database",
      accepts: ["SQL_DATABASE"],
      min: 1,
      max: 1,
    },
  ],
  capabilities: ["HTTP", "RESOURCE_READ", "RESOURCE_WRITE"],
};

export const httpService = template(
  "http-service",
  "HTTP Service",
  "HTTP_SERVICE",
  "STATELESS",
  [{ name: "responseDelayMs", min: 0, max: 5000, initial: 0 }],
  ["HTTP"],
  [cacheAside],
);

export const redis = template(
  "redis",
  "Redis",
  "REDIS",
  "DEFAULT",
  [],
  ["KEY_VALUE"],
);

export const postgresql = template(
  "postgresql",
  "PostgreSQL",
  "POSTGRESQL",
  "DEFAULT",
  [],
  ["SQL_DATABASE"],
);

// The one load profile, as GET /workload-profiles returns it (ADR 0016).
export const constantHttp: WorkloadProfileDTO = {
  name: "CONSTANT_HTTP",
  parameters: [
    { name: "requestsPerSecond", min: 1, max: 50 },
    { name: "virtualUsers", min: 1, max: 20 },
    { name: "durationSeconds", min: 10, max: 600 },
  ],
  seed: { name: "seed", min: 0, max: 2147483647 },
  requires: ["HTTP"],
  initialParameters: {
    requestsPerSecond: 5,
    virtualUsers: 2,
    durationSeconds: 60,
  },
};
