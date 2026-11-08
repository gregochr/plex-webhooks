import { describe, expect, it } from 'vitest';
import { filterResources, parseResources, shortId, typeLabel, typeOptions } from './hueResources';

const resources = [
  { id: 's1', type: 'scene', name: 'Movie Night' },
  { id: 's2', type: 'scene', name: 'Relax' },
  { id: 'r1', type: 'room', name: 'Lounge' },
  { id: 'l1', type: 'light', name: 'Floor lamp' },
  { id: 'b1', type: 'bridge_home', name: '' },
];

describe('parseResources', () => {
  it('reads id, type and metadata name, skipping malformed items', () => {
    const body = {
      data: [
        { id: 'a', type: 'scene', metadata: { name: 'Relax' } },
        { id: 'b', type: 'light' },
        null,
        { type: 'x' },
      ],
    };
    expect(parseResources(body)).toEqual([
      { id: 'a', type: 'scene', name: 'Relax' },
      { id: 'b', type: 'light', name: '' },
    ]);
  });

  it.each([null, {}, { data: 'x' }])('gives an empty list for %j', (body) => {
    expect(parseResources(body)).toEqual([]);
  });
});

describe('typeOptions and typeLabel', () => {
  it('offers All, Scene, Room and Light by default', () => {
    expect(typeOptions(resources, false)).toEqual(['all', 'scene', 'room', 'light']);
  });

  it('offers every type present, sorted, when showing all', () => {
    expect(typeOptions(resources, true)).toEqual(['all', 'bridge_home', 'light', 'room', 'scene']);
  });

  it('labels types for people', () => {
    expect(typeLabel('bridge_home')).toBe('Bridge home');
    expect(typeLabel('scene')).toBe('Scene');
  });
});

describe('filterResources', () => {
  const all = { query: '', type: 'all', showAll: false };
  const ids = (list) => list.map((r) => r.id);

  it('All hides the non-basic types unless showing all', () => {
    expect(ids(filterResources(resources, all))).toEqual(['s1', 's2', 'r1', 'l1']);
    expect(filterResources(resources, { ...all, showAll: true })).toHaveLength(5);
  });

  it('filters by type', () => {
    expect(ids(filterResources(resources, { ...all, type: 'scene' }))).toEqual(['s1', 's2']);
    expect(ids(filterResources(resources, { ...all, type: 'bridge_home' }))).toEqual(['b1']);
  });

  it('searches names and ids, ignoring case and surrounding spaces', () => {
    expect(ids(filterResources(resources, { ...all, query: ' MOVIE ' }))).toEqual(['s1']);
    expect(ids(filterResources(resources, { ...all, query: 'R1' }))).toEqual(['r1']);
    expect(filterResources(resources, { ...all, query: 'zzz' })).toEqual([]);
  });
});

describe('shortId', () => {
  it('shortens long ids and keeps short or empty ones', () => {
    expect(shortId('001788FFFE123456')).toBe('001788…3456');
    expect(shortId('abcdefghijkl')).toBe('abcdefghijkl');
    expect(shortId('')).toBe('');
    expect(shortId(null)).toBe('');
  });
});
