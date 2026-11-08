import { describe, expect, it } from 'vitest';
import { routeFromHash } from './route';

describe('routeFromHash', () => {
  it.each([
    ['#/events', 'events'],
    ['#/lighting', 'lighting'],
    ['#/config', 'config'],
    ['#/debug', 'debug'],
  ])('%s opens %s', (hash, route) => {
    expect(routeFromHash(hash)).toBe(route);
  });

  it.each(['', '#', '#/', '#/nowhere', '#/events/extra'])('%j is the mode view', (hash) => {
    expect(routeFromHash(hash)).toBe('mode');
  });
});
