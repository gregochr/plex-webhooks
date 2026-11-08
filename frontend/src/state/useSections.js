import { useCallback, useEffect, useState } from 'react';
import { apiGet } from '../api/http';

const load = () => apiGet('/api/config/sections').catch(() => null);

/**
 * GET /api/config/sections: `{adminTabEnabled, sections}`. Fetched once at start (for `adminTabEnabled`) and
 * again through `refresh` (when Config opens or an interval is saved). `loaded` is true once the first request
 * has finished, successfully or not; a failure keeps what we had (nothing, at first) and leaves the admin tab off.
 */
export function useSections() {
  const [state, setState] = useState({ data: null, loaded: false });

  const apply = useCallback(
    (data) => setState((previous) => ({ data: data ?? previous.data, loaded: true })),
    []
  );

  useEffect(() => {
    let cancelled = false;
    load().then((data) => !cancelled && apply(data));
    return () => {
      cancelled = true;
    };
  }, [apply]);

  const refresh = useCallback(() => load().then(apply), [apply]);

  return {
    data: state.data,
    loaded: state.loaded,
    adminTabEnabled: state.data?.adminTabEnabled === true,
    refresh,
  };
}
