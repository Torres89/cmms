import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PersistGate } from 'redux-persist/integration/react';
import useCachedResources from './hooks/useCachedResources';
import useColorScheme from './hooks/useColorScheme';
import Navigation from './navigation';
import { Provider } from 'react-redux';
import store, { persistor } from './store';
import { CompanySettingsProvider } from './contexts/CompanySettingsContext';
import { CustomSnackbarProvider } from './contexts/CustomSnackBarContext';
import { AuthProvider } from './contexts/AuthContext';
import FlashMessage from 'react-native-flash-message';

import Constants from 'expo-constants';

import { Provider as PaperProvider } from 'react-native-paper';
import { useEffect } from 'react';
import { Linking } from 'react-native';
import { SheetProvider } from 'react-native-actions-sheet';
import './components/actionSheets/sheets';
import { navigate } from './navigation/RootNavigation';
import { isNumeric } from './utils/validators';
import { customTheme } from './custom-theme';
import { RootLayout } from './components/RootLayout';
import AlertHost from './components/AlertHost';
import InstallPrompt from './components/InstallPrompt';

export default function App() {
  const isLoadingComplete = useCachedResources();
  const colorScheme = useColorScheme();

  useEffect(() => {
    let subscription;
    const handleDeepLink = async () => {
      // Get the initial URL when the app is launched from the deep link
      const initialUrl = await Linking.getInitialURL();
      handleUrl(initialUrl);
      // Listen to incoming deep links while the app is open
      subscription = Linking.addEventListener('url', ({ url }) =>
        handleUrl(url)
      );
    };

    const handleUrl = (url) => {
      if (url) {
        const { pathname: path } = new URL(url);
        if (path.startsWith('/app/')) {
          const arr = path.split('/');
          if (arr[2] === 'work-orders') {
            if (isNumeric(arr[3]))
              navigate('WODetails', { id: Number(arr[3]) });
            else navigate('WorkOrders', { filterFields: [] });
          } else {
            if (arr[2] === 'requests') {
              if (isNumeric(arr[3]))
                navigate('RequestDetails', { id: Number(arr[3]) });
              else navigate('Requests');
            }
          }
        }
      }
    };

    handleDeepLink();

    // Clean up event listeners
    return () => {
      if (subscription) subscription.remove();
    };
  }, []);

  if (!isLoadingComplete) {
    return null;
  } else {
    return (
      <SafeAreaProvider>
        <Provider store={store}>
          <PersistGate loading={null} persistor={persistor}>
            <AuthProvider>
              <CompanySettingsProvider>
                <PaperProvider theme={customTheme}>
                  <CustomSnackbarProvider>
                    <SheetProvider>
                      <RootLayout>
                        <InstallPrompt />
                        <FlashMessage
                          position="top"
                          statusBarHeight={Constants.statusBarHeight}
                        />
                        <Navigation colorScheme={colorScheme} />
                      </RootLayout>
                      <StatusBar />
                      <AlertHost />
                    </SheetProvider>
                  </CustomSnackbarProvider>
                </PaperProvider>
              </CompanySettingsProvider>
            </AuthProvider>
          </PersistGate>
        </Provider>
      </SafeAreaProvider>
    );
  }
}
