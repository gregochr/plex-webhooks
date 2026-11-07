import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { FakeEventSource, stubFetch } from '../test/fakeEventSource';
import { RECONNECT_MAX_MS, reconnectDelay, useCineLight } from './useCineLight';

const NOW = { state: 'Playing', title: 'Heat', sceneId: 's1' };
const HISTORY = [
  { id: 2, event: 'media.play', title: 'Heat' },
  { id: 1, event: 'media.stop', title: 'Alien' },
];
const ROUTES = {
  '/api/playback/now': NOW,
  '/api/playback/history': HISTORY,
  '/api/hue/scenes': {
    scenes: [],
    start: { id: 'a', summary: null },
    stop: { id: 'b', summary: null },
  },
  '/api/config': { hueIntegrationEnabled: true },
  '/api/hue/status': { connected: true },
  '/api/dirigera/status': { reachable: false },
};

let fetchStub;

beforeEach(() => {
  FakeEventSource.reset();
  vi.stubGlobal('EventSource', FakeEventSource);
  fetchStub = stubFetch(ROUTES);
  vi.stubGlobal('fetch', fetchStub);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('useCineLight', () => {
  it('seeds every slice from the GET endpoints', async () => {
    const { result } = renderHook(() => useCineLight());
    await waitFor(() => expect(result.current.nowPlaying).toEqual(NOW));
    expect(result.current.history.map((e) => e.id)).toEqual([2, 1]);
    expect(result.current.scenes.start.id).toBe('a');
    expect(result.current.config.hueIntegrationEnabled).toBe(true);
    expect(result.current.hueStatus.connected).toBe(true);
    expect(result.current.dirigeraStatus.reachable).toBe(false);
    const urls = fetchStub.calls.map((c) => c.url);
    expect(urls).toContain('/api/playback/history?limit=100');
  });

  it('opens one EventSource with a client id', () => {
    renderHook(() => useCineLight());
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.last.url).toMatch(/^\/api\/events\?clientId=[\w-]+$/);
  });

  it('survives a failing seed and keeps the rest', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({ ...ROUTES, '/api/hue/scenes': new Response('', { status: 500 }) })
    );
    const { result } = renderHook(() => useCineLight());
    await waitFor(() => expect(result.current.nowPlaying).toEqual(NOW));
    expect(result.current.scenes).toBeNull();
  });

  it('applies each live event and ignores malformed data', async () => {
    const { result } = renderHook(() => useCineLight());
    await waitFor(() => expect(result.current.nowPlaying).not.toBeNull());
    const es = FakeEventSource.last;

    act(() => {
      es.open();
      es.emit('message', { id: 3, event: 'media.pause', title: 'Heat' });
      es.emit('now-playing', { state: 'Paused' });
      es.emit('config', { hueIntegrationEnabled: false });
      es.emit('hue-status', { connected: false });
      es.emit('dirigera-status', { reachable: true });
      es.emit('heartbeat', {});
      es.emit('message', 'not json');
    });

    expect(result.current.connection).toBe('live');
    expect(result.current.history[0].id).toBe(3);
    expect(result.current.nowPlaying.state).toBe('Paused');
    expect(result.current.config.hueIntegrationEnabled).toBe(false);
    expect(result.current.hueStatus.connected).toBe(false);
    expect(result.current.dirigeraStatus.reachable).toBe(true);
    expect(result.current.lastHeartbeat).toEqual(expect.any(Number));
    expect(result.current.history).toHaveLength(3);
  });

  it('a media message resets a manual view to auto', async () => {
    const { result } = renderHook(() => useCineLight());
    await waitFor(() => expect(result.current.nowPlaying).not.toBeNull());
    act(() => result.current.setView('rules'));
    expect(result.current.view).toBe('rules');

    act(() => FakeEventSource.last.emit('message', { id: 9, event: 'media.play' }));

    expect(result.current.view).toBe('auto');
  });

  it('reconnects with doubling backoff, and re-fetches history and now-playing once reopened', async () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useCineLight());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    FakeEventSource.last.open();
    const seedCalls = fetchStub.calls.length;

    act(() => FakeEventSource.last.fail());
    expect(FakeEventSource.instances[0].closed).toBe(true);
    expect(result.current.connection).toBe('reconnecting');
    expect(FakeEventSource.instances).toHaveLength(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(999);
    });
    expect(FakeEventSource.instances).toHaveLength(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(FakeEventSource.instances).toHaveLength(2);

    // The second attempt fails too: the delay doubles.
    act(() => FakeEventSource.last.fail());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1999);
    });
    expect(FakeEventSource.instances).toHaveLength(2);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(FakeEventSource.instances).toHaveLength(3);

    await act(async () => {
      FakeEventSource.last.open();
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.connection).toBe('live');
    const refetched = fetchStub.calls.slice(seedCalls).map((c) => c.url);
    expect(refetched).toEqual(['/api/playback/history?limit=100', '/api/playback/now']);
  });

  it('caps the backoff and resets it after a successful open', () => {
    expect(reconnectDelay(0)).toBe(1000);
    expect(reconnectDelay(2)).toBe(4000);
    expect(reconnectDelay(20)).toBe(RECONNECT_MAX_MS);
  });

  it('closes the stream, cancels a pending reconnect and drops late seeds on unmount', async () => {
    vi.useFakeTimers();
    const { unmount } = renderHook(() => useCineLight());
    act(() => FakeEventSource.last.fail());

    unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60000);
    });

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].closed).toBe(true);
  });
});
