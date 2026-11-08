// Per-browser preferences. localStorage can be missing or throw (private windows, blocked site data), so every
// access is guarded and the page works without it.

const SHOW_ALL_KEY = 'cinelight.showAllHueTypes';

export function readShowAllTypes() {
  try {
    return window.localStorage.getItem(SHOW_ALL_KEY) === 'true';
  } catch {
    return false;
  }
}

export function writeShowAllTypes(value) {
  try {
    window.localStorage.setItem(SHOW_ALL_KEY, String(value));
  } catch {
    // Not persisted; the choice still applies until the page is closed.
  }
}
