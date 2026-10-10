// Shell: router su hash, barra di navigazione, guida installazione iOS, avviso aggiornamento.
import { useEffect, useState } from 'preact/hooks';
import { isIosSafari, isStandalone } from '../lib/platform';
import { useSession } from '../lib/session';
import { Board } from './Board';
import { Gita } from './Gita';
import { Calendar } from './Calendar';
import { CircularDetail } from './CircularDetail';
import { Circulars } from './Circulars';
import { Icon, Loading } from './common';
import type { IconName } from './common';
import { InstallGuide } from './InstallGuide';
import { Login } from './Login';
import { SeatMap } from './SeatMap';
import { Settings } from './Settings';

// "Impostazioni" come il titolo della pagina e la voce dell'app (prima la tab diceva "Opzioni").
const TABS: { path: string; label: string; icon: IconName }[] = [
  { path: 'circolari', label: 'Circolari', icon: 'circulars' },
  { path: 'bacheca', label: 'Bacheca', icon: 'board' },
  { path: 'calendario', label: 'Calendario', icon: 'calendar' },
  { path: 'mappa', label: 'Posti', icon: 'seats' },
  { path: 'impostazioni', label: 'Impostazioni', icon: 'settings' },
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
    case 'gita':
      page = <Gita />;
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
        <div class="tabbar-inner">
          {TABS.map((t) => (
            <a key={t.path} href={`#/${t.path}`} class={section === t.path ? 'active' : ''} aria-current={section === t.path ? 'page' : undefined}>
              <Icon name={t.icon} class="tab-icon" />
              <span class="tab-label">{t.label}</span>
            </a>
          ))}
        </div>
      </nav>
      {guide}
    </>
  );
}
