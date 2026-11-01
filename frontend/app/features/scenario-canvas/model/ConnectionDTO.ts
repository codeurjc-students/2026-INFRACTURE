// An arrow from a port of the source node to the target node: the source depends on the target.
export interface ConnectionDTO {
  sourceKey: string;
  port: string;
  targetKey: string;
}
