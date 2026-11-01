import { describe, expect, it } from "vitest";

import {
  connectionId,
  connectionIssue,
  toConnection,
  toEdge,
} from "~/features/scenario-canvas/connections";
import type { ComponentNode } from "~/features/scenario-canvas/graph-reducer";
import {
  httpService,
  postgresql,
  redis,
} from "../../fixtures/component-templates";

const templates = new Map(
  [httpService, redis, postgresql].map((template) => [template.key, template]),
);

function node(
  id: string,
  name: string,
  templateKey: string,
  profile: string,
): ComponentNode {
  return {
    id,
    type: "component",
    position: { x: 0, y: 0 },
    data: { templateKey, type: "", name, profile, parameters: {} },
  };
}

const orders = node("a", "orders-api", "http-service", "CACHE_ASIDE");
const cache = node("b", "cache", "redis", "DEFAULT");
const database = node("c", "orders-db", "postgresql", "DEFAULT");

describe("connections", () => {
  it("lets a port reach only nodes that offer a capability it accepts", () => {
    expect(connectionIssue(orders, "cache", cache, templates)).toBeNull();
    expect(connectionIssue(orders, "database", database, templates)).toBeNull();
    expect(connectionIssue(orders, "cache", database, templates)).toBe(
      "Port cache of orders-api needs KEY_VALUE, which orders-db does not offer",
    );
  });

  it("flags a connection whose port the new profile no longer has", () => {
    const stateless = {
      ...orders,
      data: { ...orders.data, profile: "STATELESS" },
    };
    expect(connectionIssue(stateless, "cache", cache, templates)).toBe(
      "orders-api has no port cache in profile STATELESS",
    );
  });

  it("does not judge nodes whose template or profile it does not know", () => {
    const unknown = node("d", "legacy", "retired", "OLD");
    expect(connectionIssue(unknown, "cache", cache, templates)).toBeNull();
    expect(connectionIssue(orders, "cache", unknown, templates)).toBeNull();
  });

  it("uses the backend issue key as the arrow id and keeps the port as its handle", () => {
    const connection = { sourceKey: "a", port: "cache", targetKey: "b" };

    expect(connectionId(connection)).toBe("a:cache:b");
    expect(toEdge(connection)).toMatchObject({
      id: "a:cache:b",
      source: "a",
      sourceHandle: "cache",
      target: "b",
    });
    expect(toConnection(toEdge(connection))).toEqual(connection);
  });
});
