/** `auto` shows Theater while something is playing and House rules otherwise; `theater` and `rules` are manual. */
export function resolveView(view, playbackState) {
  if (view === 'auto') {
    return playbackState === 'Playing' ? 'theater' : 'rules';
  }
  return view;
}

/** The text beside the mode button. */
export function modeStatusText(view) {
  return view === 'auto'
    ? 'Switches automatically with playback'
    : 'Manual · switches back on the next event';
}
