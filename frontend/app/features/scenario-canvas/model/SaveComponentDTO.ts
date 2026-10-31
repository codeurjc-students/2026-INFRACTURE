import type { ComponentDTO } from "./ComponentDTO";

// A node in the save request: the same as a read node without the type, which comes from its template.
export type SaveComponentDTO = Omit<ComponentDTO, "type">;
