import { useEffect, useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  View
} from 'react-native';
import {
  ActivityIndicator,
  Chip,
  IconButton,
  Surface,
  Text,
  TextInput,
  useTheme
} from 'react-native-paper';
import { useTranslation } from 'react-i18next';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getAgentUrl } from '../../config';
import { RootStackScreenProps } from '../../types';

interface Message {
  role: 'user' | 'assistant';
  content: string;
}

/**
 * The technician standing at the machine, asking it a question.
 *
 * This is the moment the whole product exists for, and it is the one MCP
 * clients cannot reach — they are desktop applications. So the phone talks to
 * the agent directly (Door 2), pinned to the scanned machine, with a fresh
 * dossier injected on every turn so the answer reflects the machine's state
 * right now rather than what the manual says about the model in general.
 */
export default function AssetChatScreen({
  route,
  navigation
}: RootStackScreenProps<'AssetChat'>) {
  const { assetId, assetName } = route.params;
  const { t } = useTranslation();
  const theme = useTheme();
  const [messages, setMessages] = useState<Message[]>([
    {
      role: 'assistant',
      content: t('ask_me_about_this_machine', { name: assetName })
    }
  ]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [sessionId, setSessionId] = useState<string | undefined>();
  const scrollRef = useRef<ScrollView>(null);

  useEffect(() => {
    navigation.setOptions({ title: assetName ?? t('assistant') });
  }, [assetName]);

  const send = async () => {
    const text = input.trim();
    if (!text || sending) return;
    setMessages((previous) => [...previous, { role: 'user', content: text }]);
    setInput('');
    setSending(true);

    try {
      const agentUrl = await getAgentUrl();
      if (!agentUrl) {
        throw new Error(t('assistant_not_configured'));
      }
      const token = await AsyncStorage.getItem('accessToken');
      const response = await fetch(`${agentUrl}/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          message: text,
          session_id: sessionId ?? '',
          asset_id: assetId
        })
      });

      if (response.status === 402) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.detail ?? t('assistant_not_configured'));
      }
      if (!response.ok) {
        throw new Error(t('assistant_unreachable'));
      }

      const data = await response.json();
      if (data.session_id) setSessionId(data.session_id);
      const notices: string[] = data.notices ?? [];
      setMessages((previous) => [
        ...previous,
        {
          role: 'assistant',
          content:
            (notices.length ? `${notices.join('\n')}\n\n` : '') +
            (data.reply || t('no_response'))
        }
      ]);
    } catch (error: any) {
      setMessages((previous) => [
        ...previous,
        { role: 'assistant', content: `⚠️ ${error.message}` }
      ]);
    } finally {
      setSending(false);
      setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 80);
    }
  };

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: theme.colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={90}
    >
      <Chip icon="robot" style={styles.scopeChip}>
        {t('talking_about')}: {assetName}
      </Chip>

      <ScrollView
        ref={scrollRef}
        style={{ flex: 1 }}
        contentContainerStyle={{ padding: 12 }}
        onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: true })}
      >
        {messages.map((message, index) => (
          <Surface
            key={index}
            elevation={1}
            style={[
              styles.bubble,
              message.role === 'user' ? styles.user : styles.assistant,
              {
                backgroundColor:
                  message.role === 'user'
                    ? theme.colors.primaryContainer
                    : theme.colors.surfaceVariant
              }
            ]}
          >
            <Text>{message.content}</Text>
          </Surface>
        ))}
        {sending && (
          <View style={{ padding: 12 }}>
            <ActivityIndicator />
          </View>
        )}
      </ScrollView>

      <View style={styles.inputRow}>
        <TextInput
          style={{ flex: 1 }}
          mode="outlined"
          dense
          value={input}
          onChangeText={setInput}
          placeholder={t('ask_a_question')}
          onSubmitEditing={send}
          disabled={sending}
        />
        <IconButton icon="send" onPress={send} disabled={sending || !input.trim()} />
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  scopeChip: { margin: 10, alignSelf: 'flex-start' },
  bubble: { padding: 10, borderRadius: 12, marginBottom: 8, maxWidth: '88%' },
  user: { alignSelf: 'flex-end' },
  assistant: { alignSelf: 'flex-start' },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingBottom: 8
  }
});
