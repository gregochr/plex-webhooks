import { useMemo } from 'react';
import Header from './components/Header';
import LoginDialog from './components/LoginDialog';
import Rules from './components/Rules';
import Theater from './components/Theater';
import { modeStatusText, resolveView } from './lib/view';
import { sceneSlot } from './lib/scenes';
import { useCineLight } from './state/useCineLight';
import { useConfigActions } from './state/useConfigActions';
import { useNarrow } from './state/useMediaQuery';

export default function App() {
  const cine = useCineLight();
  const narrow = useNarrow();
  const { cycleScene, toggleRule, failed } = useConfigActions(cine);
  const resolved = resolveView(cine.view, cine.nowPlaying?.state);
  const slots = useMemo(
    () => ({
      start: sceneSlot(cine.scenes, cine.config, 'start'),
      stop: sceneSlot(cine.scenes, cine.config, 'stop'),
    }),
    [cine.scenes, cine.config]
  );

  return (
    <div className="app" data-mode={resolved}>
      <Header view={cine.view} resolved={resolved} onSetView={cine.setView} />
      <main className="app-main">
        {failed && (
          <p role="status" className="save-failed">
            That change was not saved.
          </p>
        )}
        {resolved === 'theater' ? (
          <Theater
            nowPlaying={cine.nowPlaying}
            history={cine.history}
            catalog={cine.scenes}
            slots={slots}
            onCycle={cycleScene}
            narrow={narrow}
            statusNote={modeStatusText(cine.view)}
          />
        ) : (
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
        )}
      </main>
      <LoginDialog />
    </div>
  );
}
