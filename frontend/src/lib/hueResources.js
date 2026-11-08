// The Hue bridge's CLIP v2 resource list (GET /api/hue/resources): `{data: [{id, type, metadata: {name}}]}`.

/** The types the browser offers by default; every type present is offered when "show all types" is on. */
export const BASIC_TYPES = ['scene', 'room', 'light'];

export function parseResources(body) {
  if (!Array.isArray(body?.data)) {
    return [];
  }
  return body.data
    .filter((item) => item && typeof item.id === 'string' && typeof item.type === 'string')
    .map((item) => ({ id: item.id, type: item.type, name: item.metadata?.name ?? '' }));
}

/** `bridge_home` -> `Bridge home`. */
export function typeLabel(type) {
  const text = type.replaceAll('_', ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** The type filter's options: All, then the basic types, or every type present when `showAll`. */
export function typeOptions(resources, showAll) {
  const types = showAll ? [...new Set(resources.map((r) => r.type))].sort() : BASIC_TYPES;
  return ['all', ...types];
}

function typeMatches(resource, type, showAll) {
  if (type !== 'all') {
    return resource.type === type;
  }
  return showAll || BASIC_TYPES.includes(resource.type);
}

/** Resources matching a search text (name or id, ignoring case) and a type filter. */
export function filterResources(resources, { query, type, showAll }) {
  const needle = query.trim().toLowerCase();
  return resources.filter(
    (resource) =>
      typeMatches(resource, type, showAll) &&
      (needle === '' ||
        resource.name.toLowerCase().includes(needle) ||
        resource.id.toLowerCase().includes(needle))
  );
}

/** A long bridge id as `001788…a1b2`; short ids are left alone. */
export function shortId(id) {
  if (!id) {
    return '';
  }
  return id.length > 12 ? `${id.slice(0, 6)}…${id.slice(-4)}` : id;
}
