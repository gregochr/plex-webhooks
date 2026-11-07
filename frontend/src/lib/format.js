// Display formatting for the Theater and House rules views. Pure, so every rule is unit-tested.

const pad = (n) => String(n).padStart(2, '0');

function parse(iso) {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** `HH:MM` in the viewer's local time; an empty string when there is no usable timestamp. */
export function formatClock(iso) {
  const date = parse(iso);
  return date ? `${pad(date.getHours())}:${pad(date.getMinutes())}` : '';
}

/** `HH:MM:SS` in the viewer's local time; an empty string when there is no usable timestamp. */
export function formatClockSeconds(iso) {
  const date = parse(iso);
  return date ? `${formatClock(iso)}:${pad(date.getSeconds())}` : '';
}

const MEDIA_TYPE_LABELS = {
  movie: 'Movie',
  episode: 'Episode',
  clip: 'Clip',
  track: 'Track',
  photo: 'Photo',
};

/**
 * What to call an item: a movie by its title, an episode as `Show · S4 E2`, a track as `Artist · Title`.
 * Falls back to whatever parts exist, and to "Unknown title" when there are none.
 */
export function displayTitle(item) {
  const { mediaType, title, grandparentTitle, parentIndex, index } = item ?? {};
  let text = title;
  if (mediaType === 'episode' && grandparentTitle) {
    if (parentIndex != null && index != null) {
      text = `${grandparentTitle} · S${parentIndex} E${index}`;
    } else {
      text = title ? `${grandparentTitle} · ${title}` : grandparentTitle;
    }
  } else if (mediaType === 'track' && grandparentTitle) {
    text = title ? `${grandparentTitle} · ${title}` : grandparentTitle;
  }
  return text || 'Unknown title';
}

/** `2017 · Movie · Living Room Apple TV`, leaving out whatever is missing. */
export function metaLine(item) {
  const { year, mediaType, player } = item ?? {};
  const type = mediaType ? (MEDIA_TYPE_LABELS[mediaType] ?? mediaType) : null;
  return [year, type, player].filter((part) => part != null && part !== '').join(' · ');
}
