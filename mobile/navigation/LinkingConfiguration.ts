/**
 * Browser history for the PWA.
 *
 * Linking is enabled so every screen push becomes a history entry: that is
 * what makes the Android back gesture and the browser back button step back a
 * screen instead of closing the app. React Navigation keeps the full state
 * for each entry in memory and restores it on popstate, so route params -
 * including the onChange callbacks the modal screens receive - survive.
 *
 * What we deliberately do not do is put params in the URL. They include
 * functions, and a reload cannot restore those, so the path is just the screen
 * name and a reload (or a stale path) starts from home. Links shared from the
 * web app (/app/work-orders/12) are handled in App.tsx instead.
 */

import { LinkingOptions } from '@react-navigation/native';

import { RootStackParamList } from '../types';

const focusedRouteName = (state: any): string | undefined => {
  let current = state;
  let name: string | undefined;
  while (current?.routes?.length) {
    const route = current.routes[current.index ?? current.routes.length - 1];
    name = route.name;
    current = route.state;
  }
  return name;
};

const linking: LinkingOptions<RootStackParamList> = {
  prefixes: [],
  getPathFromState: (state) => {
    const name = focusedRouteName(state);
    return name ? `/${name}` : '/';
  },
  // Always start from the navigator's default screen.
  getStateFromPath: () => undefined
};

export default linking;
