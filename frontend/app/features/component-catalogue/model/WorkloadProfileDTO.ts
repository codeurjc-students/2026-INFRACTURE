// A load profile from GET /workload-profiles: its integer parameters with limits, the seed's range,
// the capabilities its target must offer and the values a new load starts with (ADR 0016).
export interface WorkloadProfileDTO {
  name: string;
  parameters: { name: string; min: number; max: number }[];
  seed: { name: string; min: number; max: number };
  requires: string[];
  initialParameters: Record<string, number>;
}
