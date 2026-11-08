import { useState } from 'react';
import PropTypes from 'prop-types';
import { sendTestEvent } from '../api/debug';
import { describeEvent } from '../lib/events';
import { metaLine } from '../lib/format';
import ScreenFrame from './ScreenFrame';

const SIMULATE = ['play', 'pause', 'resume', 'stop'];
const EMPTY = 'Nothing has played yet.';

/** The payload as indented JSON; the server sends it as JSON already, but a string or nothing is handled too. */
export function prettyPayload(payload) {
  if (payload === null || payload === undefined || payload === '') {
    return null;
  }
  if (typeof payload === 'string') {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
      return payload;
    }
  }
  return JSON.stringify(payload, null, 2);
}

function Simulate() {
  const [result, setResult] = useState(null);
  const send = async (event) => setResult(await sendTestEvent(event, 'movie'));
  return (
    <div className="simulate">
      <span className="simulate-label">Simulate</span>
      {SIMULATE.map((event) => (
        <button key={event} type="button" className="btn btn-secondary" onClick={() => send(event)}>
          {event.charAt(0).toUpperCase() + event.slice(1)}
        </button>
      ))}
      {result && (
        <p role="status" className={`simulate-result ${result.ok ? '' : 'is-problem'}`}>
          {result.text}
        </p>
      )}
    </div>
  );
}

function Selector({ event, text, selected, onSelect, children }) {
  return (
    <button
      type="button"
      className="event-select"
      aria-pressed={selected}
      aria-label={`Show payload for ${event.name} ${text}`}
      onClick={onSelect}
    >
      {children}
    </button>
  );
}

Selector.propTypes = {
  event: PropTypes.object.isRequired,
  text: PropTypes.string.isRequired,
  selected: PropTypes.bool.isRequired,
  onSelect: PropTypes.func.isRequired,
  children: PropTypes.node.isRequired,
};

function Detail({ item }) {
  if (!item) {
    return <p className="rail-empty">Select an event to see its payload.</p>;
  }
  const payload = prettyPayload(item.payload);
  return (
    <div>
      <h3 className="detail-title">{item.view.title}</h3>
      <dl className="detail-facts">
        <dt>Event</dt>
        <dd>{item.view.name}</dd>
        <dt>Player</dt>
        <dd>{item.player || 'Unknown'}</dd>
      </dl>
      <h4 className="rail-heading">Payload</h4>
      {payload ? (
        <pre className="payload">{payload}</pre>
      ) : (
        <p className="rail-empty">No payload recorded.</p>
      )}
    </div>
  );
}

Detail.propTypes = { item: PropTypes.object };

/** Event monitor: the last 100 media events and what CineLight did, with the selected event's payload. */
export default function EventMonitor({ history, catalog, adminTabEnabled, narrow }) {
  const [selectedId, setSelectedId] = useState(null);
  const rows = history.map((event) => ({
    raw: event,
    view: describeEvent(event, catalog),
    media: metaLine({ mediaType: event.mediaType }),
  }));
  const selected = rows.find((row) => row.raw.id === selectedId);
  const item = selected ? { ...selected.raw, view: selected.view } : null;

  const tag = (row) =>
    row.raw.source === 'debug' && <span className="tag tag-outline test-tag">test</span>;
  const lights = (row) => (
    <span className={`tone-${row.view.outcome.tone}`}>{row.view.outcome.text}</span>
  );

  return (
    <ScreenFrame
      title="Plex event monitor"
      lede="The last 100 events from Plex and what CineLight did about each."
    >
      {adminTabEnabled && <Simulate />}
      {rows.length === 0 ? (
        <p className="rail-empty">{EMPTY}</p>
      ) : (
        <div className="monitor">
          {narrow ? (
            <ul className="event-list" aria-label="Events">
              {rows.map((row) => (
                <li key={row.raw.id} className="event-card">
                  <Selector
                    event={row.view}
                    text={row.view.title}
                    selected={row.raw.id === selectedId}
                    onSelect={() => setSelectedId(row.raw.id)}
                  >
                    <span className="event-card-head">
                      <span className="tnum">{row.view.time}</span>
                      <span>{row.view.name}</span>
                      {tag(row)}
                    </span>
                    <span className="event-card-title">{row.view.title}</span>
                    {lights(row)}
                  </Selector>
                </li>
              ))}
            </ul>
          ) : (
            <div className="table-scroll">
              <table className="table event-table">
                <thead>
                  <tr>
                    <th scope="col">Time</th>
                    <th scope="col">Event</th>
                    <th scope="col">Media</th>
                    <th scope="col">Title</th>
                    <th scope="col">Lights</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.raw.id} className={row.raw.id === selectedId ? 'is-selected' : ''}>
                      <td className="tnum">{row.view.time}</td>
                      <td>
                        {row.view.name} {tag(row)}
                      </td>
                      <td>{row.media}</td>
                      <td>
                        <Selector
                          event={row.view}
                          text={row.view.title}
                          selected={row.raw.id === selectedId}
                          onSelect={() => setSelectedId(row.raw.id)}
                        >
                          {row.view.title}
                        </Selector>
                      </td>
                      <td>{lights(row)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <aside className="monitor-detail" aria-label="Selected event">
            <Detail item={item} />
          </aside>
        </div>
      )}
    </ScreenFrame>
  );
}

EventMonitor.propTypes = {
  history: PropTypes.array.isRequired,
  catalog: PropTypes.object,
  adminTabEnabled: PropTypes.bool.isRequired,
  narrow: PropTypes.bool.isRequired,
};
