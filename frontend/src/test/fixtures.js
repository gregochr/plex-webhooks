import { vi } from 'vitest';

/** A local-time ISO timestamp, so formatted clocks do not depend on the machine's zone. */
export const at = (h, m, s = 0) => new Date(2024, 0, 15, h, m, s).toISOString();

const scene = (id, name, groupId, groupName, level) => ({
  id,
  name,
  groupId,
  groupName,
  groupType: 'room',
  level,
});

export const MOVIE_NIGHT = scene('scene-movie', 'Movie Night', 'room-lounge', 'Lounge', 5);
export const RELAX = scene('scene-relax', 'Relax', 'room-lounge', 'Lounge', 30);
export const BRIGHT = scene('scene-bright', 'Bright', 'room-kitchen', 'Kitchen', null);

export const catalog = {
  scenes: [MOVIE_NIGHT, BRIGHT, RELAX],
  start: { id: MOVIE_NIGHT.id, summary: MOVIE_NIGHT },
  stop: { id: RELAX.id, summary: RELAX },
};

/** The five per-event switches for a media type, all set to `value` unless overridden. */
export function typeMap(type, value, overrides = {}) {
  const base = Object.fromEntries(
    ['play', 'resume', 'stop', 'pause', 'scrobble'].map((event) => [
      `plex.${type}-${event}-enabled`,
      value,
    ])
  );
  return { ...base, ...overrides };
}

/** movie on; episode partly on (scrobble off, as by default); clip, track and photo off. */
export const config = {
  hueIntegrationEnabled: true,
  scenes: { start: MOVIE_NIGHT.id, stop: RELAX.id },
  plexCommands: {
    movie: typeMap('movie', true),
    episode: typeMap('episode', true, { 'plex.episode-scrobble-enabled': false }),
    clip: typeMap('clip', false),
    track: typeMap('track', false),
    photo: typeMap('photo', false),
  },
};

export const playingMovie = {
  state: 'Playing',
  since: at(21, 43, 10),
  mediaType: 'movie',
  title: 'Blade Runner 2049',
  grandparentTitle: null,
  parentIndex: null,
  index: null,
  year: 2017,
  player: 'Living Room Apple TV',
  sceneId: MOVIE_NIGHT.id,
};

export const idle = { state: 'Idle', since: null };

export const history = [
  {
    id: 4,
    timestamp: at(21, 43, 31),
    event: 'media.resume',
    mediaType: 'movie',
    title: 'Blade Runner 2049',
    outcome: 'SCENE_APPLIED',
    sceneId: MOVIE_NIGHT.id,
  },
  {
    id: 3,
    timestamp: at(21, 38, 26),
    event: 'media.pause',
    mediaType: 'movie',
    title: 'Blade Runner 2049',
    outcome: 'SCENE_APPLIED',
    sceneId: RELAX.id,
  },
  {
    id: 2,
    timestamp: at(19, 47, 59),
    event: 'media.stop',
    mediaType: 'episode',
    title: 'Strange Games',
    grandparentTitle: 'Slow Horses',
    parentIndex: 4,
    index: 2,
    outcome: 'IGNORED_RULE_OFF',
    sceneId: null,
  },
  {
    id: 1,
    timestamp: at(18, 50, 50),
    event: 'media.play',
    mediaType: 'track',
    title: 'Tears in Rain',
    grandparentTitle: 'Vangelis',
    outcome: 'FAILED',
    sceneId: null,
  },
  {
    id: 0,
    timestamp: at(18, 1, 2),
    event: 'media.play',
    mediaType: 'clip',
    title: 'Trailer',
    outcome: 'IGNORED_HUE_DISABLED',
    sceneId: null,
  },
];

/** Routes for stubFetch: the six seed GETs, each overridable (`undefined` gives a 404). */
export function routes(overrides = {}) {
  return {
    '/api/playback/now': playingMovie,
    '/api/playback/history': history,
    '/api/hue/scenes': catalog,
    '/api/config': config,
    '/api/hue/status': { connected: true },
    '/api/dirigera/status': { reachable: true },
    ...overrides,
  };
}

/**
 * Makes `window.matchMedia` report the phone layout (or not) for the 768px query. Returns `setNarrow`, which
 * changes the width the way resizing the window would.
 */
export function stubWidth(narrow) {
  let current = narrow;
  const listeners = new Set();
  vi.stubGlobal('matchMedia', (query) => ({
    get matches() {
      return current;
    },
    media: query,
    addEventListener: (_, listener) => listeners.add(listener),
    removeEventListener: (_, listener) => listeners.delete(listener),
  }));
  return (value) => {
    current = value;
    listeners.forEach((listener) => listener());
  };
}
