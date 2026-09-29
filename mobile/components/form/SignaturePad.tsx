import React, { useEffect, useRef, useState } from 'react';
import { View, StyleSheet } from 'react-native';
import SignatureCanvas from 'react-signature-canvas';
import { Button, Text } from 'react-native-paper';

interface SignaturePadProps {
  label: string;
  onChange: (base64Data: string) => void;
  value?: string;
}

const SignaturePad: React.FC<SignaturePadProps> = ({
  label,
  onChange,
  value
}) => {
  const ref = useRef<SignatureCanvas>(null);
  const [hasChanged, setHasChanged] = useState(false);
  const [width, setWidth] = useState(0);

  // The canvas needs pixel dimensions; size it to its container.
  useEffect(() => {
    if (value && ref.current && width) ref.current.fromDataURL(value);
  }, [width]);

  const saveSignature = () => {
    // Same data URL shape react-native-signature-canvas produced
    onChange(ref.current.getCanvas().toDataURL('image/png'));
    setHasChanged(false);
  };

  const handleClear = () => {
    ref.current.clear();
    onChange('');
    setHasChanged(false);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <View
        style={styles.signatureContainer}
        onLayout={(event) => setWidth(event.nativeEvent.layout.width)}
      >
        {!!width && (
          <SignatureCanvas
            ref={ref}
            onBegin={() => setHasChanged(true)}
            canvasProps={{
              width,
              height: 200,
              style: { touchAction: 'none', display: 'block' }
            }}
          />
        )}
      </View>
      <View style={styles.buttonContainer}>
        <Button mode="outlined" onPress={handleClear} style={styles.button}>
          Clear
        </Button>
        {hasChanged && (
          <Button
            mode="contained"
            onPress={saveSignature}
            style={styles.button}
          >
            Save Signature
          </Button>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginVertical: 10
  },
  label: {
    fontSize: 16,
    marginBottom: 5
  },
  signatureContainer: {
    height: 200,
    borderColor: '#ccc',
    borderWidth: 1,
    borderRadius: 5,
    overflow: 'hidden',
    backgroundColor: 'white'
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    marginTop: 10,
    gap: 10
  },
  button: {
    marginLeft: 10
  }
});

export default SignaturePad;
