import { useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { SCREENS } from '../lib/route';

/**
 * The More disclosure: a button that opens a list of links to the screens. Escape closes it and returns focus to
 * the button; so does a click outside; following a link closes it. Debug is listed only when `adminTabEnabled`.
 */
export default function MoreMenu({ adminTabEnabled }) {
  const [open, setOpen] = useState(false);
  const root = useRef(null);
  const button = useRef(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onKey = (event) => {
      if (event.key === 'Escape') {
        setOpen(false);
        button.current?.focus();
      }
    };
    const onPointer = (event) => {
      if (!root.current.contains(event.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onPointer);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('mousedown', onPointer);
    };
  }, [open]);

  return (
    <div className="more" ref={root}>
      <button
        type="button"
        ref={button}
        className="btn btn-ghost more-button"
        aria-expanded={open}
        aria-controls="more-panel"
        onClick={() => setOpen((value) => !value)}
      >
        More
      </button>
      {open && (
        <nav id="more-panel" className="more-panel" aria-label="More">
          <ul className="more-list">
            {SCREENS.filter((screen) => screen.route !== 'debug' || adminTabEnabled).map(
              (screen) => (
                <li key={screen.route}>
                  <a className="more-link" href={screen.hash} onClick={() => setOpen(false)}>
                    {screen.label}
                  </a>
                </li>
              )
            )}
          </ul>
        </nav>
      )}
    </div>
  );
}

MoreMenu.propTypes = { adminTabEnabled: PropTypes.bool.isRequired };
