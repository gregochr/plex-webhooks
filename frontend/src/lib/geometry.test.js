import { describe, expect, it } from 'vitest';
import { RING_CIRCUMFERENCE, ringDash } from './geometry';

describe('ringDash', () => {
  it('uses the 754 circumference', () => {
    expect(RING_CIRCUMFERENCE).toBe(754);
    expect(ringDash(100)).toBe('754.0 754');
    expect(ringDash(50)).toBe('377.0 754');
    expect(ringDash(5)).toBe('37.7 754');
  });

  it('draws nothing for a null level and clamps out-of-range ones', () => {
    expect(ringDash(null)).toBe('0.0 754');
    expect(ringDash(-5)).toBe('0.0 754');
    expect(ringDash(140)).toBe('754.0 754');
  });
});
