import { useSyncExternalStore } from 'react';
import { routeFromHash } from '../lib/route';

function subscribe(notify) {
  window.addEventListener('hashchange', notify);
  return () => window.removeEventListener('hashchange', notify);
}

/** The current route (`mode` | `events` | `lighting` | `config` | `debug`); `hashchange` keeps it live, so Back works. */
export function useHashRoute() {
  return useSyncExternalStore(subscribe, () => routeFromHash(window.location.hash));
}
