import { describe, expect, it } from 'vitest';
import { eventKey, groupState, offEvents, rowTag, toggleEvent, toggleGroup } from './rules';

const all = (type, value) =>
  Object.fromEntries(
    ['play', 'resume', 'stop', 'pause', 'scrobble'].map((event) => [eventKey(type, event), value])
  );

describe('groupState', () => {
  it('is on when every event in the group is on', () => {
    expect(groupState(all('movie', true), 'movie', 'start')).toBe('on');
    expect(groupState(all('movie', true), 'movie', 'stop')).toBe('on');
  });

  it('is off when none is', () => {
    expect(groupState(all('movie', false), 'movie', 'start')).toBe('off');
    expect(groupState({}, 'movie', 'stop')).toBe('off');
    expect(groupState(undefined, 'movie', 'stop')).toBe('off');
  });

  it('is mixed otherwise', () => {
    const map = { ...all('movie', true), [eventKey('movie', 'resume')]: false };
    expect(groupState(map, 'movie', 'start')).toBe('mixed');
    expect(groupState(map, 'movie', 'stop')).toBe('on');
  });

  it('counts scrobble: the default episode config (scrobble off) is mixed, not on', () => {
    const map = { ...all('episode', true), [eventKey('episode', 'scrobble')]: false };
    expect(groupState(map, 'episode', 'stop')).toBe('mixed');
    expect(offEvents(map, 'episode', 'stop')).toEqual(['scrobble']);
  });

  it('does not read another media type', () => {
    expect(groupState(all('track', true), 'movie', 'start')).toBe('off');
  });
});

describe('toggleGroup', () => {
  it('turns an off group all on and leaves the other group alone', () => {
    const next = toggleGroup(all('clip', false), 'clip', 'start');
    expect(next[eventKey('clip', 'play')]).toBe(true);
    expect(next[eventKey('clip', 'resume')]).toBe(true);
    expect(next[eventKey('clip', 'pause')]).toBe(false);
  });

  it('turns a mixed group all on', () => {
    const map = { ...all('movie', true), [eventKey('movie', 'scrobble')]: false };
    const next = toggleGroup(map, 'movie', 'stop');
    expect(groupState(next, 'movie', 'stop')).toBe('on');
  });

  it('turns an on group all off, scrobble included', () => {
    const next = toggleGroup(all('movie', true), 'movie', 'stop');
    expect(next[eventKey('movie', 'pause')]).toBe(false);
    expect(next[eventKey('movie', 'stop')]).toBe(false);
    expect(next[eventKey('movie', 'scrobble')]).toBe(false);
    expect(next[eventKey('movie', 'play')]).toBe(true);
  });

  it('works from a missing map and does not mutate its input', () => {
    expect(groupState(toggleGroup(undefined, 'photo', 'start'), 'photo', 'start')).toBe('on');
    const map = all('movie', true);
    toggleGroup(map, 'movie', 'start');
    expect(map).toEqual(all('movie', true));
  });
});

describe('rowTag', () => {
  it('is On when either group is on or mixed', () => {
    expect(rowTag(all('movie', true), 'movie')).toBe('On');
    const onlyScrobble = { ...all('movie', false), [eventKey('movie', 'scrobble')]: true };
    expect(rowTag(onlyScrobble, 'movie')).toBe('On');
    const startOn = { ...all('movie', false), [eventKey('movie', 'play')]: true };
    expect(rowTag(startOn, 'movie')).toBe('On');
  });

  it('is Off when both are off', () => {
    expect(rowTag(all('movie', false), 'movie')).toBe('Off');
  });
});

describe('toggleEvent', () => {
  it('flips one switch and leaves the rest, from on, off or missing', () => {
    const map = all('movie', true);
    const off = toggleEvent(map, 'movie', 'pause');
    expect(off[eventKey('movie', 'pause')]).toBe(false);
    expect(off[eventKey('movie', 'play')]).toBe(true);
    expect(map[eventKey('movie', 'pause')]).toBe(true);
    expect(toggleEvent(off, 'movie', 'pause')[eventKey('movie', 'pause')]).toBe(true);
    expect(toggleEvent(undefined, 'clip', 'play')).toEqual({ [eventKey('clip', 'play')]: true });
  });
});
