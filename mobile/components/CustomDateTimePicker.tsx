import { useContext, useRef } from 'react';
import { View } from './Themed';
import { Text, useTheme } from 'react-native-paper';
import { TouchableOpacity } from 'react-native';
import { CompanySettingsContext } from '../contexts/CompanySettingsContext';

// <input type="datetime-local"> wants local time as YYYY-MM-DDTHH:mm
const toLocalInputValue = (date: Date): string => {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate()
  )}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

/**
 * The browser's own date-time input: on Android and iOS it opens the
 * platform's native picker, which is what the native modal used to do.
 */
export default function CustomDateTimePicker({
  onChange,
  value,
  label
}: {
  onChange: (date: Date) => void;
  value: Date;
  label: string;
}) {
  const theme = useTheme();
  const { getFormattedDate } = useContext(CompanySettingsContext);
  const inputRef = useRef<HTMLInputElement>(null);

  const showDatePicker = () => {
    const input = inputRef.current;
    if (!input) return;
    try {
      input.showPicker();
    } catch {
      // Older Safari has no showPicker(); focusing opens the picker there
      input.focus();
      input.click();
    }
  };

  return (
    <View>
      <TouchableOpacity
        onPress={showDatePicker}
        style={{
          display: 'flex',
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}
      >
        <Text>{label}</Text>
        {value && (
          <Text style={{ color: theme.colors.primary }}>
            {getFormattedDate(value.toString())}
          </Text>
        )}
      </TouchableOpacity>
      <input
        ref={inputRef}
        type="datetime-local"
        value={toLocalInputValue(value ? new Date(value) : new Date())}
        onChange={(event) => {
          if (event.target.value) onChange(new Date(event.target.value));
        }}
        style={{
          position: 'absolute',
          opacity: 0,
          width: 1,
          height: 1,
          pointerEvents: 'none',
          color: theme.colors.onSurface
        }}
      />
    </View>
  );
}
