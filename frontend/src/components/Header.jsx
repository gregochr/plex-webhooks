import { useState } from 'react';
import PropTypes from 'prop-types';
import { modeStatusText } from '../lib/view';

/**
 * The wordmark, the mode status text, the mode button and the More menu. `resolved` is the view on screen
 * (`theater` | `rules`); `view` is the setting (`auto` | `theater` | `rules`).
 */
export default function Header({ view, resolved, onSetView }) {
  const [moreOpen, setMoreOpen] = useState(false);
  const toTheater = resolved === 'rules';

  return (
    <header className="app-header" data-mode={resolved}>
      <h1 className="wordmark">CineLight</h1>
      <p className="mode-status">{modeStatusText(view)}</p>
      <button
        type="button"
        className="btn btn-secondary mode-button"
        aria-label={toTheater ? 'Theater mode' : 'House rules'}
        onClick={() => onSetView(toTheater ? 'theater' : 'rules')}
      >
        <span className="label-long">{toTheater ? 'Theater mode' : 'House rules'}</span>
        <span className="label-short" aria-hidden="true">
          {toTheater ? 'Theater' : 'Rules'}
        </span>
      </button>
      <div className="more">
        <button
          type="button"
          className="btn btn-ghost more-button"
          aria-expanded={moreOpen}
          aria-controls="more-panel"
          onClick={() => setMoreOpen((open) => !open)}
        >
          More
        </button>
        {moreOpen && (
          <div id="more-panel" className="more-panel">
            The event monitor, lighting, configuration and debug screens arrive in a later release.
          </div>
        )}
      </div>
    </header>
  );
}

Header.propTypes = {
  view: PropTypes.oneOf(['auto', 'theater', 'rules']).isRequired,
  resolved: PropTypes.oneOf(['theater', 'rules']).isRequired,
  onSetView: PropTypes.func.isRequired,
};
