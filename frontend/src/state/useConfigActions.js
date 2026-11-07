import { useCallback, useState } from 'react';
import { apiWrite } from '../api/http';
import { SCENE_KEYS, sceneSlot } from '../lib/scenes';
import { toggleGroup } from '../lib/rules';

/**
 * The two admin writes of the dashboard, both optimistic: the config is updated at once, and put back if the
 * server refuses or the sign-in dialog is cancelled. `failed` is true after such a rollback until the next write.
 */
export function useConfigActions({ config, scenes, dispatch }) {
  const [failed, setFailed] = useState(false);

  const commit = useCallback(
    async (optimistic, path, body) => {
      const previous = config;
      setFailed(false);
      dispatch({ type: 'config-local', config: optimistic });
      try {
        await apiWrite(path, { body });
      } catch {
        dispatch({ type: 'config-rollback', optimistic, previous });
        setFailed(true);
      }
    },
    [config, dispatch]
  );

  /** Moves the start or stop scene to the next one in the cycle. */
  const cycleScene = useCallback(
    (slot) => {
      const next = sceneSlot(scenes, config, slot).next;
      if (next === null) {
        return Promise.resolve();
      }
      const current = {
        start: sceneSlot(scenes, config, 'start').id,
        stop: sceneSlot(scenes, config, 'stop').id,
      };
      return commit({ ...config, scenes: { ...current, [slot]: next } }, '/api/config/hue-scene', {
        key: SCENE_KEYS[slot],
        sceneId: next,
      });
    },
    [commit, config, scenes]
  );

  /** Flips a phrase of a media type's rule; the write carries the type's full map. */
  const toggleRule = useCallback(
    (type, group) => {
      if (!config) {
        return Promise.resolve();
      }
      const map = toggleGroup(config.plexCommands?.[type], type, group);
      return commit(
        { ...config, plexCommands: { ...config.plexCommands, [type]: map } },
        `/api/config/plex-commands/${type}`,
        map
      );
    },
    [commit, config]
  );

  return { cycleScene, toggleRule, failed };
}
