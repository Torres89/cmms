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
import Meter from '../../../../../models/owns/meter';
import { MeterPayload } from './useAssetMeters';

interface PropsType {
  open: boolean;
  onClose: () => void;
  meter: Meter | null;
  onSubmit: (payload: MeterPayload) => Promise<any>;
}

/**
 * A meter: a name, what it counts in, and how often someone is expected to
 * read it.
 *
 * The frequency is not paperwork — it is what makes the dossier able to say a
 * reading is overdue, which is the difference between a counter people trust
 * and one they quietly stop believing.
 */
const MeterFormDialog: FC<PropsType> = ({ open, onClose, meter, onSubmit }) => {
  const { t }: { t: any } = useTranslation();
  const [name, setName] = useState('');
  const [unit, setUnit] = useState('');
  const [updateFrequency, setUpdateFrequency] = useState('30');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setName(meter?.name ?? '');
    setUnit(meter?.unit ?? '');
    setUpdateFrequency(String(meter?.updateFrequency ?? 30));
    setError(null);
    setSaving(false);
  }, [open, meter]);

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setError(t('required_field'));
      return;
    }
    const frequency = Number(updateFrequency.trim() || 0);
    if (Number.isNaN(frequency)) {
      setError(t('value_must_be_a_number'));
      return;
    }
    setSaving(true);
    onSubmit({ name: trimmed, unit: unit.trim(), updateFrequency: frequency })
      .then(onClose)
      .catch(() => {})
      .finally(() => setSaving(false));
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>
        <Typography variant="h4">
          {meter ? t('edit_meter') : t('add_meter')}
        </Typography>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label={t('name')}
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setError(null);
            }}
            fullWidth
            autoFocus
            required
          />
          <TextField
            label={t('unit')}
            value={unit}
            onChange={(event) => setUnit(event.target.value)}
            placeholder="h"
            fullWidth
          />
          <TextField
            label={t('update_frequency_days')}
            value={updateFrequency}
            onChange={(event) => {
              setUpdateFrequency(event.target.value);
              setError(null);
            }}
            type="number"
            fullWidth
          />
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          {t('cancel')}
        </Button>
        <Button variant="contained" onClick={submit} disabled={saving}>
          {t('save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MeterFormDialog;
