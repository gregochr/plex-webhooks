import { useCallback, useEffect, useReducer } from 'react';
import { apiGet } from '../api/http';
import { initialState, reducer } from './reducer';

export const RECONNECT_BASE_MS = 1000;
export const RECONNECT_MAX_MS = 30000;

// Everything the page needs at load; each GET is independent, so one failing leaves the others in place.
const SEEDS = {
  nowPlaying: '/api/playback/now',
  history: '/api/playback/history?limit=100',
  scenes: '/api/hue/scenes',
  config: '/api/config',
  hueStatus: '/api/hue/status',
  dirigeraStatus: '/api/dirigera/status',
};

// SSE event name -> reducer action type, for the events whose data is the new value as is.
const DATA_EVENTS = {
  'now-playing': 'now-playing',
  config: 'config',
  'hue-status': 'hue-status',
  'dirigera-status': 'dirigera-status',
};

export function reconnectDelay(attempt) {
  return Math.min(RECONNECT_BASE_MS * 2 ** attempt, RECONNECT_MAX_MS);
}

const hex = (byte) => byte.toString(16).padStart(2, '0');

/**
 * A random client id that works on plain HTTP: crypto.randomUUID exists only in secure contexts, and the
 * dashboard is normally opened over http://<lan-ip>:8080. getRandomValues is available everywhere; Math.random
 * is the last resort when there is no crypto at all.
 */
export function newClientId() {
  const bytes = new Uint8Array(16);
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes);
  } else {
    bytes.forEach((_, i) => {
      bytes[i] = Math.floor(Math.random() * 256);
    });
  }
  return Array.from(bytes, hex).join('');
}

async function fetchSeeds(keys) {
  const entries = await Promise.all(
    keys.map(async (key) => {
      try {
        return [key, await apiGet(SEEDS[key])];
      } catch {
        return null; // graceful: the SSE stream or a later seed fills it in
      }
    })
  );
  return Object.fromEntries(entries.filter(Boolean));
}

function parse(raw) {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/**
 * The page's data: seeded from the GET endpoints, then kept live by one EventSource. Also owns the `view`
 * override (see lib/view.js), because a new media event resets it.
 */
export function useCineLight() {
  const [state, dispatch] = useReducer(reducer, initialState);

  useEffect(() => {
    let cancelled = false;
    let source = null;
    let timer = null;
    let attempt = 0;
    let everOpened = false;
    const clientId = newClientId();

    async function seed(keys, force = false) {
      const data = await fetchSeeds(keys);
      if (!cancelled) {
        dispatch({ type: 'seed', data, force });
      }
    }

    function connect() {
      source = new EventSource(`/api/events?clientId=${encodeURIComponent(clientId)}`);

      source.onopen = () => {
        attempt = 0;
        dispatch({ type: 'connection', status: 'live' });
        if (everOpened) {
          // Events were missed while we were away.
          seed(['history', 'nowPlaying'], true);
        }
        everOpened = true;
      };

      source.onerror = () => {
        source.close();
        dispatch({ type: 'connection', status: 'reconnecting' });
        timer = setTimeout(connect, reconnectDelay(attempt));
        attempt += 1;
      };

      source.addEventListener('message', (event) => {
        const data = parse(event.data);
        if (data) {
          dispatch({ type: 'message', event: data });
        }
      });

      Object.entries(DATA_EVENTS).forEach(([name, type]) => {
        source.addEventListener(name, (event) => {
          const data = parse(event.data);
          if (data) {
            dispatch({ type, data });
          }
        });
      });

      source.addEventListener('heartbeat', () => {
        dispatch({ type: 'heartbeat', at: Date.now() });
      });
    }

    seed(Object.keys(SEEDS));
    connect();

    return () => {
      cancelled = true;
      clearTimeout(timer);
      source.close();
    };
  }, []);

  const setView = useCallback((view) => dispatch({ type: 'set-view', view }), []);

  return { ...state, dispatch, setView };
}
