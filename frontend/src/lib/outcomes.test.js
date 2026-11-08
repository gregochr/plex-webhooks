import { describe, expect, it } from 'vitest';
import { outcomeLabel } from './outcomes';

describe('outcomeLabel', () => {
  it.each([
    ['IGNORED_RULE_OFF', 'Ignored · rule off', 'ignored'],
    ['IGNORED_HUE_DISABLED', 'Ignored · Hue off', 'ignored'],
    ['IGNORED_UNSUPPORTED', 'Ignored · not supported', 'ignored'],
    ['IGNORED_UNKNOWN_TYPE', 'Ignored · unknown media type', 'ignored'],
    ['FAILED', 'Failed', 'failed'],
  ])('%s reads "%s"', (kind, text, tone) => {
    expect(outcomeLabel(kind, null)).toEqual({ text, tone });
  });

  it('names the scene that was applied', () => {
    expect(outcomeLabel('SCENE_APPLIED', 'Movie Night')).toEqual({
      text: '→ Movie Night',
      tone: 'applied',
    });
  });

  it('says so when an applied scene is not in the catalog', () => {
    expect(outcomeLabel('SCENE_APPLIED', null)).toEqual({ text: 'Scene applied', tone: 'applied' });
  });

  it('copes with a missing or future outcome', () => {
    expect(outcomeLabel(undefined, null).tone).toBe('unknown');
    expect(outcomeLabel('SOMETHING_NEW', null).text).toBe('No result recorded');
  });
});
