import PropTypes from 'prop-types';
import { describeEvent } from '../lib/events';
import { displayTitle } from '../lib/format';
import { findScene } from '../lib/scenes';
import { GROUPS, MEDIA_TYPES, groupState, offEvents, rowTag } from '../lib/rules';
import { slotShape } from './propShapes';

const EMPTY = 'Nothing has played yet.';

function SceneWord({ slot, onCycle }) {
  const disabled = slot.disabledReason !== null;
  return (
    <button
      type="button"
      className="scene-word"
      aria-disabled={disabled ? 'true' : undefined}
      title={disabled ? slot.disabledReason : undefined}
      onClick={() => {
        if (!disabled) {
          onCycle(slot.slot);
        }
      }}
    >
      {slot.label}
    </button>
  );
}

SceneWord.propTypes = { slot: slotShape.isRequired, onCycle: PropTypes.func.isRequired };

function Phrase({ map, type, noun, group, narrow, onToggle }) {
  const state = groupState(map, type, group);
  const text = narrow ? GROUPS[group].short : GROUPS[group].long;
  const mixed = state === 'mixed';
  return (
    <button
      type="button"
      className={`phrase phrase-${state}`}
      aria-pressed={mixed ? 'mixed' : state === 'on'}
      aria-label={`${noun} ${text}${mixed ? ', partly' : ''}`}
      title={mixed ? `Switched off for: ${offEvents(map, type, group).join(', ')}` : undefined}
      onClick={() => onToggle(type, group)}
    >
      {text}
      {mixed && <span className="partly"> partly</span>}
    </button>
  );
}

Phrase.propTypes = {
  map: PropTypes.object,
  type: PropTypes.string.isRequired,
  noun: PropTypes.string.isRequired,
  group: PropTypes.oneOf(['start', 'stop']).isRequired,
  narrow: PropTypes.bool.isRequired,
  onToggle: PropTypes.func.isRequired,
};

function Rule({ map, type, noun, narrow, onToggle }) {
  const tag = rowTag(map, type);
  const phrase = (group) => (
    <Phrase map={map} type={type} noun={noun} group={group} narrow={narrow} onToggle={onToggle} />
  );
  return (
    <li className="rule">
      <p className="rule-sentence">
        When {noun} {phrase('start')}, {narrow ? 'dim' : 'dim the room'}. When it {phrase('stop')},{' '}
        {narrow ? 'restore' : 'bring them back'}.
      </p>
      <span className={`tag ${tag === 'On' ? 'tag-accent' : 'tag-neutral'}`}>{tag}</span>
    </li>
  );
}

Rule.propTypes = {
  map: PropTypes.object,
  type: PropTypes.string.isRequired,
  noun: PropTypes.string.isRequired,
  narrow: PropTypes.bool.isRequired,
  onToggle: PropTypes.func.isRequired,
};

function nowDetail(np, scene) {
  const place = [];
  if (scene?.level != null) {
    place.push(`room at ${scene.level}%`);
  }
  if (scene?.name) {
    place.push(scene.name);
  }
  return [np.state, place.join(', ')].filter(Boolean).join(' · ');
}

function Now({ nowPlaying, catalog }) {
  const np = nowPlaying ?? { state: 'Idle' };
  const hasItem = Boolean(np.title || np.grandparentTitle);
  return (
    <div className="rail-now">
      <h3 className="rail-heading">Now</h3>
      <p className="rail-now-title">{hasItem ? displayTitle(np) : 'Nothing playing'}</p>
      <p className="rail-now-detail tnum">{nowDetail(np, findScene(catalog, np.sceneId))}</p>
    </div>
  );
}

Now.propTypes = { nowPlaying: PropTypes.object, catalog: PropTypes.object };

function Programme({ history, catalog }) {
  const events = history.map((event) => describeEvent(event, catalog));
  return (
    <div>
      <h3 className="rail-heading">Tonight&apos;s programme</h3>
      <p className="rail-sub">since CineLight started</p>
      {events.length === 0 ? (
        <p className="rail-empty">{EMPTY}</p>
      ) : (
        <ol className="programme">
          {events.map((event) => (
            <li key={event.id} className="programme-item">
              <span className="programme-time tnum">{event.time}</span>
              <span>
                <span className="programme-title">{event.title}</span>
                <span className={`programme-outcome tone-${event.outcome.tone}`}>
                  {event.name} {event.outcome.text}
                </span>
              </span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

Programme.propTypes = { history: PropTypes.array.isRequired, catalog: PropTypes.object };

function RecentEvents({ history, catalog }) {
  const events = history.slice(0, 4).map((event) => describeEvent(event, catalog));
  if (events.length === 0) {
    return <p className="recent-empty">{EMPTY}</p>;
  }
  return (
    <ol className="recent">
      {events.map((event) => (
        <li key={event.id} className="recent-item">
          <span>
            <span className="recent-time tnum">{event.time}</span> {event.name}
          </span>
          <span className={`tone-${event.outcome.tone}`}>{event.outcome.text}</span>
        </li>
      ))}
    </ol>
  );
}

RecentEvents.propTypes = { history: PropTypes.array.isRequired, catalog: PropTypes.object };

/** House rules: which events change the lights, written as sentences you can edit in place. */
export default function Rules({
  config,
  nowPlaying,
  history,
  catalog,
  slots,
  onCycle,
  onToggle,
  narrow,
}) {
  return (
    <section className="rules" aria-label="House rules">
      <div className="rules-main">
        <div className="rules-head">
          <p className="rules-kicker">The house rules</p>
          {!narrow && (
            <>
              <hr className="rules-rule" />
              <h2 className="rules-title">When the lights go down</h2>
            </>
          )}
        </div>
        <p className="rules-summary">
          Lights go to <SceneWord slot={slots.start} onCycle={onCycle} /> when something starts, and
          back to <SceneWord slot={slots.stop} onCycle={onCycle} /> when it stops.
        </p>
        {config ? (
          <ul className="rule-list">
            {MEDIA_TYPES.map(({ type, noun }) => (
              <Rule
                key={type}
                map={config.plexCommands?.[type]}
                type={type}
                noun={noun}
                narrow={narrow}
                onToggle={onToggle}
              />
            ))}
          </ul>
        ) : (
          <p className="rail-empty">Loading the rules…</p>
        )}
        {narrow && <RecentEvents history={history} catalog={catalog} />}
      </div>
      {!narrow && (
        <aside className="rules-rail" aria-label="Now and recent events">
          <Now nowPlaying={nowPlaying} catalog={catalog} />
          <Programme history={history} catalog={catalog} />
        </aside>
      )}
    </section>
  );
}

Rules.propTypes = {
  config: PropTypes.object,
  nowPlaying: PropTypes.object,
  history: PropTypes.array.isRequired,
  catalog: PropTypes.object,
  slots: PropTypes.shape({ start: slotShape.isRequired, stop: slotShape.isRequired }).isRequired,
  onCycle: PropTypes.func.isRequired,
  onToggle: PropTypes.func.isRequired,
  narrow: PropTypes.bool.isRequired,
};
