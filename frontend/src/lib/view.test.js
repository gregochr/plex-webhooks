import { describe, expect, it } from 'vitest';
import { modeStatusText, resolveView } from './view';

describe('resolveView', () => {
  it('auto shows theater only while playing', () => {
    expect(resolveView('auto', 'Playing')).toBe('theater');
    ['Paused', 'Stopped', 'Idle', undefined].forEach((state) => {
      expect(resolveView('auto', state)).toBe('rules');
    });
  });

  it('a manual choice wins over the playback state', () => {
    expect(resolveView('rules', 'Playing')).toBe('rules');
    expect(resolveView('theater', 'Idle')).toBe('theater');
  });
});

describe('modeStatusText', () => {
  it('words auto and manual', () => {
    expect(modeStatusText('auto')).toBe('Switches automatically with playback');
    expect(modeStatusText('theater')).toBe('Manual · switches back on the next event');
    expect(modeStatusText('rules')).toBe('Manual · switches back on the next event');
  });
});
