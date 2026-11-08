import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { FakeEventSource, stubFetch } from './test/fakeEventSource';
import { MOVIE_NIGHT, RELAX, config, history, idle, routes, stubWidth } from './test/fixtures';

const SECRET = 's3cr3t-VALUE-123';

const sectionsBody = (overrides = {}) => ({
  adminTabEnabled: true,
  sections: [
    {
      title: 'Intervals',
      editable: true,
      items: [
        {
          key: 'sse.heartbeat-interval',
          label: 'SSE heartbeat',
          type: 'text',
          duration: true,
          secret: false,
          writable: true,
          value: '30000',
          displayValue: '30s',
        },
        {
          key: 'hue.status-interval',
          label: 'Hue status refresh',
          type: 'text',
          duration: true,
          secret: false,
          writable: true,
          value: '60000',
          displayValue: '1m',
        },
        {
          key: 'hue.hub-address',
          label: 'Hub address',
          type: 'text',
          duration: false,
          secret: false,
          writable: false,
          value: '10.0.0.2',
          displayValue: '10.0.0.2',
        },
      ],
    },
    {
      title: 'Credentials',
      editable: false,
      items: [
        {
          key: 'hue.application-key',
          label: 'Hue application key',
          type: 'secret',
          duration: false,
          secret: true,
          writable: false,
          set: true,
        },
        {
          key: 'dirigera.token',
          label: 'DIRIGERA token',
          type: 'secret',
          duration: false,
          secret: true,
          writable: false,
          set: false,
        },
      ],
    },
  ],
  ...overrides,
});

const resourcesBody = {
  data: [
    { id: 'scene-movie', type: 'scene', metadata: { name: 'Movie Night' } },
    { id: 'scene-relax', type: 'scene', metadata: { name: 'Relax' } },
    { id: 'scene-bright', type: 'scene', metadata: { name: 'Bright' } },
    { id: 'room-lounge', type: 'room', metadata: { name: 'Lounge' } },
    { id: 'light-lamp', type: 'light', metadata: { name: 'Floor lamp' } },
    { id: 'bridge-1', type: 'bridge_home', metadata: {} },
  ],
};

const respond =
  (body, status = 200) =>
  () =>
    Response.json(body, { status });

beforeEach(() => {
  FakeEventSource.reset();
  vi.stubGlobal('EventSource', FakeEventSource);
  vi.stubGlobal('localStorage', {
    store: new Map(),
    getItem(key) {
      return this.store.get(key) ?? null;
    },
    setItem(key, value) {
      this.store.set(key, String(value));
    },
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.location.hash = '';
});

/** Renders the app at `hash` against a stubbed backend. */
async function open(hash = '', { narrow = false, ...overrides } = {}) {
  window.location.hash = hash;
  const setNarrow = stubWidth(narrow);
  const fetchStub = stubFetch(
    routes({
      '/api/playback/now': idle,
      '/api/config/sections': sectionsBody(),
      '/api/hue/resources': resourcesBody,
      '/api/hue/status': {
        hubAddress: '192.168.1.20',
        hubId: '001788FFFE123456',
        applicationKeySet: true,
        integrationEnabled: true,
        connected: true,
      },
      '/api/dirigera/status': { hubAddress: '192.168.1.30', reachable: false },
      ...overrides,
    })
  );
  vi.stubGlobal('fetch', fetchStub);
  const user = userEvent.setup();
  render(<App />);
  await waitFor(() => expect(fetchStub.calls.length).toBeGreaterThanOrEqual(7));
  return { user, fetchStub, setNarrow };
}

const writes = (fetchStub) =>
  fetchStub.calls.filter(({ init }) => ['PUT', 'POST'].includes(init.method));
const lastBody = (fetchStub) => JSON.parse(writes(fetchStub).at(-1).init.body);
const getsOf = (fetchStub, path) =>
  fetchStub.calls.filter(({ url, init }) => url === path && !init.method);

describe('routing and the More menu', () => {
  it('shows the mode view for an unknown hash and a screen for each known one', async () => {
    await open('#/nowhere');
    expect(screen.getByRole('region', { name: 'House rules' })).toBeInTheDocument();
  });

  it('follows hashchange, so Back works, and each screen has a way back to the room', async () => {
    const { user } = await open('#/events');
    expect(screen.getByRole('heading', { name: 'Plex event monitor' })).toHaveFocus();

    await user.click(screen.getByRole('link', { name: 'Back to the room' }));
    expect(window.location.hash).toBe('');
    await waitFor(() =>
      expect(screen.getByRole('region', { name: 'House rules' })).toBeInTheDocument()
    );

    act(() => {
      window.location.hash = '#/lighting';
    });
    expect(await screen.findByRole('heading', { name: 'Smart lighting' })).toBeInTheDocument();
  });

  it('the mode button leaves a screen for the room', async () => {
    const { user } = await open('#/config');
    await user.click(screen.getByRole('button', { name: 'Theater mode' }));
    expect(window.location.hash).toBe('');
    expect(await screen.findByRole('region', { name: 'Theater mode' })).toBeInTheDocument();
  });

  it('lists the screens as links, and Debug only when the admin tab is enabled', async () => {
    const { user } = await open();
    await user.click(screen.getByRole('button', { name: 'More' }));
    const panel = screen.getByRole('navigation', { name: 'More' });
    const links = within(panel).getAllByRole('link');
    expect(links.map((l) => [l.textContent, l.getAttribute('href')])).toEqual([
      ['Event monitor', '#/events'],
      ['Lighting', '#/lighting'],
      ['Config', '#/config'],
      ['Debug', '#/debug'],
    ]);
  });

  it('leaves Debug out of the menu when the admin tab is disabled', async () => {
    const { user } = await open('', {
      '/api/config/sections': sectionsBody({ adminTabEnabled: false }),
    });
    await user.click(screen.getByRole('button', { name: 'More' }));
    const panel = screen.getByRole('navigation', { name: 'More' });
    expect(within(panel).queryByRole('link', { name: 'Debug' })).not.toBeInTheDocument();
    expect(within(panel).getAllByRole('link')).toHaveLength(3);
  });

  it('leaves Debug out of the menu when the sections request fails', async () => {
    const { user } = await open('', { '/api/config/sections': undefined });
    await user.click(screen.getByRole('button', { name: 'More' }));
    expect(screen.queryByRole('link', { name: 'Debug' })).not.toBeInTheDocument();
  });

  it('Escape closes it and returns focus to the button', async () => {
    const { user } = await open();
    const more = screen.getByRole('button', { name: 'More' });
    await user.click(more);
    await user.keyboard('{Escape}');
    expect(more).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('navigation', { name: 'More' })).not.toBeInTheDocument();
    expect(more).toHaveFocus();
  });

  it('other keys leave it open, and a click outside closes it', async () => {
    const { user } = await open();
    await user.click(screen.getByRole('button', { name: 'More' }));
    await user.keyboard('a');
    expect(screen.getByRole('navigation', { name: 'More' })).toBeInTheDocument();

    await user.click(
      within(screen.getByRole('navigation', { name: 'More' })).getByText('Lighting')
    );
    // following a link closes it
    expect(screen.queryByRole('navigation', { name: 'More' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'More' }));
    await user.click(screen.getByRole('main'));
    expect(screen.queryByRole('navigation', { name: 'More' })).not.toBeInTheDocument();
  });

  it('a click inside the menu area does not close it', async () => {
    const { user } = await open();
    await user.click(screen.getByRole('button', { name: 'More' }));
    await user.click(screen.getByRole('navigation', { name: 'More' }));
    expect(screen.getByRole('navigation', { name: 'More' })).toBeInTheDocument();
  });
});

describe('Event monitor', () => {
  const rich = history.map((event, i) =>
    i === 0
      ? {
          ...event,
          source: 'debug',
          player: 'CineLight debug',
          payload: { event: 'media.resume', Player: { title: 'CineLight debug' } },
        }
      : i === 1
        ? { ...event, payload: '{"event":"media.pause"}', player: 'Apple TV' }
        : i === 2
          ? { ...event, payload: 'not json' }
          : event
  );

  it('lists the history as a table with the outcome wording, tagging a debug row as a test', async () => {
    await open('#/events', { '/api/playback/history': rich });
    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(5);
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((h) => h.textContent)
    ).toEqual(['Time', 'Event', 'Media', 'Title', 'Lights']);
    expect(rows[0]).toHaveTextContent('21:43:31');
    expect(rows[0]).toHaveTextContent('media.resume');
    expect(rows[0]).toHaveTextContent('test');
    expect(rows[0]).toHaveTextContent('Movie');
    expect(rows[0]).toHaveTextContent('Blade Runner 2049');
    expect(rows[0]).toHaveTextContent('→ Movie Night');
    expect(rows[2]).toHaveTextContent('Slow Horses · S4 E2');
    expect(rows[2]).toHaveTextContent('Ignored · rule off');
    expect(within(rows[1]).queryByText('test')).not.toBeInTheDocument();
  });

  it('shows the selected event: title, event, player and the pretty-printed payload', async () => {
    const { user } = await open('#/events', { '/api/playback/history': rich });
    const detail = screen.getByRole('complementary', { name: 'Selected event' });
    expect(detail).toHaveTextContent('Select an event to see its payload.');

    await user.click(
      await screen.findByRole('button', { name: /media\.resume Blade Runner 2049/ })
    );
    expect(within(detail).getByRole('heading', { name: 'Blade Runner 2049' })).toBeInTheDocument();
    expect(detail).toHaveTextContent('media.resume');
    expect(detail).toHaveTextContent('CineLight debug');
    expect(detail.querySelector('pre').textContent).toBe(
      JSON.stringify({ event: 'media.resume', Player: { title: 'CineLight debug' } }, null, 2)
    );

    await user.click(screen.getByRole('button', { name: /media\.pause Blade Runner 2049/ }));
    expect(detail.querySelector('pre').textContent).toBe('{\n  "event": "media.pause"\n}');
    expect(detail).toHaveTextContent('Apple TV');

    await user.click(screen.getByRole('button', { name: /media\.stop Slow Horses/ }));
    expect(detail.querySelector('pre').textContent).toBe('not json');
    expect(detail).toHaveTextContent('Unknown');

    await user.click(screen.getByRole('button', { name: /media\.play Vangelis/ }));
    expect(detail).toHaveTextContent('No payload recorded.');
  });

  it('says so when nothing has played', async () => {
    await open('#/events', { '/api/playback/history': [] });
    expect(await screen.findByText('Nothing has played yet.')).toBeInTheDocument();
  });

  it('is a list on a phone, with the payload below it', async () => {
    const { user } = await open('#/events', { narrow: true, '/api/playback/history': rich });
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    const list = await screen.findByRole('list', { name: 'Events' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(5);
    expect(within(list).getAllByText('test')).toHaveLength(1);
    await user.click(within(list).getAllByRole('button')[0]);
    expect(screen.getByRole('complementary', { name: 'Selected event' })).toHaveTextContent(
      'media.resume'
    );
  });

  it('shows the Simulate buttons only when the admin tab is enabled', async () => {
    await open('#/events', { '/api/config/sections': sectionsBody({ adminTabEnabled: false }) });
    await screen.findByRole('table');
    expect(screen.queryByRole('button', { name: 'Play' })).not.toBeInTheDocument();
  });

  it('Simulate posts the event for a movie and shows the outcome', async () => {
    const { user, fetchStub } = await open('#/events', {
      '/api/debug/pause': respond({
        command: 'pause',
        type: 'movie',
        success: true,
        outcome: 'SCENE_APPLIED',
        id: 9,
      }),
      '/api/debug/stop': respond({ outcome: 'IGNORED_RULE_OFF' }),
    });
    await user.click(await screen.findByRole('button', { name: 'Pause' }));
    const call = writes(fetchStub).at(-1);
    expect(call.url).toBe('/api/debug/pause?type=movie');
    expect(call.init.method).toBe('POST');
    expect(call.init.headers['X-Requested-With']).toBe('XMLHttpRequest');
    expect(await screen.findByRole('status')).toHaveTextContent('Sent pause: Scene applied');

    await user.click(screen.getByRole('button', { name: 'Stop' }));
    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent('Sent stop: Ignored · rule off')
    );
  });

  it('Simulate says so when the event was not sent', async () => {
    const { user } = await open('#/events', { '/api/debug/play': respond({}, 500) });
    await user.click(await screen.findByRole('button', { name: 'Play' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The play event was not sent.');
  });
});

describe('Lighting', () => {
  it('shows the Hue bridge and DIRIGERA cards', async () => {
    await open('#/lighting');
    const hue = await screen.findByRole('region', { name: 'Hue bridge' });
    expect(hue).toHaveTextContent('StatusConnected');
    expect(hue).toHaveTextContent('Hue commands enabledYes');
    expect(hue).toHaveTextContent('Hub address192.168.1.20');
    expect(hue).toHaveTextContent('Bridge id001788…3456');
    expect(hue).toHaveTextContent('Application keySet');
    const dirigera = screen.getByRole('region', { name: 'DIRIGERA' });
    expect(dirigera).toHaveTextContent('ReachableNo');
    expect(dirigera).toHaveTextContent('Hub address192.168.1.30');
  });

  it('shows Not set / Not connected / No for the opposite states', async () => {
    await open('#/lighting', {
      '/api/hue/status': { connected: false, applicationKeySet: false },
      '/api/dirigera/status': { reachable: true },
      '/api/config': { ...config, hueIntegrationEnabled: false },
    });
    const hue = await screen.findByRole('region', { name: 'Hue bridge' });
    await waitFor(() => expect(hue).toHaveTextContent('Hue commands enabledNo'));
    expect(hue).toHaveTextContent('StatusNot connected');
    expect(hue).toHaveTextContent('Application keyNot set');
    expect(hue).toHaveTextContent('Bridge id—');
    expect(screen.getByRole('region', { name: 'DIRIGERA' })).toHaveTextContent('ReachableYes');
  });

  it('fetches the statuses when it opens with none yet, and shows dashes while there are none', async () => {
    const { fetchStub } = await open('', {
      '/api/hue/status': undefined,
      '/api/dirigera/status': undefined,
    });
    expect(getsOf(fetchStub, '/api/hue/status')).toHaveLength(1);
    act(() => {
      window.location.hash = '#/lighting';
    });
    const hue = await screen.findByRole('region', { name: 'Hue bridge' });
    expect(hue).toHaveTextContent('Status—');
    await waitFor(() => expect(getsOf(fetchStub, '/api/hue/status')).toHaveLength(2));
    expect(getsOf(fetchStub, '/api/dirigera/status')).toHaveLength(2);
  });

  it('takes the status from the SSE stream', async () => {
    await open('#/lighting');
    act(() =>
      FakeEventSource.last.emit('hue-status', { connected: false, hubAddress: '10.9.9.9' })
    );
    expect(await screen.findByRole('region', { name: 'Hue bridge' })).toHaveTextContent('10.9.9.9');
  });

  it('seeds a missing status when Lighting opens, and ignores a failing one', async () => {
    const { fetchStub } = await open('', {
      '/api/hue/status': undefined,
      '/api/dirigera/status': undefined,
    });
    vi.stubGlobal(
      'fetch',
      Object.assign(
        (url, init = {}) => {
          fetchStub.calls.push({ url, init });
          if (url === '/api/hue/status') {
            return Promise.resolve(Response.json({ connected: true, hubAddress: '10.1.1.1' }));
          }
          return Promise.resolve(new Response('', { status: 500 }));
        },
        { calls: fetchStub.calls }
      )
    );
    act(() => {
      window.location.hash = '#/lighting';
    });
    expect(await screen.findByText('10.1.1.1')).toBeInTheDocument();
  });

  it('names the current start and stop scenes', async () => {
    await open('#/lighting');
    const start = await screen.findByRole('region', { name: 'On start' });
    expect(start).toHaveTextContent('Movie Night');
    expect(start).toHaveTextContent('Room at 5%');
    expect(screen.getByRole('region', { name: 'On stop' })).toHaveTextContent('Relax');
  });

  it('lists scenes, rooms and lights, tagging the current start and stop scenes', async () => {
    await open('#/lighting');
    const list = await screen.findByRole('list', { name: 'Hue resources' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(5);
    expect(within(list).queryByText('bridge-1')).not.toBeInTheDocument();
    const rows = within(list).getAllByRole('button');
    expect(rows[0]).toHaveTextContent('Movie Night');
    expect(rows[0]).toHaveTextContent('Start');
    expect(rows[1]).toHaveTextContent('Stop');
    expect(rows[2]).not.toHaveTextContent(/Start|Stop/);
    expect(rows[3]).not.toHaveTextContent(/Start|Stop/);
  });

  it('searches and filters by type', async () => {
    const { user } = await open('#/lighting');
    await screen.findByRole('list', { name: 'Hue resources' });
    await user.type(screen.getByLabelText('Search resources'), 'lounge');
    expect(screen.getAllByRole('button', { name: /Lounge/ })).toHaveLength(1);
    expect(screen.queryByRole('button', { name: /Movie Night/ })).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText('Search resources'));
    await user.selectOptions(screen.getByLabelText('Type'), 'light');
    expect(
      within(screen.getByRole('list', { name: 'Hue resources' })).getAllByRole('listitem')
    ).toHaveLength(1);

    await user.type(screen.getByLabelText('Search resources'), 'nothing like this');
    expect(screen.getByText('No resources match.')).toBeInTheDocument();
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual([
      'All',
      'Scene',
      'Room',
      'Light',
    ]);
  });

  it('offers every present type when "show all types" is on in this browser', async () => {
    localStorage.setItem('cinelight.showAllHueTypes', 'true');
    await open('#/lighting');
    await screen.findByRole('list', { name: 'Hue resources' });
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual([
      'All',
      'Bridge home',
      'Light',
      'Room',
      'Scene',
    ]);
    expect(
      within(screen.getByRole('list', { name: 'Hue resources' })).getAllByRole('listitem')
    ).toHaveLength(6);
  });

  it('Use on start / Use on stop put the selected scene, optimistically', async () => {
    const { user, fetchStub } = await open('#/lighting', {
      '/api/config/hue-scene': respond({}),
    });
    expect(screen.queryByRole('button', { name: 'Use on start' })).not.toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: /Bright/ }));
    await user.click(screen.getByRole('button', { name: 'Use on start' }));
    expect(writes(fetchStub).at(-1).url).toBe('/api/config/hue-scene');
    expect(lastBody(fetchStub)).toEqual({ key: 'hue.start-scene', sceneId: 'scene-bright' });
    expect(screen.getByRole('region', { name: 'On start' })).toHaveTextContent('Bright');

    await user.click(screen.getByRole('button', { name: 'Use on stop' }));
    expect(lastBody(fetchStub)).toEqual({ key: 'hue.stop-scene', sceneId: 'scene-bright' });
  });

  it('rolls a refused scene write back and says so', async () => {
    const { user } = await open('#/lighting', { '/api/config/hue-scene': respond({}, 400) });
    await user.click(await screen.findByRole('button', { name: /Bright/ }));
    await user.click(screen.getByRole('button', { name: 'Use on start' }));
    expect(await screen.findByText('That change was not saved.')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'On start' })).toHaveTextContent(MOVIE_NIGHT.name);
    expect(RELAX.name).toBe('Relax');
  });

  it('offers no scene buttons for a room', async () => {
    const { user } = await open('#/lighting');
    await user.click(await screen.findByRole('button', { name: /Lounge/ }));
    expect(screen.queryByRole('button', { name: 'Use on start' })).not.toBeInTheDocument();
  });

  it('says the bridge is unreachable on a 503', async () => {
    await open('#/lighting', { '/api/hue/resources': respond({ error: 'x' }, 503) });
    expect(await screen.findByText('Hue bridge unreachable.')).toBeInTheDocument();
  });

  it('says the resources could not be loaded on any other failure', async () => {
    await open('#/lighting', { '/api/hue/resources': undefined });
    expect(await screen.findByText('The Hue resources could not be loaded.')).toBeInTheDocument();
  });
});

describe('Config', () => {
  it('shows the media type by event matrix with labelled checkboxes', async () => {
    await open('#/config');
    const table = await screen.findByRole('table');
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((h) => h.textContent)
    ).toEqual(['Media type', 'Play', 'Resume', 'Pause', 'Stop', 'Scrobble']);
    expect(screen.getByRole('checkbox', { name: 'Movie play' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'TV show scrobble' })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'TV show stop' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Photo resume' })).not.toBeChecked();
    expect(screen.getAllByRole('checkbox')).toHaveLength(25);
  });

  it('writes one PUT per change, with the type full map, optimistically', async () => {
    const { user, fetchStub } = await open('#/config', {
      '/api/config/plex-commands/episode': respond({}),
    });
    await user.click(await screen.findByRole('checkbox', { name: 'TV show scrobble' }));
    expect(screen.getByRole('checkbox', { name: 'TV show scrobble' })).toBeChecked();
    expect(writes(fetchStub)).toHaveLength(1);
    expect(writes(fetchStub)[0].url).toBe('/api/config/plex-commands/episode');
    expect(lastBody(fetchStub)).toMatchObject({
      'plex.episode-scrobble-enabled': true,
      'plex.episode-play-enabled': true,
    });
    expect(Object.keys(lastBody(fetchStub))).toHaveLength(5);
  });

  it('rolls a refused change back', async () => {
    const { user } = await open('#/config', {
      '/api/config/plex-commands/clip': respond({}, 500),
    });
    await user.click(await screen.findByRole('checkbox', { name: 'Clip play' }));
    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: 'Clip play' })).not.toBeChecked()
    );
    expect(screen.getByText('That change was not saved.')).toBeInTheDocument();
  });

  it('waits for the rules to load', async () => {
    await open('#/config', { '/api/config': undefined });
    expect(await screen.findByText('Loading the rules…')).toBeInTheDocument();
  });

  it('shows intervals in whole seconds and only the writable items', async () => {
    await open('#/config');
    expect(await screen.findByLabelText('SSE heartbeat')).toHaveValue('30');
    expect(screen.getByLabelText('Hue status refresh')).toHaveValue('60');
    expect(screen.queryByLabelText('Hub address')).not.toBeInTheDocument();
    expect(screen.getByText('sse.heartbeat-interval')).toBeInTheDocument();
  });

  it('keeps Save disabled until a value changes, and Cancel restores', async () => {
    const { user } = await open('#/config');
    const save = await screen.findByRole('button', { name: 'Save' });
    expect(save).toBeDisabled();
    const input = screen.getByLabelText('SSE heartbeat');
    await user.clear(input);
    await user.type(input, '45');
    expect(save).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(input).toHaveValue('30');
    expect(save).toBeDisabled();

    // typing the stored value back is not a change
    await user.clear(input);
    await user.type(input, '30');
    expect(save).toBeDisabled();
  });

  it.each(['0', '86401', 'abc', '', '1.5'])(
    'rejects %j: Save stays disabled and the field says why',
    async (text) => {
      const { user } = await open('#/config');
      const input = await screen.findByLabelText('SSE heartbeat');
      await user.clear(input);
      if (text) {
        await user.type(input, text);
      }
      expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
      expect(input).toHaveAttribute('aria-invalid', 'true');
      expect(screen.getByText('Whole seconds, 1 to 86400.')).toBeInTheDocument();
    }
  );

  it('accepts the boundaries 1 and 86400', async () => {
    const { user } = await open('#/config');
    const input = await screen.findByLabelText('SSE heartbeat');
    await user.clear(input);
    await user.type(input, '86400');
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    await user.clear(input);
    await user.type(input, '1');
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
  });

  it('disables Save while any edited field is invalid', async () => {
    const { user } = await open('#/config');
    await user.clear(await screen.findByLabelText('SSE heartbeat'));
    await user.type(screen.getByLabelText('SSE heartbeat'), '45');
    await user.clear(screen.getByLabelText('Hue status refresh'));
    await user.type(screen.getByLabelText('Hue status refresh'), '0');
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('saves only the changed intervals, in milliseconds, then reads the sections again', async () => {
    const { user, fetchStub } = await open('#/config', {
      '/api/config/properties': respond({}),
    });
    const input = await screen.findByLabelText('SSE heartbeat');
    await user.clear(input);
    await user.type(input, '45');
    const before = getsOf(fetchStub, '/api/config/sections').length;
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(writes(fetchStub).at(-1).url).toBe('/api/config/properties');
    expect(lastBody(fetchStub)).toEqual({ 'sse.heartbeat-interval': '45000' });
    expect(await screen.findByText('Intervals saved.')).toBeInTheDocument();
    await waitFor(() =>
      expect(getsOf(fetchStub, '/api/config/sections').length).toBeGreaterThan(before)
    );
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('reports a refused save and keeps the typed value', async () => {
    const { user } = await open('#/config', { '/api/config/properties': respond({}, 400) });
    const input = await screen.findByLabelText('SSE heartbeat');
    await user.clear(input);
    await user.type(input, '45');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('The intervals were not saved.')).toBeInTheDocument();
    expect(input).toHaveValue('45');
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    await user.type(input, '0');
    expect(screen.queryByText('The intervals were not saved.')).not.toBeInTheDocument();
  });

  it('fetches the sections again whenever Config opens', async () => {
    const { fetchStub } = await open('');
    const before = getsOf(fetchStub, '/api/config/sections').length;
    act(() => {
      window.location.hash = '#/config';
    });
    await waitFor(() => expect(getsOf(fetchStub, '/api/config/sections').length).toBe(before + 1));
  });

  it('keeps the last sections when a refresh fails', async () => {
    const { fetchStub } = await open('#/config');
    await screen.findByLabelText('SSE heartbeat');
    vi.stubGlobal(
      'fetch',
      Object.assign(() => Promise.resolve(new Response('', { status: 500 })), {
        calls: fetchStub.calls,
      })
    );
    act(() => {
      window.location.hash = '#/events';
    });
    act(() => {
      window.location.hash = '#/config';
    });
    expect(await screen.findByLabelText('SSE heartbeat')).toHaveValue('30');
  });

  it('shows credentials only as Set / Not set, with the note', async () => {
    await open('#/config');
    const items = (await screen.findByText('Hue application key')).closest('li');
    expect(items).toHaveTextContent('Set');
    expect(screen.getByText('DIRIGERA token').closest('li')).toHaveTextContent('Not set');
    expect(screen.getByText(/never sent to the browser/)).toBeInTheDocument();
  });

  it('never renders a secret item value, even if the server sent one', async () => {
    const leaky = sectionsBody();
    leaky.sections[1].items[0] = {
      ...leaky.sections[1].items[0],
      value: SECRET,
      displayValue: SECRET,
    };
    leaky.sections[1].items[1] = { ...leaky.sections[1].items[1], value: SECRET, writable: true };
    await open('#/config', { '/api/config/sections': leaky });
    await screen.findByText('Hue application key');
    expect(document.body.textContent).not.toContain(SECRET);
    expect(document.body.outerHTML).not.toContain(SECRET);
    expect(Array.from(document.querySelectorAll('input')).map((i) => i.value)).not.toContain(
      SECRET
    );
    expect(screen.queryByLabelText('DIRIGERA token')).not.toBeInTheDocument();
  });

  it('says so while the sections load and when there are no intervals', async () => {
    await open('#/config', { '/api/config/sections': undefined });
    expect(await screen.findByText('Loading the settings…')).toBeInTheDocument();
  });

  it('copes with no editable intervals and sections without items', async () => {
    await open('#/config', {
      '/api/config/sections': {
        adminTabEnabled: false,
        sections: [
          { title: 'Empty', editable: false },
          { title: 'Also', editable: false, items: [] },
        ],
      },
    });
    expect(await screen.findByText('No intervals can be edited here.')).toBeInTheDocument();
  });

  it('copes with a response with no sections list', async () => {
    await open('#/config', { '/api/config/sections': { adminTabEnabled: false } });
    expect(await screen.findByText('No intervals can be edited here.')).toBeInTheDocument();
  });
});

describe('Debug', () => {
  it('says Debug is off, rather than rendering controls, when the admin tab is disabled', async () => {
    await open('#/debug', { '/api/config/sections': sectionsBody({ adminTabEnabled: false }) });
    expect(await screen.findByText(/Debug is off/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Send test event' })).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('says Debug is off when the sections request failed', async () => {
    await open('#/debug', { '/api/config/sections': undefined });
    expect(await screen.findByText(/Debug is off/)).toBeInTheDocument();
  });

  it('shows the controls when enabled', async () => {
    await open('#/debug');
    expect(await screen.findByRole('checkbox', { name: /Integrate Hue/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /Show all Hue resource types/ })).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Test Hue connection' })).toBeInTheDocument();
  });

  it('Integrate Hue puts hue-enabled, optimistically, and rolls back a refusal', async () => {
    const { user, fetchStub } = await open('#/debug', {
      '/api/config/hue-enabled': respond({}),
    });
    const box = await screen.findByRole('checkbox', { name: /Integrate Hue/ });
    await user.click(box);
    expect(box).not.toBeChecked();
    expect(writes(fetchStub).at(-1).url).toBe('/api/config/hue-enabled');
    expect(lastBody(fetchStub)).toEqual({ enabled: false });
  });

  it('does nothing for Integrate Hue before the config has loaded', async () => {
    const { user, fetchStub } = await open('#/debug', { '/api/config': undefined });
    await user.click(await screen.findByRole('checkbox', { name: /Integrate Hue/ }));
    expect(writes(fetchStub)).toHaveLength(0);
  });

  it('rolls Integrate Hue back when refused', async () => {
    const { user } = await open('#/debug', { '/api/config/hue-enabled': respond({}, 500) });
    const box = await screen.findByRole('checkbox', { name: /Integrate Hue/ });
    await user.click(box);
    await waitFor(() => expect(box).toBeChecked());
  });

  it('stores "show all types" in this browser only, and the Lighting screen honours it', async () => {
    const { user, fetchStub } = await open('#/debug');
    await user.click(await screen.findByRole('checkbox', { name: /Show all Hue resource types/ }));
    expect(localStorage.getItem('cinelight.showAllHueTypes')).toBe('true');
    expect(writes(fetchStub)).toHaveLength(0);

    act(() => {
      window.location.hash = '#/lighting';
    });
    await screen.findByRole('list', { name: 'Hue resources' });
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toContain('Bridge home');
  });

  it('survives a localStorage that throws', async () => {
    const blocked = () => {
      throw new Error('blocked');
    };
    vi.stubGlobal('localStorage', { getItem: blocked, setItem: blocked });
    const { user } = await open('#/debug');
    const box = await screen.findByRole('checkbox', { name: /Show all Hue resource types/ });
    await user.click(box);
    expect(box).toBeChecked();
  });

  it('tests the Hue connection and shows the result inline', async () => {
    const { user } = await open('#/debug', {
      '/api/hue/test-connection': respond({ connected: true, hubId: '001788FFFE123456' }),
    });
    await user.click(await screen.findByRole('button', { name: 'Test Hue connection' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Connected to bridge 001788…3456.');
  });

  it('reports a bridge that is not connected, and a failed test', async () => {
    const { user } = await open('#/debug', {
      '/api/hue/test-connection': respond({ connected: false }),
    });
    await user.click(await screen.findByRole('button', { name: 'Test Hue connection' }));
    expect(await screen.findByText('Not connected to the Hue bridge.')).toBeInTheDocument();
  });

  it('reports a failed connection test', async () => {
    const { user } = await open('#/debug', { '/api/hue/test-connection': respond({}, 500) });
    await user.click(await screen.findByRole('button', { name: 'Test Hue connection' }));
    expect(await screen.findByText('The connection test failed.')).toBeInTheDocument();
  });

  it('sends a test event with its media type and optional title, and shows the outcome', async () => {
    const { user, fetchStub } = await open('#/debug', {
      '/api/debug/resume': respond({ outcome: 'SCENE_APPLIED', success: true }),
    });
    await screen.findByRole('button', { name: 'Send test event' });
    await user.selectOptions(screen.getByLabelText('Event'), 'resume');
    await user.selectOptions(screen.getByLabelText('Media type'), 'episode');
    await user.type(screen.getByLabelText('Title (optional)'), 'Slow Horses & more');
    await user.click(screen.getByRole('button', { name: 'Send test event' }));

    const call = writes(fetchStub).at(-1);
    expect(call.init.method).toBe('POST');
    expect(call.url).toBe('/api/debug/resume?type=episode&title=Slow+Horses+%26+more');
    expect(await screen.findByRole('status')).toHaveTextContent('Sent resume: Scene applied');
  });

  it('sends no title parameter when the title is blank', async () => {
    const { user, fetchStub } = await open('#/debug', {
      '/api/debug/play': respond({ outcome: 'FAILED' }),
    });
    await user.type(await screen.findByLabelText('Title (optional)'), '   ');
    await user.click(screen.getByRole('button', { name: 'Send test event' }));
    await waitFor(() => expect(writes(fetchStub)).toHaveLength(1));
    expect(writes(fetchStub)[0].url).toBe('/api/debug/play?type=movie');
    expect(await screen.findByRole('status')).toHaveTextContent('Sent play: Failed');
  });

  it('says so when the test event was refused', async () => {
    const { user } = await open('#/debug', { '/api/debug/play': respond({}, 500) });
    await user.click(await screen.findByRole('button', { name: 'Send test event' }));
    expect(await screen.findByRole('status')).toHaveTextContent('The play event was not sent.');
  });
});
