import { describe, expect, it } from 'vitest';
import { cycleOrder, findScene, nextSceneId, sceneList, sceneName, sceneSlot } from './scenes';

const scene = (id, name, groupId, level = 20) => ({
  id,
  name,
  groupId,
  groupName: groupId,
  groupType: 'room',
  level,
});
const A1 = scene('a1', 'Movie Night', 'lounge', 5);
const B1 = scene('b1', 'Bright', 'kitchen', 100);
const A2 = scene('a2', 'Relax', 'lounge', 30);
const B2 = scene('b2', 'Dim', 'kitchen', null);
const SCENES = [A1, B1, A2, B2];

describe('cycleOrder and nextSceneId', () => {
  it("puts the current scene's room first, then the rest, in catalog order", () => {
    expect(cycleOrder(SCENES, 'a1').map((s) => s.id)).toEqual(['a1', 'a2', 'b1', 'b2']);
    expect(cycleOrder(SCENES, 'b2').map((s) => s.id)).toEqual(['b1', 'b2', 'a1', 'a2']);
  });

  it('steps through the room, then the rest, skipping nothing, and wraps', () => {
    const seen = [];
    let id = 'a1';
    for (let i = 0; i < 5; i += 1) {
      id = nextSceneId(SCENES, id);
      seen.push(id);
    }
    expect(seen).toEqual(['a2', 'b1', 'b2', 'a1', 'a2']);
  });

  it('starts from the first scene when the current one is not in the catalog', () => {
    expect(nextSceneId(SCENES, 'gone')).toBe('a1');
    expect(nextSceneId(SCENES, null)).toBe('a1');
  });

  it('treats scenes without a group as having no room', () => {
    const loose = [scene('x', 'X', null), scene('y', 'Y', null)];
    expect(cycleOrder(loose, 'x').map((s) => s.id)).toEqual(['x', 'y']);
    expect(nextSceneId(loose, 'x')).toBe('y');
  });

  it('has nowhere to go with no scenes, or when the only scene is current', () => {
    expect(nextSceneId([], 'a1')).toBeNull();
    expect(nextSceneId([A1], 'a1')).toBeNull();
    expect(nextSceneId([A1], 'other')).toBe('a1');
  });
});

describe('lookups', () => {
  const catalog = {
    scenes: SCENES,
    start: { id: 'a1', summary: A1 },
    stop: { id: 'zz', summary: scene('zz', 'Hidden', null, 1) },
  };

  it('finds a scene in the list, or in the start/stop selection', () => {
    expect(findScene(catalog, 'b1')).toBe(B1);
    expect(findScene(catalog, 'zz').name).toBe('Hidden');
    expect(findScene(catalog, 'nope')).toBeNull();
    expect(findScene(catalog, null)).toBeNull();
    expect(findScene(null, 'a1')).toBeNull();
  });

  it('gives a name or null', () => {
    expect(sceneName(catalog, 'a2')).toBe('Relax');
    expect(sceneName(catalog, 'nope')).toBeNull();
  });

  it('tolerates a missing catalog', () => {
    expect(sceneList(null)).toEqual([]);
    expect(sceneList({ scenes: null })).toEqual([]);
  });
});

describe('sceneSlot', () => {
  const catalog = {
    scenes: SCENES,
    start: { id: 'a1', summary: A1 },
    stop: { id: 'a2', summary: A2 },
  };

  it('prefers the live config id and reads name, level and next', () => {
    const slot = sceneSlot(catalog, { scenes: { start: 'b1', stop: 'a2' } }, 'start');
    expect(slot).toMatchObject({
      id: 'b1',
      label: 'Bright',
      level: 100,
      next: 'b2',
      disabledReason: null,
    });
  });

  it("falls back to the catalog's selection when there is no config", () => {
    expect(sceneSlot(catalog, null, 'stop').id).toBe('a2');
  });

  it('a scene with no level has a null level', () => {
    expect(sceneSlot(catalog, { scenes: { start: 'b2' } }, 'start').level).toBeNull();
  });

  it('with no catalog it shows the configured id, or "Not set", and explains why it cannot cycle', () => {
    const withId = sceneSlot(null, { scenes: { start: 'abc-123' } }, 'start');
    expect(withId).toMatchObject({ label: 'abc-123', level: null, next: null });
    expect(withId.disabledReason).toMatch(/not available/);
    const none = sceneSlot({ scenes: [], start: { id: null, summary: null } }, null, 'start');
    expect(none.label).toBe('Not set');
  });

  it('with a single scene it cannot cycle and says so', () => {
    const slot = sceneSlot({ scenes: [A1] }, { scenes: { start: 'a1' } }, 'start');
    expect(slot.disabledReason).toMatch(/no other scene/);
  });
});
