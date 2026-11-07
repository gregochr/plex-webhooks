import { displayTitle, formatClockSeconds } from './format';
import { outcomeLabel } from './outcomes';
import { sceneName } from './scenes';

/** One history item as the views show it. `catalog` resolves the activated scene's name. */
export function describeEvent(event, catalog) {
  return {
    id: event.id,
    time: formatClockSeconds(event.timestamp),
    name: event.event ?? '',
    title: displayTitle(event),
    outcome: outcomeLabel(event.outcome, sceneName(catalog, event.sceneId)),
  };
}
