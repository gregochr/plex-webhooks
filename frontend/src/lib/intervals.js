// Intervals are stored in milliseconds but shown and edited in whole seconds (the backend accepts 1 s to 24 h).

export const MIN_SECONDS = 1;
export const MAX_SECONDS = 86400;

/** The whole seconds shown for a stored millisecond value (string or number); '' when it is not a number. */
export function msToSeconds(value) {
  const ms = Number(value);
  return value === null || value === undefined || value === '' || !Number.isFinite(ms)
    ? ''
    : String(Math.round(ms / 1000));
}

/** A typed value as whole seconds in range, or null when it is not valid. */
export function parseSeconds(text) {
  if (!/^\d{1,6}$/.test(String(text).trim())) {
    return null;
  }
  const seconds = Number(text);
  return seconds >= MIN_SECONDS && seconds <= MAX_SECONDS ? seconds : null;
}

const differs = (item, drafts) =>
  drafts[item.key] !== undefined && drafts[item.key].trim() !== msToSeconds(item.value);

/**
 * What Save would send: `{key: "<ms>"}` for each item whose draft differs from the stored value. `drafts` maps a
 * key to the text typed so far; an untouched item has no draft.
 */
export function intervalChanges(items, drafts) {
  const changes = {};
  items
    .filter((item) => differs(item, drafts))
    .forEach((item) => {
      const seconds = parseSeconds(drafts[item.key]);
      if (seconds !== null) {
        changes[item.key] = String(seconds * 1000);
      }
    });
  return changes;
}

/** True when every draft that has been typed is a valid number of seconds. */
export function draftsValid(drafts) {
  return Object.values(drafts).every((text) => parseSeconds(text) !== null);
}
