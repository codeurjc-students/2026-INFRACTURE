// jsdom has no layout: React Flow needs sizes and these browser APIs to render nodes.
class ResizeObserverMock {
  constructor(private readonly callback: ResizeObserverCallback) {}
  observe(target: Element) {
    this.callback(
      [{ target, contentRect: { width: 800, height: 600 } } as never],
      this as never,
    );
  }
  unobserve() {}
  disconnect() {}
}

class DOMMatrixReadOnlyMock {
  m22: number;
  constructor(transform?: string) {
    const scale = transform?.match(/scale\(([1-9.])\)/)?.[1];
    this.m22 = scale === undefined ? 1 : Number(scale);
  }
}

globalThis.ResizeObserver = ResizeObserverMock as never;
globalThis.DOMMatrixReadOnly = DOMMatrixReadOnlyMock as never;
// Edge labels measure their text with getBBox, which jsdom does not implement.
Object.defineProperty(SVGElement.prototype, "getBBox", {
  configurable: true,
  value: () => ({ x: 0, y: 0, width: 0, height: 0 }),
});
Object.defineProperties(HTMLElement.prototype, {
  offsetHeight: { configurable: true, get: () => 600 },
  offsetWidth: { configurable: true, get: () => 800 },
});

export {};
