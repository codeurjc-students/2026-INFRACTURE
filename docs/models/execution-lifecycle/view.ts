// Shows the Execution of scenario A; scenario B only matters through the global reservation.
const cleaning: Record<string, string> = {
  stop: "Limpiando tras Stop",
  failure: "Limpiando tras un fallo",
  crash: "Limpiando tras una caída",
};

export const title = "Ciclo de vida de una ejecución del escenario A";

const label = (v: any) => {
  const p = v.phase.A;
  if (p === "none") return "Sin ejecución";
  if (p === "RESERVED") return v.desired.A === "STOPPED" ? "Reservada, Stop pedido" : "Reservada";
  if (p === "STARTING") return v.desired.A === "STOPPED" ? "Arrancando, Stop pedido" : "Arrancando";
  if (p === "RUNNING") return v.desired.A === "STOPPED" ? "En marcha, Stop pedido" : "En marcha";
  if (p === "STOPPING") return cleaning[v.cause.A];
  if (p === "INTERRUPTED") return "Backend caído";
  return p === "COMPLETED" ? "Terminada con éxito" : "Terminada con fallo";
};

export const state = (v: any) => {
  const l = label(v);
  return v.released.A || v.phase.A === "INTERRUPTED" ? l : ["Reserva tomada, escenario en solo lectura", l];
};

export const group = (v: any) => {
  const p = v.phase.A;
  if (p === "none" || p === "COMPLETED" || p === "FAILED") return "fuera";
  if (p === "STOPPING" || p === "INTERRUPTED") return "limpieza";
  return "activa";
};

export const colors = { fuera: "tan", activa: "teal", limpieza: "orange" };

const labels: Record<string, string> = {
  UserRun: "pulsas Run",
  PlanCompiled: "el plan se compila",
  UpSucceeds: "todo arranca sano",
  UpFails: "falla la descarga, el arranque o la salud",
  UserStop: "pulsas Stop",
  StartCancelled: "el arranque se cancela",
  CleanupBegins: "empieza la limpieza",
  CleanupSucceeds: "no queda nada con la etiqueta",
  BackendCrashes: "el backend se cae",
  BackendRestarts: "el backend arranca y reconcilia",
};

export const step = (action: string) => labels[action] ?? action;
