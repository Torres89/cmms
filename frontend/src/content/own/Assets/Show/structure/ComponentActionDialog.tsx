import { FC, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import {
  ComponentAction,
  ComponentSummary
} from '../../../../../models/owns/dossier';

export type ComponentActionKind = 'remove' | 'overhaul' | 'scrap';

interface PropsType {
  open: boolean;
  onClose: () => void;
  kind: ComponentActionKind;
  component: ComponentSummary | null;
  onSubmit: (componentId: number, action: ComponentAction) => Promise<any>;
}

const toInstant = (value: string): string | undefined =>
  value ? new Date(value).toISOString() : undefined;

const numberOrUndefined = (value: string): number | undefined =>
  value.trim() === '' ? undefined : Number(value.trim());

const TITLE: Record<ComponentActionKind, string> = {
  remove: 'remove_component',
  overhaul: 'overhaul',
  scrap: 'scrap'
};

/**
 * Removing, overhauling or scrapping a component.
 *
 * All three are ledger entries rather than edits, so all three ask the same
 * two questions first — when, and at what meter reading — and only then the
 * ones specific to the action. An overhaul additionally resets the
 * since-overhaul counters server-side, which is why it takes a cost and a
 * vendor rather than a meter value.
 */
const ComponentActionDialog: FC<PropsType> = ({
  open,
  onClose,
  kind,
  component,
  onSubmit
}) => {
  const { t }: { t: any } = useTranslation();
  const [occurredAt, setOccurredAt] = useState('');
  const [meterValue, setMeterValue] = useState('');
  const [cost, setCost] = useState('');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setOccurredAt('');
    setMeterValue('');
    setCost('');
    setReason('');
    setError(null);
    setSaving(false);
  }, [open, kind, component]);

  const submit = () => {
    if (!component) return;
    setSaving(true);
    onSubmit(component.id, {
      occurredAt: toInstant(occurredAt),
      meterValue:
        kind === 'overhaul' ? undefined : numberOrUndefined(meterValue),
      cost: kind === 'overhaul' ? numberOrUndefined(cost) : undefined,
      reason: reason.trim() || undefined
    })
      .then(onClose)
      .catch(() => setError(t('could_not_save_component')))
      .finally(() => setSaving(false));
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>
        <Typography variant="h4">{t(TITLE[kind])}</Typography>
        {component && (
          <Typography variant="subtitle2">
            {component.name
              ? `${component.name} — ${component.serialNumber}`
              : component.serialNumber}
          </Typography>
        )}
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label={t('occurred_at')}
            value={occurredAt}
            onChange={(event) => setOccurredAt(event.target.value)}
            type="datetime-local"
            InputLabelProps={{ shrink: true }}
            fullWidth
          />
          {kind === 'overhaul' ? (
            <TextField
              label={t('cost')}
              value={cost}
              onChange={(event) => setCost(event.target.value)}
              type="number"
              fullWidth
            />
          ) : (
            <TextField
              label={t('meter_value')}
              value={meterValue}
              onChange={(event) => setMeterValue(event.target.value)}
              type="number"
              fullWidth
            />
          )}
          <TextField
            label={t('reason')}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            fullWidth
            multiline
            rows={2}
          />
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          {t('cancel')}
        </Button>
        <Button
          variant="contained"
          color={kind === 'scrap' ? 'error' : 'primary'}
          onClick={submit}
          disabled={saving}
        >
          {t(TITLE[kind])}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ComponentActionDialog;
