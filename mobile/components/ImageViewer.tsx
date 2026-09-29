import { useEffect, useState } from 'react';
import { Image, Modal, StyleSheet, View } from 'react-native';
import { IconButton, Text } from 'react-native-paper';

/**
 * Fullscreen viewer with previous/next, replacing react-native-image-viewing
 * (which ships native-only files). Pinch-zoom is left to the browser.
 */
export default function ImageViewer({
  images,
  imageIndex,
  visible,
  onRequestClose
}: {
  images: { uri: string }[];
  imageIndex: number;
  visible: boolean;
  onRequestClose: () => void;
}) {
  const [index, setIndex] = useState(Math.max(imageIndex, 0));

  useEffect(() => {
    if (visible) setIndex(Math.max(imageIndex, 0));
  }, [visible, imageIndex]);

  const image = images[index];
  return (
    <Modal
      visible={visible && !!image}
      transparent
      animationType="fade"
      onRequestClose={onRequestClose}
    >
      <View style={styles.backdrop}>
        {image && (
          <Image
            source={{ uri: image.uri }}
            style={styles.image}
            resizeMode="contain"
          />
        )}
        <IconButton
          icon="close"
          iconColor="white"
          size={28}
          style={styles.close}
          onPress={onRequestClose}
        />
        {images.length > 1 && (
          <View style={styles.pager}>
            <IconButton
              icon="chevron-left"
              iconColor="white"
              disabled={index === 0}
              onPress={() => setIndex(index - 1)}
            />
            <Text style={{ color: 'white' }}>{`${index + 1} / ${
              images.length
            }`}</Text>
            <IconButton
              icon="chevron-right"
              iconColor="white"
              disabled={index === images.length - 1}
              onPress={() => setIndex(index + 1)}
            />
          </View>
        )}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'black',
    justifyContent: 'center'
  },
  image: {
    width: '100%',
    height: '80%'
  },
  close: {
    position: 'absolute',
    top: 12,
    right: 12
  },
  pager: {
    position: 'absolute',
    bottom: 24,
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center'
  }
});
