import { StyleSheet } from 'react-native';

import { View } from '../../components/Themed';
import * as React from 'react';
import { useEffect, useRef, useState } from 'react';
import { RootStackScreenProps } from '../../types';
import { useTranslation } from 'react-i18next';
import { Text } from 'react-native-paper';
import { BrowserMultiFormatReader, IScannerControls } from '@zxing/browser';
import { isCameraSupported } from '../../utils/pwa';

/**
 * Camera scanning in the browser. ZXing decodes QR and the 1D formats
 * (Code 128, EAN, ...) that parts and assets are labelled with; it is bundled,
 * so scanning works offline and without a CDN.
 */
export default function SelectBarcodeModal({
  navigation,
  route
}: RootStackScreenProps<'SelectBarcode'>) {
  const { onChange } = route.params;
  const { t } = useTranslation();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState<string | null>(
    isCameraSupported()
      ? null
      : t(
          'camera_not_supported',
          'This browser cannot open the camera. It needs HTTPS and camera permission.'
        )
  );

  useEffect(() => {
    if (!isCameraSupported() || !videoRef.current) return;
    let controls: IScannerControls | null = null;
    let done = false;
    const reader = new BrowserMultiFormatReader();

    reader
      .decodeFromConstraints(
        { video: { facingMode: { ideal: 'environment' } }, audio: false },
        videoRef.current,
        (result, _err, scannerControls) => {
          if (!result || done) return;
          done = true;
          scannerControls.stop();
          onChange(result.getText());
        }
      )
      .then((started) => {
        controls = started;
        if (done) started.stop();
      })
      .catch(() => setError(t('no_access_to_camera')));

    return () => {
      done = true;
      controls?.stop();
    };
  }, []);

  return (
    <View style={styles.container}>
      {error ? (
        <View style={styles.message}>
          <Text variant={'titleMedium'}>{error}</Text>
        </View>
      ) : (
        <>
          <video
            ref={videoRef}
            muted
            playsInline
            autoPlay
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
          <View style={styles.hint}>
            <Text style={{ color: 'white', textAlign: 'center' }}>
              {t(
                'point_camera_at_code',
                'Point the camera at a barcode or QR code'
              )}
            </Text>
          </View>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black'
  },
  message: {
    margin: 20,
    padding: 20,
    borderRadius: 10
  },
  hint: {
    position: 'absolute',
    bottom: 40,
    left: 20,
    right: 20,
    padding: 12,
    borderRadius: 8,
    backgroundColor: 'rgba(0,0,0,0.6)'
  }
});
