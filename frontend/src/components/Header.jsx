import PropTypes from 'prop-types';
import { modeStatusText } from '../lib/view';
import MoreMenu from './MoreMenu';

/**
 * The wordmark, the mode status text, the mode button and the More menu. `resolved` is the view on screen
 * (`theater` | `rules`); `view` is the setting (`auto` | `theater` | `rules`).
 */
export default function Header({ view, resolved, onSetView, adminTabEnabled }) {
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
      <MoreMenu adminTabEnabled={adminTabEnabled} />
    </header>
  );
}

Header.propTypes = {
  view: PropTypes.oneOf(['auto', 'theater', 'rules']).isRequired,
  resolved: PropTypes.oneOf(['theater', 'rules']).isRequired,
  onSetView: PropTypes.func.isRequired,
  adminTabEnabled: PropTypes.bool.isRequired,
};
