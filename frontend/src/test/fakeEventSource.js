/** A stand-in for EventSource that tests drive by hand. */
export class FakeEventSource {
  static instances = [];

  static reset() {
    FakeEventSource.instances = [];
  }

  static get last() {
    return FakeEventSource.instances[FakeEventSource.instances.length - 1];
  }

  constructor(url) {
    this.url = url;
    this.closed = false;
    this.listeners = {};
    this.onopen = null;
    this.onerror = null;
    FakeEventSource.instances.push(this);
  }

  addEventListener(name, listener) {
    (this.listeners[name] ||= []).push(listener);
  }

  close() {
    this.closed = true;
  }

  open() {
    this.onopen?.();
  }

  fail() {
    this.onerror?.();
  }

  emit(name, data) {
    const raw = typeof data === 'string' ? data : JSON.stringify(data);
    (this.listeners[name] || []).forEach((listener) => listener({ data: raw }));
  }
}

/** A fetch stub answering each URL from a map of path -> body (or a function returning a Response). */
export function stubFetch(routes) {
  const calls = [];
  const fetchStub = (url, init = {}) => {
    calls.push({ url, init });
    const route = routes[url.split('?')[0]];
    const result = typeof route === 'function' ? route(url, init) : route;
    if (result instanceof Response) {
      return Promise.resolve(result);
    }
    if (result === undefined) {
      return Promise.resolve(new Response('', { status: 404 }));
    }
    return Promise.resolve(Response.json(result));
  };
  fetchStub.calls = calls;
  return fetchStub;
}
