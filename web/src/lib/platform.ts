// Rilevamento piattaforma per la guida all'installazione e il permesso notifiche su iOS.
export function isIos(): boolean {
  const ua = navigator.userAgent;
  // iPadOS si presenta come Mac: lo si riconosce dal touch.
  return /iPad|iPhone|iPod/.test(ua) || (ua.includes('Macintosh') && navigator.maxTouchPoints > 1);
}

export function isStandalone(): boolean {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

// Safari su iOS (non Chrome/Firefox per iOS, che non possono installare PWA prima di iOS 16.4).
export function isIosSafari(): boolean {
  return isIos() && !/CriOS|FxiOS|EdgiOS/.test(navigator.userAgent);
}

export function pushSupported(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}
