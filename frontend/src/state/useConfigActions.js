import { useCallback, useState } from 'react';
import { apiWrite } from '../api/http';
import { SCENE_KEYS, sceneSlot } from '../lib/scenes';
import { toggleEvent, toggleGroup } from '../lib/rules';

/**
 * The admin writes of the dashboard, all optimistic: the config is updated at once, and put back if the
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

  /** Makes `sceneId` the start or stop scene. */
  const setScene = useCallback(
    (slot, sceneId) => {
      const current = {
        start: sceneSlot(scenes, config, 'start').id,
        stop: sceneSlot(scenes, config, 'stop').id,
      };
      return commit(
        { ...config, scenes: { ...current, [slot]: sceneId } },
        '/api/config/hue-scene',
        {
          key: SCENE_KEYS[slot],
          sceneId,
        }
      );
    },
    [commit, config, scenes]
  );

  /** Moves the start or stop scene to the next one in the cycle. */
  const cycleScene = useCallback(
    (slot) => {
      const next = sceneSlot(scenes, config, slot).next;
      return next === null ? Promise.resolve() : setScene(slot, next);
    },
    [config, scenes, setScene]
  );

  /** Writes a media type's full map of switches, optimistically. */
  const writeMap = useCallback(
    (type, map) =>
      commit(
        { ...config, plexCommands: { ...config.plexCommands, [type]: map } },
        `/api/config/plex-commands/${type}`,
        map
      ),
    [commit, config]
  );

  /** Flips one event of a media type (the Config matrix). */
  const toggleCell = useCallback(
    (type, event) => writeMap(type, toggleEvent(config.plexCommands?.[type], type, event)),
    [config, writeMap]
  );

  /** Turns the Hue integration on or off. */
  const setHueEnabled = useCallback(
    (enabled) =>
      config
        ? commit({ ...config, hueIntegrationEnabled: enabled }, '/api/config/hue-enabled', {
            enabled,
          })
        : Promise.resolve(),
    [commit, config]
  );

  /** Flips a phrase of a media type's rule; the write carries the type's full map. */
  const toggleRule = useCallback(
    (type, group) => {
      if (!config) {
        return Promise.resolve();
      }
      return writeMap(type, toggleGroup(config.plexCommands?.[type], type, group));
    },
    [config, writeMap]
  );

  return { cycleScene, setScene, toggleRule, toggleCell, setHueEnabled, failed };
}
