import type { Edge } from "@xyflow/react";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import type { ComponentNode } from "./graph-reducer";
import type { ConnectionDTO } from "./model/ConnectionDTO";

// The arrow id is the key the backend gives a connection in its issues, so both point to the same arrow.
export function connectionId({ sourceKey, port, targetKey }: ConnectionDTO) {
  return `${sourceKey}:${port}:${targetKey}`;
}

// A connection as React Flow draws it: the port is the source handle the arrow leaves from.
export function toEdge(connection: ConnectionDTO, selected = false): Edge {
  return {
    id: connectionId(connection),
    source: connection.sourceKey,
    sourceHandle: connection.port,
    target: connection.targetKey,
    selected,
  };
}

export function toConnection(edge: Edge): ConnectionDTO {
  return {
    sourceKey: edge.source,
    port: edge.sourceHandle ?? "",
    targetKey: edge.target,
  };
}

// The contract profile a node uses, if its template and profile are known.
export function profileOf(
  node: ComponentNode,
  templatesByKey: Map<string, ComponentTemplateDTO>,
) {
  return templatesByKey
    .get(node.data.templateKey)
    ?.contract.profiles.find((profile) => profile.name === node.data.profile);
}

// Why a connection breaks the source's contract, with the backend's wording, or null when it fits.
// A node whose profile is unknown already has its own issue, so its connections are not judged.
export function connectionIssue(
  source: ComponentNode,
  port: string,
  target: ComponentNode,
  templatesByKey: Map<string, ComponentTemplateDTO>,
): string | null {
  const sourceProfile = profileOf(source, templatesByKey);
  if (!sourceProfile) return null;
  const contract = sourceProfile.ports.find(
    (candidate) => candidate.name === port,
  );
  if (!contract) {
    return `${source.data.name} has no port ${port} in profile ${sourceProfile.name}`;
  }
  const offered = profileOf(target, templatesByKey)?.capabilities;
  if (!offered || contract.accepts.some((need) => offered.includes(need))) {
    return null;
  }
  return `Port ${port} of ${source.data.name} needs ${contract.accepts.join(" or ")}, which ${target.data.name} does not offer`;
}
