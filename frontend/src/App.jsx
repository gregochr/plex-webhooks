import { useCallback, useEffect, useMemo, useState } from 'react';
import { apiGet } from './api/http';
import Config from './components/Config';
import Debug from './components/Debug';
import EventMonitor from './components/EventMonitor';
import Header from './components/Header';
import Lighting from './components/Lighting';
import LoginDialog from './components/LoginDialog';
import Rules from './components/Rules';
import Theater from './components/Theater';
import { readShowAllTypes, writeShowAllTypes } from './lib/prefs';
import { modeStatusText, resolveView } from './lib/view';
import { sceneSlot } from './lib/scenes';
import { useCineLight } from './state/useCineLight';
import { useConfigActions } from './state/useConfigActions';
import { useHashRoute } from './state/useHashRoute';
import { useNarrow } from './state/useMediaQuery';
import { useSections } from './state/useSections';

/** On opening Lighting, fetches whichever bridge status the initial seed and the SSE stream have not supplied. */
function useStatusSeed(route, { hueStatus, dirigeraStatus, dispatch }) {
  const needHue = hueStatus === null;
  const needDirigera = dirigeraStatus === null;
  useEffect(() => {
    if (route !== 'lighting' || (!needHue && !needDirigera)) {
      return undefined;
    }
    let cancelled = false;
    const wanted = [
      needHue && ['hueStatus', '/api/hue/status'],
      needDirigera && ['dirigeraStatus', '/api/dirigera/status'],
    ].filter(Boolean);
    Promise.all(
      wanted.map(([key, path]) =>
        apiGet(path).then(
          (body) => [key, body],
          () => null
        )
      )
    ).then((entries) => {
      if (!cancelled) {
        dispatch({ type: 'seed', data: Object.fromEntries(entries.filter(Boolean)), force: false });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [route, needHue, needDirigera, dispatch]);
}

export default function App() {
  const cine = useCineLight();
  const narrow = useNarrow();
  const route = useHashRoute();
  const sections = useSections();
  const [showAll, setShowAll] = useState(readShowAllTypes);
  const { cycleScene, setScene, toggleRule, toggleCell, setHueEnabled, failed } =
    useConfigActions(cine);
  useStatusSeed(route, cine);
  const refreshSections = sections.refresh;

  useEffect(() => {
    if (route === 'config') {
      refreshSections();
    }
  }, [route, refreshSections]);

  const onScreen = route !== 'mode';
  const resolved = onScreen ? 'rules' : resolveView(cine.view, cine.nowPlaying?.state);
  const slots = useMemo(
    () => ({
      start: sceneSlot(cine.scenes, cine.config, 'start'),
      stop: sceneSlot(cine.scenes, cine.config, 'stop'),
    }),
    [cine.scenes, cine.config]
  );

  // The mode button returns to the room as well as choosing the view.
  const { setView } = cine;
  const onSetView = useCallback(
    (view) => {
      if (window.location.hash !== '') {
        window.location.hash = '';
      }
      setView(view);
    },
    [setView]
  );
  const onSetShowAll = useCallback((value) => {
    writeShowAllTypes(value);
    setShowAll(value);
  }, []);

  let body;
  if (route === 'events') {
    body = (
      <EventMonitor
        history={cine.history}
        catalog={cine.scenes}
        adminTabEnabled={sections.adminTabEnabled}
        narrow={narrow}
      />
    );
  } else if (route === 'lighting') {
    body = (
      <Lighting
        hueStatus={cine.hueStatus}
        dirigeraStatus={cine.dirigeraStatus}
        config={cine.config}
        slots={slots}
        showAll={showAll}
        onSetScene={setScene}
      />
    );
  } else if (route === 'config') {
    body = (
      <Config
        config={cine.config}
        sections={sections.data}
        onToggle={toggleCell}
        onIntervalsSaved={refreshSections}
      />
    );
  } else if (route === 'debug') {
    body = (
      <Debug
        loaded={sections.loaded}
        adminTabEnabled={sections.adminTabEnabled}
        hueEnabled={cine.config?.hueIntegrationEnabled === true}
        showAll={showAll}
        onSetHueEnabled={setHueEnabled}
        onSetShowAll={onSetShowAll}
      />
    );
  } else if (resolved === 'theater') {
    body = (
      <Theater
        nowPlaying={cine.nowPlaying}
        history={cine.history}
        catalog={cine.scenes}
        slots={slots}
        onCycle={cycleScene}
        narrow={narrow}
        statusNote={modeStatusText(cine.view)}
      />
    );
  } else {
    body = (
      <Rules
        config={cine.config}
        nowPlaying={cine.nowPlaying}
        history={cine.history}
        catalog={cine.scenes}
        slots={slots}
        onCycle={cycleScene}
        onToggle={toggleRule}
        narrow={narrow}
      />
    );
  }

  return (
    <div className="app" data-mode={resolved}>
      <Header
        view={cine.view}
        resolved={resolved}
        onSetView={onSetView}
        adminTabEnabled={sections.adminTabEnabled}
      />
      <main className="app-main">
        {failed && (
          <p role="status" className="save-failed">
            That change was not saved.
          </p>
        )}
        {body}
      </main>
      <LoginDialog />
    </div>
  );
}
