import { useState } from 'react';
import PropTypes from 'prop-types';
import { sendTestEvent } from '../api/debug';
import { apiGet } from '../api/http';
import { shortId } from '../lib/hueResources';
import ScreenFrame from './ScreenFrame';

const EVENTS = ['play', 'pause', 'resume', 'stop', 'scrobble'];
const TYPES = ['movie', 'episode', 'clip', 'track', 'photo'];
const cap = (word) => word.charAt(0).toUpperCase() + word.slice(1);

function Toggle({ id, label, hint, checked, onChange }) {
  return (
    <div className="toggle">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <label htmlFor={id}>
        <span className="toggle-label">{label}</span>
        <span className="toggle-hint">{hint}</span>
      </label>
    </div>
  );
}

Toggle.propTypes = {
  id: PropTypes.string.isRequired,
  label: PropTypes.string.isRequired,
  hint: PropTypes.string.isRequired,
  checked: PropTypes.bool.isRequired,
  onChange: PropTypes.func.isRequired,
};

function TestConnection() {
  const [result, setResult] = useState(null);
  const run = async () => {
    setResult({ ok: true, text: 'Testing…' });
    try {
      const body = await apiGet('/api/hue/test-connection');
      setResult(
        body?.connected
          ? { ok: true, text: `Connected to bridge ${shortId(body.hubId)}.` }
          : { ok: false, text: 'Not connected to the Hue bridge.' }
      );
    } catch {
      setResult({ ok: false, text: 'The connection test failed.' });
    }
  };
  return (
    <div className="debug-block">
      <button type="button" className="btn btn-secondary" onClick={run}>
        Test Hue connection
      </button>
      {result && (
        <p role="status" className={result.ok ? 'save-note' : 'save-note is-problem'}>
          {result.text}
        </p>
      )}
    </div>
  );
}

function SendEvent() {
  const [event, setEvent] = useState('play');
  const [type, setType] = useState('movie');
  const [title, setTitle] = useState('');
  const [result, setResult] = useState(null);

  const send = async (submit) => {
    submit.preventDefault();
    setResult(await sendTestEvent(event, type, title));
  };

  return (
    <form className="debug-block send-event" onSubmit={send}>
      <div className="field">
        <label htmlFor="test-event">Event</label>
        <select
          id="test-event"
          className="input"
          value={event}
          onChange={(e) => setEvent(e.target.value)}
        >
          {EVENTS.map((name) => (
            <option key={name} value={name}>
              {cap(name)}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label htmlFor="test-type">Media type</label>
        <select
          id="test-type"
          className="input"
          value={type}
          onChange={(e) => setType(e.target.value)}
        >
          {TYPES.map((name) => (
            <option key={name} value={name}>
              {cap(name)}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label htmlFor="test-title">Title (optional)</label>
        <input
          id="test-title"
          className="input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
      </div>
      <button type="submit" className="btn btn-primary">
        Send test event
      </button>
      {result && (
        <p role="status" className={result.ok ? 'save-note' : 'save-note is-problem'}>
          {result.text}
        </p>
      )}
    </form>
  );
}

/** Debug: switches and tools for working on the Hue set-up. Only reachable when the admin tab is enabled. */
export default function Debug({
  loaded,
  adminTabEnabled,
  hueEnabled,
  showAll,
  onSetHueEnabled,
  onSetShowAll,
}) {
  if (!loaded) {
    return (
      <ScreenFrame title="Debug">
        <p className="rail-empty">Loading…</p>
      </ScreenFrame>
    );
  }
  if (!adminTabEnabled) {
    return (
      <ScreenFrame title="Debug">
        <p role="status" className="rail-empty">
          Debug is off. Enable it with dashboard.admin-tab-enabled.
        </p>
      </ScreenFrame>
    );
  }
  return (
    <ScreenFrame
      title="Debug"
      lede="Tools for trying the Hue set-up. Test events run the real lights."
    >
      <div className="debug-layout">
        <div>
          <Toggle
            id="toggle-hue"
            label="Integrate Hue"
            hint="Off stops CineLight changing the lights."
            checked={hueEnabled}
            onChange={onSetHueEnabled}
          />
          <Toggle
            id="toggle-all-types"
            label="Show all Hue resource types"
            hint="A setting of this browser: the Lighting screen lists every type the bridge reports."
            checked={showAll}
            onChange={onSetShowAll}
          />
          <TestConnection />
        </div>
        <div>
          <h3 className="section-title">Send test event</h3>
          <SendEvent />
        </div>
      </div>
    </ScreenFrame>
  );
}

Debug.propTypes = {
  loaded: PropTypes.bool.isRequired,
  adminTabEnabled: PropTypes.bool.isRequired,
  hueEnabled: PropTypes.bool.isRequired,
  showAll: PropTypes.bool.isRequired,
  onSetHueEnabled: PropTypes.func.isRequired,
  onSetShowAll: PropTypes.func.isRequired,
};
