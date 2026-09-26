// Guida all'installazione su iOS: mostrata in Safari quando AILA non è aperta dalla Home.
export function InstallGuide({ onDismiss }: { onDismiss: () => void }) {
  return (
    <div class="install-guide" role="dialog" aria-modal="true" aria-labelledby="install-title">
      <div class="install-card">
        <img src="/icons/apple-touch-icon.png" alt="" width={72} height={72} class="install-icon" />
        <h2 id="install-title">Installa AILA</h2>
        <p>Aggiungila alla schermata Home: si apre a tutto schermo, funziona offline e può mandarti le notifiche.</p>
        <ol>
          <li>
            Tocca <strong>Condividi</strong>
            <svg class="share-icon" viewBox="0 0 24 24" aria-label="icona Condividi" role="img">
              <path d="M12 3v12M7.5 7.5 12 3l4.5 4.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M8 11H6a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-2" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
            nella barra di Safari.
          </li>
          <li>
            Scorri e scegli <strong>Aggiungi alla schermata Home</strong>.
          </li>
          <li>
            Tocca <strong>Aggiungi</strong>, poi apri AILA dall'icona sulla Home.
          </li>
        </ol>
        <button class="btn btn-block" onClick={onDismiss}>
          Continua nel browser
        </button>
      </div>
    </div>
  );
}
