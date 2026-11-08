import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { ApiError, apiGet } from '../api/http';
import {
  filterResources,
  parseResources,
  shortId,
  typeLabel,
  typeOptions,
} from '../lib/hueResources';
import { slotShape } from './propShapes';
import ScreenFrame from './ScreenFrame';

const DASH = '—';

function Facts({ rows }) {
  return (
    <dl className="facts">
      {rows.map(([label, value]) => (
        <div key={label} className="fact">
          <dt>{label}</dt>
          <dd>{value || DASH}</dd>
        </div>
      ))}
    </dl>
  );
}

Facts.propTypes = { rows: PropTypes.array.isRequired };

const yesNo = (value) => (value === true ? 'Yes' : value === false ? 'No' : null);

function BridgeCards({ hueStatus, dirigeraStatus, hueEnabled }) {
  const hueState = hueStatus ? (hueStatus.connected ? 'Connected' : 'Not connected') : null;
  return (
    <div className="card-pair">
      <section className="card" aria-labelledby="hue-card">
        <h3 id="hue-card" className="card-title">
          Hue bridge
        </h3>
        <Facts
          rows={[
            ['Status', hueState],
            ['Hue commands enabled', yesNo(hueEnabled)],
            ['Hub address', hueStatus?.hubAddress],
            ['Bridge id', shortId(hueStatus?.hubId)],
            [
              'Application key',
              hueStatus ? (hueStatus.applicationKeySet ? 'Set' : 'Not set') : null,
            ],
          ]}
        />
      </section>
      <section className="card" aria-labelledby="dirigera-card">
        <h3 id="dirigera-card" className="card-title">
          DIRIGERA
        </h3>
        <Facts
          rows={[
            ['Reachable', yesNo(dirigeraStatus?.reachable)],
            ['Hub address', dirigeraStatus?.hubAddress],
          ]}
        />
      </section>
    </div>
  );
}

BridgeCards.propTypes = {
  hueStatus: PropTypes.object,
  dirigeraStatus: PropTypes.object,
  hueEnabled: PropTypes.bool,
};

function SceneCard({ heading, slot, name }) {
  return (
    <section className="card" aria-label={heading}>
      <p className="card-kicker">{heading}</p>
      <p className="card-title">{name}</p>
      {slot.level != null && <p className="card-meta">Room at {slot.level}%</p>}
    </section>
  );
}

SceneCard.propTypes = {
  heading: PropTypes.string.isRequired,
  slot: slotShape.isRequired,
  name: PropTypes.string.isRequired,
};

/** The Hue resources: search, type filter and, for a selected scene, Use on start / Use on stop. */
function Resources({ showAll, slots, onSetScene }) {
  const [state, setState] = useState({ status: 'loading', resources: [] });
  const [query, setQuery] = useState('');
  const [type, setType] = useState('all');
  const [selectedId, setSelectedId] = useState(null);

  useEffect(() => {
    let cancelled = false;
    apiGet('/api/hue/resources')
      .then((body) => !cancelled && setState({ status: 'ready', resources: parseResources(body) }))
      .catch(
        (error) =>
          !cancelled &&
          setState({
            status: error instanceof ApiError && error.status === 503 ? 'unreachable' : 'failed',
            resources: [],
          })
      );
    return () => {
      cancelled = true;
    };
  }, []);

  const options = typeOptions(state.resources, showAll);
  const shown = filterResources(state.resources, { query, type, showAll });
  const selected = state.resources.find((r) => r.id === selectedId);
  const tagFor = (resource) =>
    resource.type === 'scene'
      ? [slots.start.id === resource.id && 'Start', slots.stop.id === resource.id && 'Stop'].filter(
          Boolean
        )
      : [];

  if (state.status === 'loading') {
    return <p className="rail-empty">Loading the Hue resources…</p>;
  }
  if (state.status === 'unreachable') {
    return (
      <p role="status" className="rail-empty">
        Hue bridge unreachable.
      </p>
    );
  }
  if (state.status === 'failed') {
    return (
      <p role="status" className="rail-empty">
        The Hue resources could not be loaded.
      </p>
    );
  }

  return (
    <div>
      <div className="resource-tools">
        <div className="field">
          <label htmlFor="resource-search">Search resources</label>
          <input
            id="resource-search"
            className="input"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="resource-type">Type</label>
          <select
            id="resource-type"
            className="input"
            value={type}
            onChange={(event) => setType(event.target.value)}
          >
            {options.map((option) => (
              <option key={option} value={option}>
                {option === 'all' ? 'All' : typeLabel(option)}
              </option>
            ))}
          </select>
        </div>
      </div>
      {shown.length === 0 ? (
        <p className="rail-empty">No resources match.</p>
      ) : (
        <ul className="resource-list" aria-label="Hue resources">
          {shown.map((resource) => (
            <li key={resource.id}>
              <button
                type="button"
                className="resource-row"
                aria-pressed={resource.id === selectedId}
                onClick={() => setSelectedId(resource.id)}
              >
                <span className="resource-name">{resource.name || resource.id}</span>
                <span className="resource-type">{typeLabel(resource.type)}</span>
                {tagFor(resource).map((tag) => (
                  <span key={tag} className="tag tag-accent">
                    {tag}
                  </span>
                ))}
              </button>
            </li>
          ))}
        </ul>
      )}
      {selected?.type === 'scene' && (
        <div className="resource-actions">
          <p className="resource-selected">{selected.name || selected.id}</p>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => onSetScene('start', selected.id)}
          >
            Use on start
          </button>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => onSetScene('stop', selected.id)}
          >
            Use on stop
          </button>
        </div>
      )}
    </div>
  );
}

Resources.propTypes = {
  showAll: PropTypes.bool.isRequired,
  slots: PropTypes.shape({ start: slotShape.isRequired, stop: slotShape.isRequired }).isRequired,
  onSetScene: PropTypes.func.isRequired,
};

/** Lighting: the Hue and DIRIGERA status, the current start and stop scenes, and the bridge's resources. */
export default function Lighting({
  hueStatus,
  dirigeraStatus,
  config,
  slots,
  showAll,
  onSetScene,
}) {
  return (
    <ScreenFrame
      title="Smart lighting"
      lede="What CineLight is talking to, and the scenes it uses."
    >
      <BridgeCards
        hueStatus={hueStatus}
        dirigeraStatus={dirigeraStatus}
        hueEnabled={config?.hueIntegrationEnabled}
      />
      <div className="card-pair">
        <SceneCard heading="On start" slot={slots.start} name={slots.start.label} />
        <SceneCard heading="On stop" slot={slots.stop} name={slots.stop.label} />
      </div>
      <h3 className="section-title">Hue resources</h3>
      <Resources showAll={showAll} slots={slots} onSetScene={onSetScene} />
    </ScreenFrame>
  );
}

Lighting.propTypes = {
  hueStatus: PropTypes.object,
  dirigeraStatus: PropTypes.object,
  config: PropTypes.object,
  slots: PropTypes.shape({ start: slotShape.isRequired, stop: slotShape.isRequired }).isRequired,
  showAll: PropTypes.bool.isRequired,
  onSetScene: PropTypes.func.isRequired,
};
