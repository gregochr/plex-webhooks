import PropTypes from 'prop-types';
import { describeEvent } from '../lib/events';
import { displayTitle, formatClock, metaLine } from '../lib/format';
import { ringDash } from '../lib/geometry';
import { findScene } from '../lib/scenes';
import { slotShape } from './propShapes';

const EMPTY = 'Nothing has played yet.';

function SceneRow({ slot, eyebrow, onCycle }) {
  const disabled = slot.disabledReason !== null;
  return (
    <button
      type="button"
      className="scene-row"
      aria-disabled={disabled ? 'true' : undefined}
      title={disabled ? slot.disabledReason : undefined}
      onClick={() => {
        if (!disabled) {
          onCycle(slot.slot);
        }
      }}
    >
      <span className="scene-row-eyebrow">{eyebrow}</span>{' '}
      <span className="scene-row-name">{slot.label}</span>
    </button>
  );
}

SceneRow.propTypes = {
  slot: slotShape.isRequired,
  eyebrow: PropTypes.string.isRequired,
  onCycle: PropTypes.func.isRequired,
};

function Ring({ level, label }) {
  return (
    <svg className="ring" viewBox="0 0 300 300" role="img" aria-label={label}>
      <circle className="ring-track" cx="150" cy="150" r="120" />
      <circle className="ring-level" cx="150" cy="150" r="120" strokeDasharray={ringDash(level)} />
    </svg>
  );
}

Ring.propTypes = { level: PropTypes.number, label: PropTypes.string.isRequired };

/**
 * Theater mode: what is playing, the room level the current scene sets, the two scenes, and the last four events.
 * The dark ground is the point: it is glanceable from the sofa.
 */
export default function Theater({
  nowPlaying,
  history,
  catalog,
  slots,
  onCycle,
  narrow,
  statusNote,
}) {
  const np = nowPlaying ?? { state: 'Idle' };
  const hasItem = Boolean(np.title || np.grandparentTitle);
  const scene = findScene(catalog, np.sceneId);
  const level = scene?.level ?? null;
  const sceneLabel = np.sceneId ? (scene?.name ?? 'Scene unknown') : null;
  const since = formatClock(np.since);
  const meta = hasItem ? metaLine(np) : '';
  const ringLabel = describeRing(level, sceneLabel);
  const events = history.slice(0, 4).map((event) => describeEvent(event, catalog));

  return (
    <section className="theater" aria-label="Theater mode">
      <div className="theater-stage">
        <div className="theater-now">
          <p className="kicker">
            {np.state}
            {since && (
              <>
                {' · since '}
                <span className="tnum">{since}</span>
              </>
            )}
          </p>
          <h2 className="theater-title">{hasItem ? displayTitle(np) : 'Nothing playing'}</h2>
          {meta && <p className="theater-meta">{meta}</p>}
        </div>
        <div className="ring-wrap">
          <Ring level={level} label={ringLabel} />
          <div className="ring-centre">
            {level !== null && <span className="ring-level-text tnum">{level}%</span>}
            {level !== null && !narrow && <span className="ring-caption">Room level</span>}
            {sceneLabel ? (
              <span className="ring-scene">{sceneLabel}</span>
            ) : (
              <span className="ring-scene ring-scene-muted">No scene applied yet</span>
            )}
          </div>
        </div>
        <div className="scene-rows">
          <SceneRow
            slot={slots.start}
            eyebrow={narrow ? 'On play' : 'On play · tap to change'}
            onCycle={onCycle}
          />
          <SceneRow
            slot={slots.stop}
            eyebrow={narrow ? 'On stop' : 'On pause or stop · tap to change'}
            onCycle={onCycle}
          />
        </div>
      </div>
      <div className="strip">
        {events.length === 0 ? (
          <p className="strip-empty">{EMPTY}</p>
        ) : (
          <ol className="strip-list">
            {events.map((event) => (
              <li key={event.id} className="strip-item">
                <span className="strip-when tnum">
                  {event.time} · {event.name}
                </span>
                <span className="strip-title">{event.title}</span>
                <span className={`strip-outcome tone-${event.outcome.tone}`}>
                  {event.outcome.text}
                </span>
              </li>
            ))}
          </ol>
        )}
      </div>
      {narrow && <p className="theater-note">{statusNote}</p>}
    </section>
  );
}

function describeRing(level, sceneLabel) {
  if (level !== null) {
    return `Room level ${level}%, scene ${sceneLabel}`;
  }
  return sceneLabel ? `Scene ${sceneLabel}` : 'No scene applied yet';
}

Theater.propTypes = {
  nowPlaying: PropTypes.object,
  history: PropTypes.array.isRequired,
  catalog: PropTypes.object,
  slots: PropTypes.shape({ start: slotShape.isRequired, stop: slotShape.isRequired }).isRequired,
  onCycle: PropTypes.func.isRequired,
  narrow: PropTypes.bool.isRequired,
  statusNote: PropTypes.string.isRequired,
};
