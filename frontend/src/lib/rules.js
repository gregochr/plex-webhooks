// The House rules: five media types, two phrase groups each, over the backend's per-event switches
// (`plex.<type>-<event>-enabled`).

export const MEDIA_TYPES = [
  { type: 'movie', noun: 'a movie' },
  { type: 'episode', noun: 'an episode' },
  { type: 'clip', noun: 'a clip' },
  { type: 'track', noun: 'a track' },
  { type: 'photo', noun: 'a photo slideshow' },
];

/** Scrobble belongs to "pauses or stops" and counts towards its state (unlike the prototype). */
export const GROUPS = {
  start: { events: ['play', 'resume'], long: 'plays or resumes', short: 'plays' },
  stop: { events: ['pause', 'stop', 'scrobble'], long: 'pauses or stops', short: 'stops' },
};

export const eventKey = (type, event) => `plex.${type}-${event}-enabled`;

/** `on` when every event in the group is on, `off` when none is, `mixed` otherwise. */
export function groupState(map, type, group) {
  const flags = GROUPS[group].events.map((event) => map?.[eventKey(type, event)] === true);
  if (flags.every(Boolean)) {
    return 'on';
  }
  return flags.some(Boolean) ? 'mixed' : 'off';
}

/** The events in a group that are switched off, for a mixed phrase's tooltip. */
export function offEvents(map, type, group) {
  return GROUPS[group].events.filter((event) => map?.[eventKey(type, event)] !== true);
}

/** A click: off or mixed turns the whole group on, on turns it all off. Returns the type's new full map. */
export function toggleGroup(map, type, group) {
  const turnOn = groupState(map, type, group) !== 'on';
  const next = { ...map };
  GROUPS[group].events.forEach((event) => {
    next[eventKey(type, event)] = turnOn;
  });
  return next;
}

/** `On` if either group is on or mixed, else `Off`. */
export function rowTag(map, type) {
  return groupState(map, type, 'start') === 'off' && groupState(map, type, 'stop') === 'off'
    ? 'Off'
    : 'On';
}
