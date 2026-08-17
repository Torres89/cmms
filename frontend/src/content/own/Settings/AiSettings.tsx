import { FC, useContext, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Link,
  MenuItem,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import KeyTwoToneIcon from '@mui/icons-material/KeyTwoTone';
import api from '../../../utils/api';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { agentUrl } from '../../../config';

interface AiConfig {
  provider: string;
  model?: string;
  baseUrl?: string;
  monthlyTokenCap?: number;
  apiKeyMasked?: string;
  keyConfigured: boolean;
}

interface UsageSummary {
  available: boolean;
  days?: number;
  calls?: number;
  promptTokens?: number;
  completionTokens?: number;
  toolCalls?: number;
  monthToDateTokens?: number;
  byModel?: { model: string; calls: number; tokens: number }[];
}

const PROVIDERS = [
  { value: 'NONE', labelKey: 'ai_provider_none' },
  { value: 'ANTHROPIC', labelKey: 'ai_provider_anthropic' },
  { value: 'OPENAI', labelKey: 'ai_provider_openai' },
  { value: 'CUSTOM', labelKey: 'ai_provider_custom' },
  { value: 'MANAGED', labelKey: 'ai_provider_managed' }
];

/**
 * Three doors to a model, none of them ours.
 *
 * Door 1 is the customer's own MCP client and needs nothing configured here.
 * Door 2 is their own key. Door 3 is the managed add-on. This screen is where
 * a company picks, and it is deliberately explicit that the key never comes
 * back out once saved.
 */
const AiSettings: FC = () => {
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [config, setConfig] = useState<AiConfig | null>(null);
  const [usage, setUsage] = useState<UsageSummary | null>(null);
  const [apiKey, setApiKey] = useState('');
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    api
      .get<AiConfig>('ai-config')
      .then(setConfig)
      .catch(() => showSnackBar(t('could_not_load_ai_settings'), 'error'))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  useEffect(() => {
    // Usage lives with the agent, which is the service that meters it.
    fetch(`${agentUrl}/usage?days=30`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`
      }
    })
      .then((response) => (response.ok ? response.json() : null))
      .then(setUsage)
      .catch(() => setUsage(null));
  }, []);

  const save = () => {
    if (!config) return;
    setSaving(true);
    api
      .patch<AiConfig>('ai-config', {
        provider: config.provider,
        model: config.model,
        baseUrl: config.baseUrl,
        monthlyTokenCap: config.monthlyTokenCap,
        // Absent means "leave the stored key alone"; a blank string clears it.
        ...(apiKey ? { apiKey } : {})
      })
      .then((updated) => {
        setConfig(updated);
        setApiKey('');
        showSnackBar(t('ai_settings_saved'), 'success');
      })
      .catch(() => showSnackBar(t('could_not_save_ai_settings'), 'error'))
      .finally(() => setSaving(false));
  };

  const clearKey = () => {
    api
      .deletes('ai-config/key')
      .then(() => {
        showSnackBar(t('api_key_removed'), 'success');
        load();
      })
      .catch(() => showSnackBar(t('could_not_save_ai_settings'), 'error'));
  };

  if (loading || !config) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  const mcpUrl = `${agentUrl.replace(/\/+$/, '')}/mcp`;

  return (
    <Stack spacing={3}>
      <Card>
        <CardHeader
          title={t('connect_your_own_ai')}
          subheader={t('connect_your_own_ai_description')}
        />
        <Divider />
        <CardContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            {t('mcp_door_explanation')}
            <Box sx={{ mt: 1 }}>
              <Typography
                variant="body2"
                sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}
              >
                {mcpUrl}
              </Typography>
            </Box>
          </Alert>

          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <TextField
                select
                fullWidth
                label={t('ai_provider')}
                value={config.provider}
                onChange={(event) =>
                  setConfig({ ...config, provider: event.target.value })
                }
                helperText={t('ai_provider_helper')}
              >
                {PROVIDERS.map((provider) => (
                  <MenuItem key={provider.value} value={provider.value}>
                    {t(provider.labelKey)}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>

            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label={t('model')}
                value={config.model ?? ''}
                onChange={(event) => setConfig({ ...config, model: event.target.value })}
                placeholder="claude-sonnet-5"
                disabled={config.provider === 'NONE'}
              />
            </Grid>

            {config.provider !== 'NONE' && config.provider !== 'MANAGED' && (
              <>
                <Grid item xs={12} md={8}>
                  <TextField
                    fullWidth
                    type="password"
                    label={t('api_key')}
                    value={apiKey}
                    onChange={(event) => setApiKey(event.target.value)}
                    placeholder={
                      config.keyConfigured
                        ? `${config.apiKeyMasked} — ${t('leave_blank_to_keep')}`
                        : t('paste_your_api_key')
                    }
                    helperText={t('api_key_never_returned')}
                    InputProps={{
                      startAdornment: <KeyTwoToneIcon fontSize="small" sx={{ mr: 1 }} />
                    }}
                  />
                </Grid>
                <Grid item xs={12} md={4}>
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ height: '100%' }}>
                    {config.keyConfigured && (
                      <>
                        <Chip size="small" color="success" label={t('key_installed')} />
                        <Button size="small" color="error" onClick={clearKey}>
                          {t('remove')}
                        </Button>
                      </>
                    )}
                  </Stack>
                </Grid>
              </>
            )}

            {config.provider === 'CUSTOM' && (
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label={t('base_url')}
                  value={config.baseUrl ?? ''}
                  onChange={(event) => setConfig({ ...config, baseUrl: event.target.value })}
                  placeholder="https://your-endpoint/v1"
                />
              </Grid>
            )}

            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                type="number"
                label={t('monthly_token_cap')}
                value={config.monthlyTokenCap ?? ''}
                onChange={(event) =>
                  setConfig({
                    ...config,
                    monthlyTokenCap: event.target.value
                      ? Number(event.target.value)
                      : undefined
                  })
                }
                helperText={t('monthly_token_cap_helper')}
              />
            </Grid>
          </Grid>

          <Box sx={{ mt: 2 }}>
            <Button variant="contained" onClick={save} disabled={saving}>
              {t('save')}
            </Button>
          </Box>
        </CardContent>
      </Card>

      {usage?.available && (
        <Card>
          <CardHeader title={t('ai_usage')} subheader={`${t('last')} ${usage.days} ${t('days')}`} />
          <Divider />
          <CardContent>
            <Grid container spacing={2}>
              <Grid item xs={6} md={3}>
                <Typography variant="caption" color="text.secondary">
                  {t('conversations')}
                </Typography>
                <Typography variant="h4">{usage.calls?.toLocaleString()}</Typography>
              </Grid>
              <Grid item xs={6} md={3}>
                <Typography variant="caption" color="text.secondary">
                  {t('tokens_this_month')}
                </Typography>
                <Typography variant="h4">
                  {usage.monthToDateTokens?.toLocaleString()}
                </Typography>
              </Grid>
              <Grid item xs={6} md={3}>
                <Typography variant="caption" color="text.secondary">
                  {t('tool_calls')}
                </Typography>
                <Typography variant="h4">{usage.toolCalls?.toLocaleString()}</Typography>
              </Grid>
            </Grid>
            {usage.byModel?.length > 0 && (
              <Stack direction="row" spacing={1} sx={{ mt: 2 }} flexWrap="wrap">
                {usage.byModel.map((row) => (
                  <Chip
                    key={row.model}
                    size="small"
                    variant="outlined"
                    label={`${row.model}: ${row.tokens.toLocaleString()} ${t('tokens')}`}
                    sx={{ mb: 0.5 }}
                  />
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>
      )}
    </Stack>
  );
};

export default AiSettings;
