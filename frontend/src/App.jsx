import Header from './components/Header';
import LoginDialog from './components/LoginDialog';
import { resolveView } from './lib/view';
import { useCineLight } from './state/useCineLight';

export default function App() {
  const cine = useCineLight();
  const resolved = resolveView(cine.view, cine.nowPlaying?.state);

  return (
    <div className="app" data-mode={resolved}>
      <Header view={cine.view} resolved={resolved} onSetView={cine.setView} />
      <main className="app-main">
        {/* Placeholders: PR 2b builds Theater and House rules. */}
        {resolved === 'theater' ? (
          <section aria-label="Theater mode" className="placeholder">
            Theater mode
          </section>
        ) : (
          <section aria-label="House rules" className="placeholder">
            House rules
          </section>
        )}
      </main>
      <LoginDialog />
    </div>
  );
}
