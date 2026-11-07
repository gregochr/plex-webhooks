import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { FakeEventSource, stubFetch } from './test/fakeEventSource';
import {
  BRIGHT,
  MOVIE_NIGHT,
  RELAX,
  at,
  catalog,
  config,
  history,
  idle,
  playingMovie,
  routes,
  stubWidth,
  typeMap,
} from './test/fixtures';

beforeEach(() => {
  FakeEventSource.reset();
  vi.stubGlobal('EventSource', FakeEventSource);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

/** Renders the app against a stubbed backend. `put` answers the admin writes. */
async function open({ narrow = false, put = () => Response.json({}), ...overrides } = {}) {
  const setNarrow = stubWidth(narrow);
  const fetchStub = stubFetch(
    routes({
      '/api/config/hue-scene': (url, init) => put(url, init),
      '/api/config/plex-commands/movie': (url, init) => put(url, init),
      '/api/config/plex-commands/episode': (url, init) => put(url, init),
      '/api/config/plex-commands/clip': (url, init) => put(url, init),
      ...overrides,
    })
  );
  vi.stubGlobal('fetch', fetchStub);
  const user = userEvent.setup();
  render(<App />);
  await waitFor(() => expect(fetchStub.calls.length).toBeGreaterThanOrEqual(6));
  return { user, fetchStub, setNarrow };
}

const writes = (fetchStub) => fetchStub.calls.filter(({ init }) => init.method === 'PUT');
const lastBody = (fetchStub) => JSON.parse(writes(fetchStub).at(-1).init.body);

describe('Theater, desktop', () => {
  it('shows the kicker, title, meta, level ring, scenes and the last four events', async () => {
    await open();
    const theater = await screen.findByRole('region', { name: 'Theater mode' });

    expect(within(theater).getByText(/^Playing/)).toHaveTextContent('Playing · since 21:43');
    expect(within(theater).getByRole('heading', { name: 'Blade Runner 2049' })).toBeInTheDocument();
    expect(within(theater).getByText('2017 · Movie · Living Room Apple TV')).toBeInTheDocument();
    expect(within(theater).getByText('5%')).toBeInTheDocument();
    expect(within(theater).getByText('Room level')).toBeInTheDocument();

    const ring = within(theater).getByRole('img', { name: 'Room level 5%, scene Movie Night' });
    const arc = ring.querySelector('.ring-level');
    expect(arc).toHaveAttribute('r', '120');
    expect(arc).toHaveAttribute('stroke-dasharray', '37.7 754');

    expect(
      within(theater).getByRole('button', { name: 'On play · tap to change Movie Night' })
    ).toBeInTheDocument();
    expect(
      within(theater).getByRole('button', { name: 'On pause or stop · tap to change Relax' })
    ).toBeInTheDocument();

    const strip = within(theater).getAllByRole('listitem');
    expect(strip).toHaveLength(4);
    expect(strip[0]).toHaveTextContent('21:43:31 · media.resume');
    expect(strip[0]).toHaveTextContent('Blade Runner 2049');
    expect(strip[0]).toHaveTextContent('→ Movie Night');
    expect(strip[1]).toHaveTextContent('→ Relax');
    expect(strip[2]).toHaveTextContent('Slow Horses · S4 E2');
    expect(strip[2]).toHaveTextContent('Ignored · rule off');
    expect(strip[3]).toHaveTextContent('Vangelis · Tears in Rain');
    expect(strip[3]).toHaveTextContent('Failed');
  });

  it('shows no simulate buttons', async () => {
    await open();
    await screen.findByRole('region', { name: 'Theater mode' });
    ['Play', 'Pause', 'Resume', 'Stop'].forEach((name) =>
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument()
    );
  });

  it('shows the scene name alone when the scene has no level', async () => {
    await open({
      '/api/playback/now': { ...playingMovie, sceneId: BRIGHT.id },
    });
    const theater = await screen.findByRole('region', { name: 'Theater mode' });

    expect(within(theater).getByRole('img', { name: 'Scene Bright' })).toBeInTheDocument();
    expect(within(theater).queryByText(/%/)).not.toBeInTheDocument();
    expect(within(theater).queryByText('Room level')).not.toBeInTheDocument();
    expect(within(theater).getByRole('img').querySelector('.ring-level')).toHaveAttribute(
      'stroke-dasharray',
      '0.0 754'
    );
    expect(within(theater).getAllByText('Bright').length).toBeGreaterThan(0);
  });

  it('writes an episode as show, season and episode and drops a missing meta part', async () => {
    await open({
      '/api/playback/now': {
        ...playingMovie,
        mediaType: 'episode',
        title: 'Strange Games',
        grandparentTitle: 'Slow Horses',
        parentIndex: 4,
        index: 2,
        year: null,
      },
    });
    const theater = await screen.findByRole('region', { name: 'Theater mode' });

    expect(
      within(theater).getByRole('heading', { name: 'Slow Horses · S4 E2' })
    ).toBeInTheDocument();
    expect(within(theater).getByText('Episode · Living Room Apple TV')).toBeInTheDocument();
  });

  it('does not invent a scene when none has been applied yet', async () => {
    await open({ '/api/playback/now': { ...playingMovie, sceneId: null } });
    const theater = await screen.findByRole('region', { name: 'Theater mode' });

    expect(within(theater).getByRole('img', { name: 'No scene applied yet' })).toBeInTheDocument();
    expect(within(theater).getByText('No scene applied yet')).toBeInTheDocument();
  });

  it('names a scene the catalog does not know as unknown rather than guessing', async () => {
    await open({ '/api/playback/now': { ...playingMovie, sceneId: 'gone' } });
    const theater = await screen.findByRole('region', { name: 'Theater mode' });
    expect(within(theater).getByRole('img', { name: 'Scene Scene unknown' })).toBeInTheDocument();
  });

  it('shows the empty message when nothing has been recorded', async () => {
    await open({ '/api/playback/history': [] });
    const theater = await screen.findByRole('region', { name: 'Theater mode' });
    expect(within(theater).getByText('Nothing has played yet.')).toBeInTheDocument();
    expect(within(theater).queryAllByRole('listitem')).toHaveLength(0);
  });

  it('manual Theater while idle shows an idle ring, not a made-up item', async () => {
    const { user } = await open({ '/api/playback/now': idle });
    await user.click(await screen.findByRole('button', { name: 'Theater mode' }));
    const theater = screen.getByRole('region', { name: 'Theater mode' });

    expect(within(theater).getByText('Idle')).toBeInTheDocument();
    expect(within(theater).getByRole('heading', { name: 'Nothing playing' })).toBeInTheDocument();
    expect(within(theater).queryByText(/since/)).not.toBeInTheDocument();
  });

  it('cycles the "on play" scene optimistically within the same room first', async () => {
    const { user, fetchStub } = await open();
    const row = await screen.findByRole('button', { name: /On play/ });

    await user.click(row);

    expect(screen.getByRole('button', { name: /On play/ })).toHaveTextContent('Relax');
    const [call] = writes(fetchStub);
    expect(call.url).toBe('/api/config/hue-scene');
    expect(lastBody(fetchStub)).toEqual({ key: 'hue.start-scene', sceneId: RELAX.id });
  });

  it('cycles the "on pause or stop" scene on through the other rooms, then wraps', async () => {
    const { user, fetchStub } = await open();

    await user.click(await screen.findByRole('button', { name: /On pause or stop/ }));
    expect(lastBody(fetchStub)).toEqual({ key: 'hue.stop-scene', sceneId: BRIGHT.id });
    await user.click(screen.getByRole('button', { name: /On pause or stop/ }));
    expect(lastBody(fetchStub)).toEqual({ key: 'hue.stop-scene', sceneId: MOVIE_NIGHT.id });
    await user.click(screen.getByRole('button', { name: /On pause or stop/ }));
    expect(lastBody(fetchStub)).toEqual({ key: 'hue.stop-scene', sceneId: RELAX.id });
  });

  it('rolls the scene back when the server refuses the write', async () => {
    const { user } = await open({ put: () => new Response('', { status: 500 }) });

    await user.click(await screen.findByRole('button', { name: /On play/ }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /On play/ })).toHaveTextContent('Movie Night')
    );
    expect(screen.getByRole('status')).toHaveTextContent('That change was not saved.');
  });

  it('rolls the scene back when the login dialog is cancelled', async () => {
    const { user } = await open({ put: () => new Response('', { status: 401 }) });

    await user.click(await screen.findByRole('button', { name: /On play/ }));
    expect(await screen.findByRole('heading', { name: 'Sign in to make changes' })).toBeVisible();
    expect(screen.getByRole('button', { name: /On play/ })).toHaveTextContent('Relax');

    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /On play/ })).toHaveTextContent('Movie Night')
    );
  });

  it('keeps a newer config from another dashboard when an older write fails', async () => {
    const { user } = await open({ put: () => new Response('', { status: 500 }) });
    await user.click(await screen.findByRole('button', { name: /On play/ }));
    act(() =>
      FakeEventSource.last.emit('config', {
        ...config,
        scenes: { start: BRIGHT.id, stop: RELAX.id },
      })
    );

    await waitFor(() => expect(screen.getByRole('status')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /On play/ })).toHaveTextContent('Bright');
  });

  it('follows a scene changed on another dashboard (config event)', async () => {
    await open();
    await screen.findByRole('region', { name: 'Theater mode' });

    act(() =>
      FakeEventSource.last.emit('config', {
        ...config,
        scenes: { start: MOVIE_NIGHT.id, stop: BRIGHT.id },
      })
    );

    expect(
      await screen.findByRole('button', { name: 'On pause or stop · tap to change Bright' })
    ).toBeInTheDocument();
  });

  it('updates the ring and strip from now-playing and message events', async () => {
    await open();
    await screen.findByRole('region', { name: 'Theater mode' });

    act(() => {
      FakeEventSource.last.emit('now-playing', { ...playingMovie, sceneId: RELAX.id });
      FakeEventSource.last.emit('message', {
        id: 5,
        timestamp: at(22, 1, 3),
        event: 'media.play',
        mediaType: 'movie',
        title: 'Heat',
        outcome: 'IGNORED_UNSUPPORTED',
      });
    });

    expect(await screen.findByRole('img', { name: 'Room level 30%, scene Relax' })).toBeVisible();
    const strip = screen.getAllByRole('listitem');
    expect(strip[0]).toHaveTextContent('22:01:03 · media.play');
    expect(strip[0]).toHaveTextContent('Ignored · not supported');
    expect(strip).toHaveLength(4);
  });
});

describe('Theater, phone', () => {
  it('shortens the labels, hides the level caption and puts the status note at the foot', async () => {
    await open({ narrow: true });
    const theater = await screen.findByRole('region', { name: 'Theater mode' });

    expect(
      within(theater).getByRole('button', { name: 'On play Movie Night' })
    ).toBeInTheDocument();
    expect(within(theater).getByRole('button', { name: 'On stop Relax' })).toBeInTheDocument();
    expect(within(theater).queryByText('Room level')).not.toBeInTheDocument();
    expect(within(theater).getByText('5%')).toBeInTheDocument();
    expect(within(theater).getByText('Switches automatically with playback')).toBeInTheDocument();
    expect(within(theater).getAllByRole('listitem')).toHaveLength(4);
  });

  it('follows a window resize from desktop to phone', async () => {
    const { setNarrow } = await open();
    await screen.findByText('Room level');

    act(() => setNarrow(true));

    await waitFor(() => expect(screen.queryByText('Room level')).not.toBeInTheDocument());
  });
});

describe('House rules, desktop', () => {
  async function rules(overrides) {
    const result = await open({ '/api/playback/now': idle, ...overrides });
    const region = await screen.findByRole('region', { name: 'House rules' });
    await waitFor(() => expect(region).not.toHaveTextContent('Loading the rules'));
    return { ...result, region };
  }

  it('shows the heading, the summary sentence and one sentence per media type', async () => {
    const { region } = await rules();

    expect(within(region).getByRole('heading', { name: 'When the lights go down' })).toBeVisible();
    expect(within(region).getByText('The house rules')).toBeVisible();
    expect(within(region).getByRole('button', { name: 'Movie Night' })).toBeInTheDocument();
    expect(within(region).getByRole('button', { name: 'Relax' })).toBeInTheDocument();
    const rows = within(region).getAllByRole('listitem').slice(0, 5);
    expect(rows).toHaveLength(5);
    expect(rows[0]).toHaveTextContent('When a movie plays or resumes, dim the room.');
    expect(rows[0]).toHaveTextContent('When it pauses or stops, bring them back.');
    expect(rows[1]).toHaveTextContent('When an episode');
    expect(rows[4]).toHaveTextContent('When a photo slideshow');
  });

  it('marks on, partly and off phrases, and the row tag', async () => {
    const { region } = await rules();
    const [movie, episode, clip] = within(region).getAllByRole('listitem');

    const moviePlays = within(movie).getByRole('button', { name: /plays or resumes/ });
    expect(moviePlays).toHaveAttribute('aria-pressed', 'true');
    expect(within(movie).getByText('On')).toBeInTheDocument();

    const episodeStops = within(episode).getByRole('button', { name: /pauses or stops/ });
    expect(episodeStops).toHaveAttribute('aria-pressed', 'mixed');
    expect(episodeStops).toHaveAttribute('title', 'Switched off for: scrobble');
    expect(episodeStops).toHaveTextContent('partly');
    expect(episodeStops).toHaveClass('phrase-mixed');
    expect(within(episode).getByRole('button', { name: /plays or resumes/ })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    expect(within(episode).getByText('On')).toBeInTheDocument();

    const clipPlays = within(clip).getByRole('button', { name: /plays or resumes/ });
    expect(clipPlays).toHaveAttribute('aria-pressed', 'false');
    expect(clipPlays).toHaveClass('phrase-off');
    expect(within(clip).getByText('Off')).toBeInTheDocument();
  });

  it('turns an off phrase on, sending the type’s full map, optimistically', async () => {
    const { user, fetchStub, region } = await rules();
    const clip = within(region).getAllByRole('listitem')[2];

    await user.click(within(clip).getByRole('button', { name: /plays or resumes/ }));

    expect(within(clip).getByRole('button', { name: /plays or resumes/ })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    expect(within(clip).getByText('On')).toBeInTheDocument();
    const [call] = writes(fetchStub);
    expect(call.url).toBe('/api/config/plex-commands/clip');
    expect(lastBody(fetchStub)).toEqual(
      typeMap('clip', false, {
        'plex.clip-play-enabled': true,
        'plex.clip-resume-enabled': true,
      })
    );
  });

  it('turns a mixed phrase fully on, scrobble included', async () => {
    const { user, fetchStub, region } = await rules();
    const episode = within(region).getAllByRole('listitem')[1];

    await user.click(within(episode).getByRole('button', { name: /pauses or stops/ }));

    expect(lastBody(fetchStub)).toEqual(typeMap('episode', true));
    expect(within(episode).getByRole('button', { name: /pauses or stops/ })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
  });

  it('turns an on phrase fully off', async () => {
    const { user, fetchStub, region } = await rules();
    const movie = within(region).getAllByRole('listitem')[0];

    await user.click(within(movie).getByRole('button', { name: /pauses or stops/ }));

    expect(lastBody(fetchStub)).toEqual(
      typeMap('movie', true, {
        'plex.movie-pause-enabled': false,
        'plex.movie-stop-enabled': false,
        'plex.movie-scrobble-enabled': false,
      })
    );
    expect(within(movie).getByRole('button', { name: /pauses or stops/ })).toHaveClass(
      'phrase-off'
    );
  });

  it('a failed write rolls the phrase back', async () => {
    const { user, region } = await rules({ put: () => new Response('', { status: 500 }) });
    const clip = within(region).getAllByRole('listitem')[2];

    await user.click(within(clip).getByRole('button', { name: /plays or resumes/ }));

    await waitFor(() =>
      expect(within(clip).getByRole('button', { name: /plays or resumes/ })).toHaveAttribute(
        'aria-pressed',
        'false'
      )
    );
    expect(within(clip).getByText('Off')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('not saved');
  });

  it('a cancelled login rolls the phrase back', async () => {
    const { user, region } = await rules({ put: () => new Response('', { status: 401 }) });
    const movie = within(region).getAllByRole('listitem')[0];

    await user.click(within(movie).getByRole('button', { name: /plays or resumes/ }));
    expect(within(movie).getByRole('button', { name: /plays or resumes/ })).toHaveAttribute(
      'aria-pressed',
      'false'
    );
    await user.click(await screen.findByRole('button', { name: 'Cancel' }));

    await waitFor(() =>
      expect(within(movie).getByRole('button', { name: /plays or resumes/ })).toHaveAttribute(
        'aria-pressed',
        'true'
      )
    );
  });

  it('cycles the summary scenes', async () => {
    const { user, fetchStub, region } = await rules();

    await user.click(within(region).getByRole('button', { name: 'Movie Night' }));

    expect(lastBody(fetchStub)).toEqual({ key: 'hue.start-scene', sceneId: RELAX.id });
    expect(within(region).getAllByRole('button', { name: 'Relax' })).toHaveLength(2);
  });

  it('shows the Now rail and the programme, labelled as since start', async () => {
    const { region } = await rules({ '/api/playback/now': { ...playingMovie, state: 'Paused' } });
    const rail = within(region).getByRole('complementary');

    expect(within(rail).getByText('Now')).toBeVisible();
    expect(within(rail).getByText('Paused · room at 5%, Movie Night')).toBeVisible();
    expect(within(rail).getByText("Tonight's programme")).toBeVisible();
    expect(within(rail).getByText('since CineLight started')).toBeVisible();
    const items = within(rail).getAllByRole('listitem');
    expect(items).toHaveLength(history.length);
    expect(items[0]).toHaveTextContent('21:43:31');
    expect(items[0]).toHaveTextContent('media.resume → Movie Night');
    expect(items[4]).toHaveTextContent('media.play Ignored · Hue off');
  });

  it('shows only the scene in Now when it has no level', async () => {
    const { region } = await rules({
      '/api/playback/now': { ...playingMovie, state: 'Paused', sceneId: BRIGHT.id },
    });
    expect(within(region).getByText('Paused · Bright')).toBeVisible();
  });

  it('shows Idle and the empty message with no history', async () => {
    const { region } = await rules({ '/api/playback/history': [] });
    const rail = within(region).getByRole('complementary');
    expect(within(rail).getByText('Nothing playing')).toBeVisible();
    expect(within(rail).getByText('Idle')).toBeVisible();
    expect(within(rail).getByText('Nothing has played yet.')).toBeVisible();
  });

  it('re-renders when another dashboard changes a rule (config event)', async () => {
    const { region } = await rules();
    const photo = within(region).getAllByRole('listitem')[4];
    expect(within(photo).getByText('Off')).toBeInTheDocument();

    act(() =>
      FakeEventSource.last.emit('config', {
        ...config,
        plexCommands: { ...config.plexCommands, photo: typeMap('photo', true) },
      })
    );

    await waitFor(() => expect(within(photo).getByText('On')).toBeInTheDocument());
  });

  it('says the rules are loading while the config is unavailable', async () => {
    await open({ '/api/playback/now': idle, '/api/config': undefined });
    const region = await screen.findByRole('region', { name: 'House rules' });
    expect(region).toHaveTextContent('Loading the rules…');
  });
});

describe('House rules, phone', () => {
  async function phoneRules(overrides) {
    await open({ narrow: true, '/api/playback/now': idle, ...overrides });
    const region = await screen.findByRole('region', { name: 'House rules' });
    await within(region).findAllByRole('listitem');
    return region;
  }

  it('uses the short sentence and puts the last four events under the rules, with no rail', async () => {
    const region = await phoneRules();

    expect(within(region).queryByRole('complementary')).not.toBeInTheDocument();
    expect(within(region).queryByRole('heading')).not.toBeInTheDocument();
    const rows = within(region).getAllByRole('listitem');
    expect(rows).toHaveLength(9);
    expect(rows[0]).toHaveTextContent('When a movie plays, dim. When it stops, restore.');
    expect(within(rows[0]).getByRole('button', { name: 'a movie plays' })).toBeInTheDocument();
    expect(rows[5]).toHaveTextContent('21:43:31 media.resume');
    expect(rows[5]).toHaveTextContent('→ Movie Night');
    expect(rows[8]).toHaveTextContent('Failed');
  });

  it('names the partly state in the short label too', async () => {
    const region = await phoneRules();
    const [, episode] = within(region).getAllByRole('listitem');
    expect(
      within(episode).getByRole('button', { name: 'an episode stops, partly' })
    ).toHaveAttribute('aria-pressed', 'mixed');
  });

  it('shows the empty message below the rules when nothing has played', async () => {
    const region = await phoneRules({ '/api/playback/history': [] });
    expect(within(region).getByText('Nothing has played yet.')).toBeVisible();
  });
});

describe('without a scene catalog (no Hue bridge)', () => {
  const noCatalog = {
    '/api/hue/scenes': {
      scenes: [],
      start: { id: 'abc-start', summary: null },
      stop: { id: null, summary: null },
    },
    '/api/config': {
      ...config,
      scenes: { start: 'abc-start', stop: null },
    },
  };

  it('Rules shows the configured id or "Not set" and cannot cycle', async () => {
    const { user, fetchStub } = await open({ '/api/playback/now': idle, ...noCatalog });
    const start = await screen.findByRole('button', { name: 'abc-start' });
    const stop = screen.getByRole('button', { name: 'Not set' });

    expect(start).toHaveAttribute('aria-disabled', 'true');
    expect(start).toHaveAttribute('title', expect.stringMatching(/not available/));
    await user.click(start);
    await user.click(stop);

    expect(writes(fetchStub)).toHaveLength(0);
  });

  it('Theater shows the same and no level, and does not cycle', async () => {
    const { user, fetchStub } = await open({
      ...noCatalog,
      '/api/playback/now': { ...playingMovie, sceneId: 'abc-start' },
    });
    const theater = await screen.findByRole('region', { name: 'Theater mode' });
    const row = within(theater).getByRole('button', { name: /On play/ });

    expect(row).toHaveTextContent('abc-start');
    expect(row).toHaveAttribute('aria-disabled', 'true');
    expect(within(theater).queryByText(/%/)).not.toBeInTheDocument();
    await user.click(row);
    expect(writes(fetchStub)).toHaveLength(0);
    expect(within(theater).getByRole('button', { name: /On pause or stop/ })).toHaveTextContent(
      'Not set'
    );
  });

  it('survives a catalog that never loaded at all', async () => {
    await open({ '/api/playback/now': idle, '/api/hue/scenes': undefined });
    expect(await screen.findByRole('button', { name: MOVIE_NIGHT.id })).toBeInTheDocument();
  });
});

describe('mode switching with the real views', () => {
  it('goes Theater to House rules and back, and a media event returns to auto', async () => {
    const { user } = await open();
    await screen.findByRole('region', { name: 'Theater mode' });

    await user.click(screen.getByRole('button', { name: 'House rules' }));
    expect(screen.getByRole('region', { name: 'House rules' })).toBeInTheDocument();

    act(() =>
      FakeEventSource.last.emit('message', {
        id: 9,
        timestamp: at(22, 5, 0),
        event: 'media.play',
        title: 'Heat',
        outcome: 'SCENE_APPLIED',
        sceneId: RELAX.id,
      })
    );
    expect(await screen.findByRole('region', { name: 'Theater mode' })).toBeInTheDocument();
  });

  it('uses no raw HTML: a hostile title is rendered as text', async () => {
    await open({
      '/api/playback/now': { ...playingMovie, title: '<img src=x onerror=alert(1)>' },
    });
    const theater = await screen.findByRole('region', { name: 'Theater mode' });
    expect(theater.querySelector('img')).toBeNull();
    expect(
      within(theater).getByRole('heading', { name: '<img src=x onerror=alert(1)>' })
    ).toBeVisible();
  });
});

describe('catalog fixture', () => {
  it('is what the tests think it is', () => {
    expect(catalog.scenes.map((s) => s.name)).toEqual(['Movie Night', 'Bright', 'Relax']);
  });
});
