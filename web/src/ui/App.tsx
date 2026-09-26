// Shell: router su hash, barra di navigazione, guida installazione iOS, avviso aggiornamento.
import { useEffect, useState } from 'preact/hooks';
import { isIosSafari, isStandalone } from '../lib/platform';
import { useSession } from '../lib/session';
import { Board } from './Board';
import { Calendar } from './Calendar';
import { CircularDetail } from './CircularDetail';
import { Circulars } from './Circulars';
import { Loading } from './common';
import { InstallGuide } from './InstallGuide';
import { Login } from './Login';
import { SeatMap } from './SeatMap';
import { Settings } from './Settings';

const TABS = [
  { path: 'circolari', label: 'Circolari', icon: '📄' },
  { path: 'bacheca', label: 'Bacheca', icon: '💡' },
  { path: 'calendario', label: 'Calendario', icon: '📅' },
  { path: 'mappa', label: 'Posti', icon: '🪑' },
  { path: 'impostazioni', label: 'Opzioni', icon: '⚙️' },
];

function useHashRoute(): string[] {
  const read = () => location.hash.replace(/^#\/?/, '').split('/').filter(Boolean);
  const [parts, setParts] = useState(read);
  useEffect(() => {
    const onChange = () => {
      setParts(read());
      window.scrollTo(0, 0);
    };
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return parts;
}

const GUIDE_DISMISSED = 'aila-install-guide-dismissed';

function guideDismissed(): boolean {
  try {
    return sessionStorage.getItem(GUIDE_DISMISSED) === '1';
  } catch {
    return false;
  }
}

export function App({ updateReady, onUpdate }: { updateReady: boolean; onUpdate: () => void }) {
  const { session, ready } = useSession();
  const route = useHashRoute();
  const [showGuide, setShowGuide] = useState(() => isIosSafari() && !isStandalone() && !guideDismissed());

  const guide = showGuide && (
    <InstallGuide
      onDismiss={() => {
        try {
          sessionStorage.setItem(GUIDE_DISMISSED, '1');
        } catch {
          // storage non disponibile: la guida torna al prossimo avvio
        }
        setShowGuide(false);
      }}
    />
  );

  if (!ready) return <Loading />;
  if (!session) {
    return (
      <>
        <Login />
        {guide}
      </>
    );
  }

  const [section = 'circolari', param] = route;
  let page;
  switch (section) {
    case 'circolari':
      page = param && /^\d+$/.test(param) ? <CircularDetail number={Number(param)} /> : <Circulars />;
      break;
    case 'bacheca':
      page = <Board />;
      break;
    case 'calendario':
      page = <Calendar />;
      break;
    case 'mappa':
      page = <SeatMap />;
      break;
    case 'impostazioni':
      page = <Settings />;
      break;
    default:
      page = <Circulars />;
  }

  return (
    <>
      {updateReady && (
        <div class="update-banner" role="status">
          Nuova versione di AILA disponibile.
          <button class="btn btn-small" onClick={onUpdate}>
            Aggiorna
          </button>
        </div>
      )}
      <main class="content">{page}</main>
      <nav class="tabbar" aria-label="Sezioni">
        {TABS.map((t) => (
          <a key={t.path} href={`#/${t.path}`} class={section === t.path ? 'active' : ''} aria-current={section === t.path ? 'page' : undefined}>
            <span aria-hidden="true">{t.icon}</span>
            <span class="tab-label">{t.label}</span>
          </a>
        ))}
      </nav>
      {guide}
    </>
  );
}
