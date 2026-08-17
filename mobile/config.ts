import Constants from 'expo-constants';
import AsyncStorage from '@react-native-async-storage/async-storage';

export const googleMapsConfig = {
  apiKey: process.env.GOOGLE_KEY
};

// Default API URL from Expo config
const defaultApiUrl = Constants.expoConfig.extra.API_URL;
export const IS_LOCALHOST = false;

// Function to get the API URL (either custom or default)
export const getApiUrl = async (): Promise<string> => {
  try {
    // Try to get custom URL from AsyncStorage
    const customUrl = await AsyncStorage.getItem('customApiUrl');

    // Use custom URL if available, otherwise use default
    const rawApiUrl = customUrl || defaultApiUrl;
    return rawApiUrl.endsWith('/') ? rawApiUrl : rawApiUrl + '/';
  } catch (error) {
    // Fallback to default URL if there's an error
    const rawApiUrl = defaultApiUrl;
    return rawApiUrl.endsWith('/') ? rawApiUrl : rawApiUrl + '/';
  }
};

/**
 * The agent, for the on-machine assistant.
 *
 * Door 2: the technician's phone is the highest-value moment in this product
 * and MCP clients are desktop applications, so mobile talks to the agent
 * directly with the user's own token.
 */
const defaultAgentUrl = Constants.expoConfig.extra.AGENT_URL;

export const getAgentUrl = async (): Promise<string> => {
  try {
    const customUrl = await AsyncStorage.getItem('customAgentUrl');
    const raw = customUrl || defaultAgentUrl || '';
    if (!raw) return '';
    return raw.endsWith('/') ? raw.slice(0, -1) : raw;
  } catch (error) {
    return defaultAgentUrl || '';
  }
};
