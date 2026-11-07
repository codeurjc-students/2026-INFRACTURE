// A node as it was frozen when Run reserved the execution, with what the backend last saw of it.
// status and response are null until the backend has seen the running node.
export interface ExecutedComponentDTO {
  key: string;
  name: string;
  type: string;
  status: "STARTING" | "HEALTHY" | "UNHEALTHY" | "EXITED" | null;
  response: string | null;
}
