import { describe, expect, it } from 'vitest';
import { HISTORY_LIMIT, initialState, mergeHistory, reducer } from './reducer';

const media = (id, event = 'media.play') => ({ id, event, title: `T${id}` });

describe('reducer', () => {
  it('seeds only the keys it is given and leaves the rest', () => {
    const state = reducer(initialState, { type: 'seed', data: { config: { a: 1 } } });
    expect(state.config).toEqual({ a: 1 });
    expect(state.history).toEqual([]);
    expect(state.scenes).toBeNull();
  });

  it('seeds the history newest first and merges with events already received', () => {
    const live = reducer(initialState, { type: 'message', event: media(5) });
    const state = reducer(live, {
      type: 'seed',
      data: { history: [media(5), media(4), media(3)] },
    });
    expect(state.history.map((e) => e.id)).toEqual([5, 4, 3]);
  });

  it('an initial seed does not overwrite a slice a live event already set; a forced seed does', () => {
    const live = reducer(initialState, { type: 'now-playing', data: { state: 'Playing' } });
    const initial = reducer(live, {
      type: 'seed',
      data: { nowPlaying: { state: 'Idle' }, config: { a: 1 } },
    });
    expect(initial.nowPlaying).toEqual({ state: 'Playing' });
    expect(initial.config).toEqual({ a: 1 });

    const forced = reducer(live, {
      type: 'seed',
      data: { nowPlaying: { state: 'Paused' } },
      force: true,
    });
    expect(forced.nowPlaying).toEqual({ state: 'Paused' });
  });

  it('prepends a media message and resets a manual view to auto', () => {
    const manual = reducer(initialState, { type: 'set-view', view: 'theater' });
    const state = reducer(manual, { type: 'message', event: media(1) });
    expect(state.history).toHaveLength(1);
    expect(state.view).toBe('auto');
  });

  it('ignores a non-media message: not stored, view untouched', () => {
    const manual = reducer(initialState, { type: 'set-view', view: 'rules' });
    expect(reducer(manual, { type: 'message', event: media(2, 'library.new') })).toBe(manual);
    expect(reducer(manual, { type: 'message', event: { id: 3 } })).toBe(manual);
  });

  it('does not duplicate an event delivered twice', () => {
    const once = reducer(initialState, { type: 'message', event: media(1) });
    const twice = reducer(once, { type: 'message', event: media(1) });
    expect(twice.history).toHaveLength(1);
  });

  it('caps the history', () => {
    const many = Array.from({ length: HISTORY_LIMIT + 10 }, (_, i) => media(i));
    expect(mergeHistory([], many)).toHaveLength(HISTORY_LIMIT);
    expect(mergeHistory([], many)[0].id).toBe(HISTORY_LIMIT + 9);
  });

  it.each([
    ['now-playing', 'nowPlaying'],
    ['config', 'config'],
    ['hue-status', 'hueStatus'],
    ['dirigera-status', 'dirigeraStatus'],
  ])('replaces %s data', (type, key) => {
    const state = reducer(initialState, { type, data: { x: 1 } });
    expect(state[key]).toEqual({ x: 1 });
  });

  it('records heartbeat, connection and view', () => {
    expect(reducer(initialState, { type: 'heartbeat', at: 7 }).lastHeartbeat).toBe(7);
    expect(reducer(initialState, { type: 'connection', status: 'live' }).connection).toBe('live');
    expect(reducer(initialState, { type: 'set-view', view: 'rules' }).view).toBe('rules');
  });

  it('shows an optimistic config and rolls it back when it is still the one on screen', () => {
    const previous = { v: 'server' };
    const optimistic = { v: 'local' };
    const local = reducer(
      { ...initialState, config: previous },
      { type: 'config-local', config: optimistic }
    );
    expect(local.config).toBe(optimistic);
    expect(reducer(local, { type: 'config-rollback', optimistic, previous }).config).toBe(previous);
  });

  it('does not roll back over a newer config that arrived meanwhile', () => {
    const optimistic = { v: 'local' };
    const newer = reducer(
      { ...initialState, config: optimistic },
      { type: 'config', data: { v: 'other dashboard' } }
    );
    const after = reducer(newer, {
      type: 'config-rollback',
      optimistic,
      previous: { v: 'server' },
    });
    expect(after.config).toEqual({ v: 'other dashboard' });
  });

  it('returns the same state for an unknown action', () => {
    expect(reducer(initialState, { type: 'nope' })).toBe(initialState);
  });
});
