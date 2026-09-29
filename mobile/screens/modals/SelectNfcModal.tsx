import { Alert, StyleSheet } from 'react-native';
import { View } from '../../components/Themed';
import * as React from 'react';
import { useEffect } from 'react';
import { RootStackScreenProps } from '../../types';
import { useTranslation } from 'react-i18next';
import { ActivityIndicator, Text } from 'react-native-paper';
import { isNfcSupported, normaliseNfcSerial } from '../../utils/pwa';

/**
 * Web NFC (NDEFReader). Chrome on Android only, and only on HTTPS; the tag's
 * serial number is what the native app used as the NFC id.
 */
export default function SelectNfcModal({
  navigation,
  route
}: RootStackScreenProps<'SelectNfc'>) {
  const { onChange } = route.params;
  const { t } = useTranslation();

  useEffect(() => {
    if (!isNfcSupported()) {
      Alert.alert(
        t('error'),
        t(
          'nfc_not_supported',
          'This browser cannot read NFC tags. Use Chrome on Android, or scan a barcode instead.'
        ),
        [{ text: 'Ok', onPress: () => navigation.goBack() }]
      );
      return;
    }

    const controller = new AbortController();
    let done = false;
    // @ts-ignore - NDEFReader is not in the TypeScript DOM lib yet
    const reader = new window.NDEFReader();

    reader.onreading = (event: { serialNumber?: string }) => {
      if (done) return;
      done = true;
      controller.abort();
      const tagId = event.serialNumber
        ? normaliseNfcSerial(event.serialNumber)
        : null;
      if (tagId) {
        onChange(tagId);
      } else {
        Alert.alert(t('error'), t('tag_not_found'), [
          { text: 'Ok', onPress: () => navigation.goBack() }
        ]);
      }
    };

    reader
      .scan({ signal: controller.signal })
      .catch((error: Error) => {
        if (done || controller.signal.aborted) return;
        Alert.alert(t('error'), t(error.message), [
          { text: 'Ok', onPress: () => navigation.goBack() }
        ]);
      });

    return () => {
      done = true;
      controller.abort();
    };
  }, []);

  return (
    <View style={styles.container}>
      <Text style={{ marginBottom: 20 }} variant={'titleLarge'}>
        {t('scanning')}
      </Text>
      <ActivityIndicator size={'large'} />
      <Text style={{ marginTop: 20, textAlign: 'center' }}>
        {t('nfc_hold_tag', 'Hold the tag against the back of the phone')}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20
  }
});
