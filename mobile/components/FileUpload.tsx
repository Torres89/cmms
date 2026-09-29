import { View } from './Themed';
import * as ImagePicker from 'expo-image-picker';
import * as DocumentPicker from 'expo-document-picker';
import * as React from 'react';
import { useContext, useRef, useState } from 'react';
import {
  Alert,
  Image,
  ScrollView,
  Text,
  TouchableOpacity
} from 'react-native';
import { IconButton, useTheme } from 'react-native-paper';
import { useTranslation } from 'react-i18next';
import mime from 'mime';
import { ActionSheetRef, SheetManager } from 'react-native-actions-sheet';
import { CustomSnackBarContext } from '../contexts/CustomSnackBarContext';
import { IFile } from '../models/file';
import { assetToFile } from '../utils/overall';
import {
  DocumentPickerOptions,
  DocumentPickerResult
} from 'expo-document-picker';

interface OwnProps {
  title: string;
  type: 'image' | 'file' | 'spreadsheet';
  multiple: boolean;
  description: string;
  onChange: (files: IFile[]) => void;
}

export default function FileUpload({
  title,
  type,
  multiple,
  onChange
}: OwnProps) {
  const theme = useTheme();
  const actionSheetRef = useRef<ActionSheetRef>(null);
  const [images, setImages] = useState<IFile[]>([]);
  const [files, setFiles] = useState<IFile[]>([]);
  const { t } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const maxFileSize: number = 7;

  const onChangeInternal = (files: IFile[], type: 'file' | 'image') => {
    if (type === 'file') {
      setFiles(files);
    } else {
      setImages(files);
    }
    onChange(files);
  };
  const isMoreThanTheMB = (fileSize: number, limit: number) => {
    return fileSize / 1024 / 1024 > limit;
  };
  // Browsers ask for camera and photo access themselves when the file input
  // opens, so there is no permission step here.
  const takePhoto = async () => {
    try {
      const result = await ImagePicker.launchCameraAsync({
        mediaTypes: ImagePicker.MediaTypeOptions.Images,
        quality: 1
      });
      if (result.canceled === true) return;
      await onImagePicked(result);
    } catch (e) {
      console.error('Error taking photo:', e);
      Alert.alert('Error', 'Failed to take photo. Please try again.');
    }
  };
  const pickImage = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ImagePicker.MediaTypeOptions.Images,
        allowsMultipleSelection: multiple,
        selectionLimit: multiple ? 10 : 1,
        quality: 1
      });
      if (result.canceled === true) return;
      await onImagePicked(result);
      return result;
    } catch (error) {
      console.error('Error picking image:', error);
      Alert.alert('Error', 'Failed to pick image. Please try again.');
    }
  };
  const checkSize = (size?: number) => {
    if (size === undefined || size === null) return;
    if (isMoreThanTheMB(size, maxFileSize)) {
      showSnackBar(t('max_file_size_error', { size: maxFileSize }), 'error');
      throw new Error(t('max_file_size_error', { size: maxFileSize }));
    }
  };
  const onImagePicked = async (result: ImagePicker.ImagePickerResult) => {
    if (!result.canceled) {
      for (const asset of result.assets) {
        checkSize(asset.file?.size ?? asset.fileSize);
      }
      onChangeInternal(result.assets.map(assetToFile), 'image');
    }
  };
  const pickFile = async () => {
    try {
      // Pass the 'multiple' prop to enable multi-file selection if needed
      const options: DocumentPickerOptions = {
        type:
          type === 'spreadsheet'
            ? [
                'application/vnd.ms-excel',
                'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                'text/csv'
              ]
            : '*/*', // Default to all file types
        multiple
      };

      const result: DocumentPickerResult =
        await DocumentPicker.getDocumentAsync(options);
      if (
        result.canceled === true ||
        !result.assets ||
        result.assets.length === 0
      ) {
        console.log('Document picker was canceled or no file selected');
        return;
      }

      // Process selected files (currently only handles the first asset due to existing component logic)
      const selectedAssets = result.assets;
      const filesToUpload: IFile[] = [];

      // Loop through selected assets to perform size checks
      for (const asset of selectedAssets) {
        checkSize(asset.file?.size ?? asset.size);

        filesToUpload.push({
          uri: asset.uri,
          name: asset.name,
          type:
            mime.getType(asset.name) ||
            asset.mimeType ||
            'application/octet-stream', // Use mime or the asset's mimeType
          file: asset.file
        });

        // Break the loop if 'multiple' is false, as the current display logic only shows one file.
        if (!multiple) break;
      }

      // Pass the selected file(s) to the internal change handler
      onChangeInternal(filesToUpload, 'file');
    } catch (error) {
      console.error('Error picking document:', error);
    }
  };
  const onPress = () => {
    if (type === 'image')
      SheetManager.show('upload-file-sheet', {
        payload: {
          onPickImage: pickImage,
          onTakePhoto: takePhoto
        }
      });
    else pickFile();
  };
  return (
    <View style={{ display: 'flex', flexDirection: 'column' }}>
      <TouchableOpacity onPress={onPress}>
        <Text>{title}</Text>
      </TouchableOpacity>
      <ScrollView>
        {type === 'image' &&
          !!images.length &&
          images.map((image) => (
            <View>
              <Image source={{ uri: image.uri }} style={{ height: 200 }} />
              <IconButton
                style={{ position: 'absolute', top: 10, right: 10 }}
                onPress={() => {
                  onChangeInternal(
                    images.filter((item) => item.uri !== image.uri),
                    'image'
                  );
                }}
                icon={'close-circle'}
                iconColor={theme.colors.error}
              />
            </View>
          ))}
        {type !== 'image' && // Covers 'file' and 'spreadsheet' types
          !!files.length &&
          files.map((file, index) => (
            <View
              key={file.uri} // <--- Use a unique key
              style={{
                display: 'flex',
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
                paddingVertical: 1 // Optional: Add padding for separation
              }}
            >
              <Text style={{ color: theme.colors.primary, flexShrink: 1 }}>
                {file.name}
              </Text>
              <IconButton
                onPress={() => {
                  onChangeInternal(
                    files.filter((_, i) => i !== index),
                    'file'
                  );
                }}
                icon={'close-circle'}
                iconColor={theme.colors.error}
              />
            </View>
          ))}
      </ScrollView>
    </View>
  );
}
