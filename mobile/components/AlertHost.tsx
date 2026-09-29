import { useEffect, useState } from 'react';
import { Alert, AlertButton, AlertOptions } from 'react-native';
import { Button, Dialog, Portal, Text } from 'react-native-paper';

/**
 * react-native-web ships Alert.alert as an empty function, so every confirm
 * dialog in the app (delete, "no asset found - create one?", permission
 * errors) would silently do nothing. This routes Alert.alert to a Paper
 * dialog instead. Call sites stay unchanged.
 */
interface PendingAlert {
  title: string;
  message?: string;
  buttons: AlertButton[];
  options?: AlertOptions;
}

let show: ((alert: PendingAlert) => void) | null = null;
const queue: PendingAlert[] = [];

Alert.alert = (
  title: string,
  message?: string,
  buttons?: AlertButton[],
  options?: AlertOptions
) => {
  const alert: PendingAlert = {
    title,
    message,
    buttons: buttons?.length ? buttons : [{ text: 'OK' }],
    options
  };
  if (show) show(alert);
  else queue.push(alert);
};

export default function AlertHost() {
  const [alerts, setAlerts] = useState<PendingAlert[]>([]);

  useEffect(() => {
    show = (alert) => setAlerts((current) => [...current, alert]);
    if (queue.length) setAlerts(queue.splice(0));
    return () => {
      show = null;
    };
  }, []);

  const current = alerts[0];
  const close = () => setAlerts((all) => all.slice(1));

  const onDismiss = () => {
    if (current?.options?.cancelable === false) return;
    close();
    current?.options?.onDismiss?.();
  };

  return (
    <Portal>
      <Dialog visible={!!current} onDismiss={onDismiss}>
        {current && (
          <>
            <Dialog.Title>{current.title}</Dialog.Title>
            {!!current.message && (
              <Dialog.Content>
                <Text variant="bodyMedium">{current.message}</Text>
              </Dialog.Content>
            )}
            <Dialog.Actions>
              {current.buttons.map((button, index) => (
                <Button
                  key={index}
                  textColor={
                    button.style === 'destructive' ? '#d32f2f' : undefined
                  }
                  onPress={() => {
                    close();
                    button.onPress?.();
                  }}
                >
                  {button.text ?? 'OK'}
                </Button>
              ))}
            </Dialog.Actions>
          </>
        )}
      </Dialog>
    </Portal>
  );
}
