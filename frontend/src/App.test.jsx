import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { FakeEventSource, stubFetch } from './test/fakeEventSource';

function routes(state) {
  return {
    '/api/playback/now': { state, title: 'Heat' },
    '/api/playback/history': [],
    '/api/hue/scenes': {
      scenes: [],
      start: { id: 'a', summary: null },
      stop: { id: 'b', summary: null },
    },
    '/api/config': { hueIntegrationEnabled: true },
    '/api/hue/status': { connected: true },
    '/api/dirigera/status': { reachable: true },
  };
}

beforeEach(() => {
  FakeEventSource.reset();
  vi.stubGlobal('EventSource', FakeEventSource);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('App shell', () => {
  it('shows the wordmark, the auto status, and House rules while idle', async () => {
    vi.stubGlobal('fetch', stubFetch(routes('Idle')));
    render(<App />);

    expect(screen.getByRole('heading', { name: 'CineLight' })).toBeInTheDocument();
    expect(screen.getByText('Switches automatically with playback')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'House rules' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Theater mode' })).toBeInTheDocument();
  });

  it('switches to Theater automatically when playback starts', async () => {
    vi.stubGlobal('fetch', stubFetch(routes('Idle')));
    render(<App />);

    act(() => FakeEventSource.last.emit('now-playing', { state: 'Playing', title: 'Heat' }));

    expect(await screen.findByRole('region', { name: 'Theater mode' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'House rules' })).toBeInTheDocument();
  });

  it('seeds into Theater when something is already playing', async () => {
    vi.stubGlobal('fetch', stubFetch(routes('Playing')));
    render(<App />);
    expect(await screen.findByRole('region', { name: 'Theater mode' })).toBeInTheDocument();
  });

  it('the mode button overrides manually, and the next media event hands it back', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', stubFetch(routes('Playing')));
    render(<App />);
    await screen.findByRole('region', { name: 'Theater mode' });

    await user.click(screen.getByRole('button', { name: 'House rules' }));
    expect(screen.getByRole('region', { name: 'House rules' })).toBeInTheDocument();
    expect(screen.getByText('Manual · switches back on the next event')).toBeInTheDocument();

    act(() => FakeEventSource.last.emit('message', { id: 1, event: 'media.play', title: 'Heat' }));
    await waitFor(() =>
      expect(screen.getByText('Switches automatically with playback')).toBeInTheDocument()
    );
    expect(screen.getByRole('region', { name: 'Theater mode' })).toBeInTheDocument();
  });

  it('can go from House rules to a manual Theater', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', stubFetch(routes('Idle')));
    render(<App />);

    await user.click(screen.getByRole('button', { name: 'Theater mode' }));

    expect(screen.getByRole('region', { name: 'Theater mode' })).toBeInTheDocument();
  });

  it('the More button is a disclosure that toggles its panel', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', stubFetch(routes('Idle')));
    render(<App />);
    const more = screen.getByRole('button', { name: 'More' });
    expect(more).toHaveAttribute('aria-expanded', 'false');

    await user.click(more);
    expect(more).toHaveAttribute('aria-expanded', 'true');
    expect(document.getElementById('more-panel')).toBeInTheDocument();

    await user.click(more);
    expect(document.getElementById('more-panel')).not.toBeInTheDocument();
  });

  it('every control in the header is a real button', () => {
    vi.stubGlobal('fetch', stubFetch(routes('Idle')));
    render(<App />);
    const header = screen.getByRole('banner');
    expect(header.querySelectorAll('button')).toHaveLength(2);
    expect(header.querySelectorAll('[onclick], [role="button"]')).toHaveLength(0);
  });
});
