import { apiFetch } from "~/shared/api/api-fetch";
import type { WorkloadProfileDTO } from "../model/WorkloadProfileDTO";

// Every load profile; the list is short, so it comes in one response.
export function getWorkloadProfiles(): Promise<WorkloadProfileDTO[]> {
  return apiFetch<WorkloadProfileDTO[]>("/api/v1/workload-profiles");
}
