// The four More-menu screens are hash routes; any other hash is the mode view (Theater or House rules).

export const SCREENS = [
  { route: 'events', hash: '#/events', label: 'Event monitor' },
  { route: 'lighting', hash: '#/lighting', label: 'Lighting' },
  { route: 'config', hash: '#/config', label: 'Config' },
  { route: 'debug', hash: '#/debug', label: 'Debug' },
];

/** `events` | `lighting` | `config` | `debug` for a known hash, else `mode`. */
export function routeFromHash(hash) {
  return SCREENS.find((screen) => screen.hash === hash)?.route ?? 'mode';
}
