import { useState } from 'react';
import PropTypes from 'prop-types';
import { apiWrite } from '../api/http';
import {
  MAX_SECONDS,
  MIN_SECONDS,
  draftsValid,
  intervalChanges,
  msToSeconds,
  parseSeconds,
} from '../lib/intervals';
import { eventKey } from '../lib/rules';
import ScreenFrame from './ScreenFrame';

const EVENTS = ['play', 'resume', 'pause', 'stop', 'scrobble'];
const TYPES = [
  ['movie', 'Movie'],
  ['episode', 'TV show'],
  ['clip', 'Clip'],
  ['track', 'Track'],
  ['photo', 'Photo'],
];
const cap = (word) => word.charAt(0).toUpperCase() + word.slice(1);

function Matrix({ config, onToggle }) {
  return (
    <div className="table-scroll">
      <table className="table matrix">
        <thead>
          <tr>
            <th scope="col">Media type</th>
            {EVENTS.map((event) => (
              <th key={event} scope="col" className="matrix-cell">
                {cap(event)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {TYPES.map(([type, label]) => (
            <tr key={type}>
              <th scope="row">{label}</th>
              {EVENTS.map((event) => (
                <td key={event} className="matrix-cell">
                  <label className="matrix-check">
                    <input
                      type="checkbox"
                      checked={config.plexCommands?.[type]?.[eventKey(type, event)] === true}
                      onChange={() => onToggle(type, event)}
                    />
                    <span className="sr-only">{`${label} ${event}`}</span>
                  </label>
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

Matrix.propTypes = { config: PropTypes.object.isRequired, onToggle: PropTypes.func.isRequired };

function Intervals({ items, onSaved }) {
  const [drafts, setDrafts] = useState({});
  const [message, setMessage] = useState(null);
  const changes = intervalChanges(items, drafts);
  const canSave = Object.keys(changes).length > 0 && draftsValid(drafts);

  const save = async (event) => {
    event.preventDefault();
    try {
      await apiWrite('/api/config/properties', { body: changes });
      setDrafts({});
      setMessage({ ok: true, text: 'Intervals saved.' });
      onSaved();
    } catch {
      setMessage({ ok: false, text: 'The intervals were not saved.' });
    }
  };

  if (items.length === 0) {
    return <p className="rail-empty">No intervals can be edited here.</p>;
  }
  return (
    <form onSubmit={save} noValidate>
      <ul className="setting-list">
        {items.map((item) => {
          const text = drafts[item.key] ?? msToSeconds(item.value);
          const invalid = parseSeconds(text) === null;
          return (
            <li key={item.key} className="setting">
              <div className="setting-name">
                <label htmlFor={`interval-${item.key}`}>{item.label}</label>
                <span className="setting-key">{item.key}</span>
                {invalid && (
                  <span id={`error-${item.key}`} className="setting-error">
                    Whole seconds, {MIN_SECONDS} to {MAX_SECONDS}.
                  </span>
                )}
              </div>
              <input
                id={`interval-${item.key}`}
                className="input setting-input tnum"
                inputMode="numeric"
                value={text}
                aria-invalid={invalid}
                aria-describedby={invalid ? `error-${item.key}` : undefined}
                onChange={(event) => {
                  setMessage(null);
                  setDrafts({ ...drafts, [item.key]: event.target.value });
                }}
              />
              <span className="setting-unit">s</span>
            </li>
          );
        })}
      </ul>
      <div className="setting-actions">
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => {
            setDrafts({});
            setMessage(null);
          }}
        >
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={!canSave}>
          Save
        </button>
      </div>
      {message && (
        <p role="status" className={message.ok ? 'save-note' : 'save-note is-problem'}>
          {message.text}
        </p>
      )}
    </form>
  );
}

Intervals.propTypes = { items: PropTypes.array.isRequired, onSaved: PropTypes.func.isRequired };

/** Credentials: only whether each is set. A secret item's value is never read, so it cannot be rendered. */
function Credentials({ items }) {
  return (
    <>
      <ul className="setting-list">
        {items.map((item) => (
          <li key={item.key} className="setting setting-credential">
            <span className="setting-name">{item.label}</span>
            <span className={`tag ${item.set === true ? 'tag-accent' : 'tag-neutral'}`}>
              {item.set === true ? 'Set' : 'Not set'}
            </span>
          </li>
        ))}
      </ul>
      <p className="screen-note">
        Values are never sent to the browser. Change them in application.properties or the
        environment.
      </p>
    </>
  );
}

Credentials.propTypes = { items: PropTypes.array.isRequired };

/** Config: the per-event matrix, the intervals (whole seconds) and the credentials (Set / Not set). */
export default function Config({ config, sections, onToggle, onIntervalsSaved }) {
  const items = (sections?.sections ?? []).flatMap((section) => section.items ?? []);
  const intervals = items.filter((item) => item.writable && !item.secret);
  const credentials = items.filter((item) => item.secret);

  return (
    <ScreenFrame
      title="Which media cues the lights"
      lede="Play and Resume apply the start scene. Pause, Stop and Scrobble apply the stop scene."
    >
      <div className="config-layout">
        <div className="config-main">
          {config ? (
            <Matrix config={config} onToggle={onToggle} />
          ) : (
            <p className="rail-empty">Loading the rules…</p>
          )}
        </div>
        <div className="config-side">
          <h3 className="section-title">Intervals</h3>
          {sections ? (
            <Intervals items={intervals} onSaved={onIntervalsSaved} />
          ) : (
            <p className="rail-empty">Loading the settings…</p>
          )}
          <h3 className="section-title">Credentials</h3>
          {sections && <Credentials items={credentials} />}
        </div>
      </div>
    </ScreenFrame>
  );
}

Config.propTypes = {
  config: PropTypes.object,
  sections: PropTypes.object,
  onToggle: PropTypes.func.isRequired,
  onIntervalsSaved: PropTypes.func.isRequired,
};
