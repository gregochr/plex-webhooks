// Plain-word labels for what CineLight did about an event (the backend's PlexOutcome kinds).

const IGNORED = {
  IGNORED_RULE_OFF: 'Ignored · rule off',
  IGNORED_HUE_DISABLED: 'Ignored · Hue off',
  IGNORED_UNSUPPORTED: 'Ignored · not supported',
  IGNORED_UNKNOWN_TYPE: 'Ignored · unknown media type',
};

/**
 * The label for an event's outcome and its tone (`applied` | `ignored` | `failed` | `unknown`).
 * `sceneName` is the activated scene's name, or null when the catalog does not know it.
 */
export function outcomeLabel(outcome, sceneName) {
  if (outcome === 'SCENE_APPLIED') {
    return { text: sceneName ? `→ ${sceneName}` : 'Scene applied', tone: 'applied' };
  }
  if (outcome === 'FAILED') {
    return { text: 'Failed', tone: 'failed' };
  }
  if (IGNORED[outcome]) {
    return { text: IGNORED[outcome], tone: 'ignored' };
  }
  return { text: 'No result recorded', tone: 'unknown' };
}
