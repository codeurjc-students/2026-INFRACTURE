// jsdom has no EventSource. This stand-in records every stream the page opens, so a test can deliver
// messages, cut the connection or have the server refuse it, as a real EventSource would see them.
export class FakeEventSource extends EventTarget {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  static instances: FakeEventSource[] = [];

  readonly url: string;
  readyState = FakeEventSource.CONNECTING;
  onopen: ((event: Event) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;

  constructor(url: string) {
    super();
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  static latest() {
    return FakeEventSource.instances.at(-1)!;
  }

  static reset() {
    FakeEventSource.instances = [];
  }

  open() {
    this.readyState = FakeEventSource.OPEN;
    this.onopen?.(new Event("open"));
  }

  // A named message, with data sent as JSON.
  emit(type: string, data: unknown) {
    this.dispatchEvent(
      new MessageEvent(type, { data: JSON.stringify(data) }),
    );
  }

  // closed: the server refused the stream (401, 404, 429); otherwise the browser is reconnecting.
  fail(closed: boolean) {
    this.readyState = closed
      ? FakeEventSource.CLOSED
      : FakeEventSource.CONNECTING;
    this.onerror?.(new Event("error"));
  }

  close() {
    this.readyState = FakeEventSource.CLOSED;
  }
}

globalThis.EventSource = FakeEventSource as unknown as typeof EventSource;
