import { useEffect, useState } from 'react';
import { Banner } from 'react-native-paper';
import { useTranslation } from 'react-i18next';
import {
  clearDeferredInstallPrompt,
  getDeferredInstallPrompt,
  InstallPromptEvent,
  isIos,
  isStandalone,
  onInstallPromptAvailable
} from '../utils/pwa';

const DISMISSED_KEY = 'installPromptDismissedAt';
// Ask again after a week rather than never.
const DISMISS_FOR_MS = 7 * 24 * 3600 * 1000;

const wasDismissedRecently = (): boolean => {
  try {
    const at = Number(localStorage.getItem(DISMISSED_KEY));
    return !!at && Date.now() - at < DISMISS_FOR_MS;
  } catch {
    return false;
  }
};

/**
 * Android/Chrome: a real Install button (beforeinstallprompt).
 * iOS/Safari: there is no install API, so explain Share -> Add to Home Screen.
 * Hidden once the app runs from the home screen.
 */
export default function InstallPrompt() {
  const { t } = useTranslation();
  const [installEvent, setInstallEvent] = useState<InstallPromptEvent | null>(
    getDeferredInstallPrompt()
  );
  const [dismissed, setDismissed] = useState<boolean>(wasDismissedRecently());

  useEffect(() => onInstallPromptAvailable(setInstallEvent), []);

  if (isStandalone() || dismissed) return null;
  const ios = isIos();
  if (!installEvent && !ios) return null;

  const dismiss = () => {
    setDismissed(true);
    try {
      localStorage.setItem(DISMISSED_KEY, String(Date.now()));
    } catch {}
  };

  const install = async () => {
    await installEvent.prompt();
    await installEvent.userChoice;
    clearDeferredInstallPrompt();
    setInstallEvent(null);
  };

  return (
    <Banner
      visible
      icon="cellphone-arrow-down"
      actions={[
        { label: t('not_now', 'Not now'), onPress: dismiss },
        ...(installEvent
          ? [{ label: t('install', 'Install'), onPress: install }]
          : [])
      ]}
    >
      {`${t('install_app_title', 'Install Atlas on this phone')}. ${
        ios
          ? t(
              'install_app_ios',
              'Tap the Share button, then "Add to Home Screen".'
            )
          : t(
              'install_app_description',
              'Opens full screen from your home screen, like an app.'
            )
      }`}
    </Banner>
  );
}
