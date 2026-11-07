/** The server keeps the last 100 media events; so do we. */
export const HISTORY_LIMIT = 100;

export const initialState = {
  nowPlaying: null,
  history: [],
  scenes: null,
  config: null,
  hueStatus: null,
  dirigeraStatus: null,
  // 'connecting' until the first open, then 'live' or 'reconnecting'.
  connection: 'connecting',
  lastHeartbeat: null,
  // Slices a live SSE event has already set: a slower initial GET must not overwrite them.
  live: {},
  // 'auto' | 'theater' | 'rules'; see lib/view.js.
  view: 'auto',
};

/** Merge two lists of events by id, newest first, capped. */
export function mergeHistory(current, incoming) {
  const byId = new Map();
  [...current, ...incoming].forEach((event) => byId.set(event.id, event));
  return [...byId.values()].sort((a, b) => b.id - a.id).slice(0, HISTORY_LIMIT);
}

function isMedia(event) {
  return typeof event.event === 'string' && event.event.startsWith('media.');
}

function live(state, key, data) {
  return { ...state, [key]: data, live: { ...state.live, [key]: true } };
}

/**
 * Seed: any subset of the keys {nowPlaying, history, scenes, config, hueStatus, dirigeraStatus}, as fetched.
 * An initial seed never overwrites a slice a live event has already set; a `force` seed (after a reconnect,
 * when events were missed) does.
 * Message: one SSE `message` event. Only media events enter the history, and a media event hands the
 * display back to `auto` (a manual mode lasts only until the next event).
 */
export function reducer(state, action) {
  switch (action.type) {
    case 'seed': {
      const { history, ...rest } = action.data;
      const fresh = action.force
        ? rest
        : Object.fromEntries(Object.entries(rest).filter(([key]) => !state.live[key]));
      return {
        ...state,
        ...fresh,
        history: history ? mergeHistory(state.history, history) : state.history,
      };
    }
    case 'message':
      if (!isMedia(action.event)) {
        return state;
      }
      return {
        ...state,
        history: mergeHistory(state.history, [action.event]),
        view: 'auto',
      };
    case 'now-playing':
      return live(state, 'nowPlaying', action.data);
    case 'config':
      return live(state, 'config', action.data);
    case 'hue-status':
      return live(state, 'hueStatus', action.data);
    case 'dirigera-status':
      return live(state, 'dirigeraStatus', action.data);
    case 'heartbeat':
      return { ...state, lastHeartbeat: action.at };
    case 'connection':
      return { ...state, connection: action.status };
    case 'set-view':
      return { ...state, view: action.view };
    default:
      return state;
  }
}
