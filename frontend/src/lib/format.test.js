import { describe, expect, it } from 'vitest';
import { displayTitle, formatClock, formatClockSeconds, metaLine } from './format';

const at = (h, m, s) => new Date(2024, 0, 15, h, m, s).toISOString();

describe('time formatting', () => {
  it('formats local HH:MM and HH:MM:SS with zero padding', () => {
    expect(formatClock(at(9, 5, 7))).toBe('09:05');
    expect(formatClockSeconds(at(21, 43, 31))).toBe('21:43:31');
    expect(formatClockSeconds(at(0, 0, 0))).toBe('00:00:00');
  });

  it('gives an empty string for a missing or unparseable timestamp', () => {
    expect(formatClock(null)).toBe('');
    expect(formatClock(undefined)).toBe('');
    expect(formatClock('not a date')).toBe('');
    expect(formatClockSeconds('not a date')).toBe('');
  });
});

describe('displayTitle', () => {
  it('uses a movie title as is', () => {
    expect(displayTitle({ mediaType: 'movie', title: 'Heat' })).toBe('Heat');
  });

  it('writes an episode as show, season and episode', () => {
    const episode = {
      mediaType: 'episode',
      title: 'Strange Games',
      grandparentTitle: 'Slow Horses',
    };
    expect(displayTitle({ ...episode, parentIndex: 4, index: 2 })).toBe('Slow Horses · S4 E2');
  });

  it('falls back for an episode without numbers or without a show', () => {
    const base = { mediaType: 'episode', grandparentTitle: 'Slow Horses' };
    expect(displayTitle({ ...base, title: 'Strange Games' })).toBe('Slow Horses · Strange Games');
    expect(displayTitle(base)).toBe('Slow Horses');
    expect(
      displayTitle({ mediaType: 'episode', title: 'Strange Games', parentIndex: 1, index: 1 })
    ).toBe('Strange Games');
  });

  it('writes a track as artist and title, and tolerates a missing part', () => {
    const track = { mediaType: 'track', title: 'Tears in Rain', grandparentTitle: 'Vangelis' };
    expect(displayTitle(track)).toBe('Vangelis · Tears in Rain');
    expect(displayTitle({ mediaType: 'track', grandparentTitle: 'Vangelis' })).toBe('Vangelis');
    expect(displayTitle({ mediaType: 'track', title: 'Tears in Rain' })).toBe('Tears in Rain');
  });

  it('never invents a title', () => {
    expect(displayTitle({ mediaType: 'movie' })).toBe('Unknown title');
    expect(displayTitle(null)).toBe('Unknown title');
  });

  it('shows the item title for a clip even when a show is set', () => {
    expect(displayTitle({ mediaType: 'clip', title: 'Trailer', grandparentTitle: 'X' })).toBe(
      'Trailer'
    );
  });
});

describe('metaLine', () => {
  it('joins year, type and player', () => {
    expect(metaLine({ year: 2017, mediaType: 'movie', player: 'Living Room Apple TV' })).toBe(
      '2017 · Movie · Living Room Apple TV'
    );
  });

  it('omits whatever is missing', () => {
    expect(metaLine({ mediaType: 'episode', player: 'TV' })).toBe('Episode · TV');
    expect(metaLine({ year: 2017 })).toBe('2017');
    expect(metaLine({ year: null, mediaType: null, player: '' })).toBe('');
    expect(metaLine(undefined)).toBe('');
  });

  it('passes an unknown media type through', () => {
    expect(metaLine({ mediaType: 'podcast' })).toBe('podcast');
  });
});
