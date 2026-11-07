// Scene lookup and cycling. `catalog` is GET /api/hue/scenes ({scenes, start, stop}) and may be null or empty
// (no Hue bridge); `config` is GET /api/config ({scenes: {start, stop}, ...}).

export const SCENE_KEYS = { start: 'hue.start-scene', stop: 'hue.stop-scene' };

const NO_CATALOG = 'The scene list is not available, so scenes cannot be changed here.';
const NO_OTHER = 'There is no other scene to switch to.';

export function sceneList(catalog) {
  return Array.isArray(catalog?.scenes) ? catalog.scenes : [];
}

/** The catalog's summary for a scene id, or null. */
export function findScene(catalog, id) {
  if (!id) {
    return null;
  }
  const found = sceneList(catalog).find((scene) => scene.id === id);
  if (found) {
    return found;
  }
  return (
    [catalog?.start, catalog?.stop].find((sel) => sel?.id === id && sel.summary)?.summary ?? null
  );
}

/** A scene's name, or null when the catalog does not know it. */
export function sceneName(catalog, id) {
  return findScene(catalog, id)?.name ?? null;
}

/**
 * The order scenes cycle in: the current scene's room or zone first, then the rest, each in catalog order.
 * Nothing is skipped; cycling wraps.
 */
export function cycleOrder(scenes, currentId) {
  const current = scenes.find((scene) => scene.id === currentId);
  const groupId = current?.groupId ?? null;
  const inGroup = (scene) => groupId !== null && scene.groupId === groupId;
  return [...scenes.filter(inGroup), ...scenes.filter((scene) => !inGroup(scene))];
}

/** The id after `currentId` in the cycle, or null when there is nothing to cycle to. */
export function nextSceneId(scenes, currentId) {
  const order = cycleOrder(scenes, currentId);
  if (order.length === 0 || (order.length === 1 && order[0].id === currentId)) {
    return null;
  }
  const index = order.findIndex((scene) => scene.id === currentId);
  return order[(index + 1) % order.length].id;
}

/**
 * Everything a view needs about the start or stop scene: its id (the live config first, then the catalog's),
 * a label (name, else the configured id, else "Not set"), its level, the next id, and why cycling is off.
 */
export function sceneSlot(catalog, config, slot) {
  const id = config?.scenes?.[slot] ?? catalog?.[slot]?.id ?? null;
  const summary = findScene(catalog, id);
  const scenes = sceneList(catalog);
  const next = nextSceneId(scenes, id);
  let disabledReason = null;
  if (scenes.length === 0) {
    disabledReason = NO_CATALOG;
  } else if (next === null) {
    disabledReason = NO_OTHER;
  }
  return {
    slot,
    id,
    label: summary?.name ?? id ?? 'Not set',
    level: summary?.level ?? null,
    next,
    disabledReason,
  };
}
