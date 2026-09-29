import 'dotenv/config';
import { ExpoConfig, ConfigContext } from 'expo/config';

// Build-time defaults only. In the container, API_URL and AGENT_URL are
// injected at start into /env.js (see docker-entrypoint.sh), so one image
// serves any deployment. config.ts reads that first.
const apiUrl = process.env.API_URL;
const agentUrl = process.env.AGENT_URL;

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'Atlas CMMS',
  slug: 'atlas-cmms',
  version: '1.0.37',
  orientation: 'portrait',
  icon: './assets/images/icon.png',
  scheme: 'atlascmms',
  userInterfaceStyle: 'automatic',
  platforms: ['web'],
  web: {
    bundler: 'metro',
    output: 'single',
    favicon: './assets/images/favicon.png',
    name: 'Atlas CMMS',
    shortName: 'Atlas',
    themeColor: '#5569ff',
    backgroundColor: '#ffffff'
  },
  extra: {
    API_URL: apiUrl,
    AGENT_URL: agentUrl
  },
  plugins: ['expo-font']
});
