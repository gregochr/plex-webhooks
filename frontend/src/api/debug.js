import { apiWrite } from './http';
import { outcomeLabel } from '../lib/outcomes';

/**
 * Sends a test event down the real pipeline (POST /api/debug/{event}). Resolves to `{ok, text}` for display:
 * the outcome in the history's own words, or why nothing was sent.
 */
export async function sendTestEvent(event, type, title) {
  const query = new URLSearchParams({ type });
  if (title && title.trim() !== '') {
    query.set('title', title.trim());
  }
  try {
    const result = await apiWrite(`/api/debug/${event}?${query}`, { method: 'POST' });
    const label = outcomeLabel(result?.outcome, null);
    return { ok: label.tone === 'applied', text: `Sent ${event}: ${label.text}` };
  } catch {
    return { ok: false, text: `The ${event} event was not sent.` };
  }
}
