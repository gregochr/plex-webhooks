import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readShowAllTypes, writeShowAllTypes } from './prefs';

/** An in-memory Storage: the test machine's own localStorage may be missing or read-only. */
function memoryStorage() {
  const data = new Map();
  return {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, String(value)),
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('show all Hue types preference', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', memoryStorage());
  });

  it('defaults to off and round-trips', () => {
    expect(readShowAllTypes()).toBe(false);
    writeShowAllTypes(true);
    expect(readShowAllTypes()).toBe(true);
    writeShowAllTypes(false);
    expect(readShowAllTypes()).toBe(false);
  });

  it('survives a localStorage that throws', () => {
    const blocked = () => {
      throw new Error('blocked');
    };
    vi.stubGlobal('localStorage', { getItem: blocked, setItem: blocked });
    expect(readShowAllTypes()).toBe(false);
    expect(() => writeShowAllTypes(true)).not.toThrow();
  });
});
