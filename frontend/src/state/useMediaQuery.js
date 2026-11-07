import { useSyncExternalStore } from 'react';

/** The one breakpoint (see styles/app.css): at or under this width the phone layout applies. */
export const NARROW_QUERY = '(max-width: 768px)';

function matcher(query) {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia(query)
    : null;
}

/** Whether a CSS media query currently matches; false where matchMedia does not exist. */
export function useMediaQuery(query) {
  return useSyncExternalStore(
    (notify) => {
      const list = matcher(query);
      list?.addEventListener('change', notify);
      return () => list?.removeEventListener('change', notify);
    },
    () => matcher(query)?.matches ?? false
  );
}

export const useNarrow = () => useMediaQuery(NARROW_QUERY);
