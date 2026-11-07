export const RING_CIRCUMFERENCE = 754;

/** The stroke-dasharray of the level ring: the filled arc, then the rest. Null or out-of-range levels clamp. */
export function ringDash(level) {
  const clamped = Math.min(100, Math.max(0, level ?? 0));
  return `${((RING_CIRCUMFERENCE * clamped) / 100).toFixed(1)} ${RING_CIRCUMFERENCE}`;
}
