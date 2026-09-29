/**
 * Browser capabilities the PWA depends on. The app used to be a native build
 * where these were compile-time facts; in a browser they vary by device, so
 * every scanner and installer checks here first.
 */

const hasWindow = typeof window !== 'undefined';

export const isStandalone = (): boolean =>
  hasWindow &&
  (window.matchMedia?.('(display-mode: standalone)').matches ||
    (window.navigator as any).standalone === true);

export const isIos = (): boolean =>
  hasWindow &&
  (/iPad|iPhone|iPod/.test(navigator.userAgent) ||
    // iPadOS reports itself as a Mac
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1));

/** Web NFC: Chrome on Android only. iOS exposes no NFC API to the web. */
export const isNfcSupported = (): boolean =>
  hasWindow && 'NDEFReader' in window;

export const isCameraSupported = (): boolean =>
  hasWindow && !!navigator.mediaDevices?.getUserMedia;

/**
 * Chrome fires beforeinstallprompt once, possibly before React mounts, so
 * index.html stashes it on window and this reads it from there.
 */
export interface InstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export const getDeferredInstallPrompt = (): InstallPromptEvent | null =>
  (hasWindow && (window as any).__deferredInstallPrompt) || null;

export const onInstallPromptAvailable = (
  listener: (event: InstallPromptEvent) => void
): (() => void) => {
  if (!hasWindow) return () => {};
  const handler = () => {
    const event = getDeferredInstallPrompt();
    if (event) listener(event);
  };
  window.addEventListener('atlas-install-available', handler);
  return () => window.removeEventListener('atlas-install-available', handler);
};

export const clearDeferredInstallPrompt = () => {
  if (hasWindow) (window as any).__deferredInstallPrompt = null;
};

/**
 * Web NFC reports serial numbers as "04:a2:1f:..."; the native app stored
 * react-native-nfc-manager's "04A21F...". Normalise so tags registered from
 * the old app still resolve to their asset.
 */
export const normaliseNfcSerial = (serial: string): string =>
  serial.replace(/[^0-9a-f]/gi, '').toUpperCase();
