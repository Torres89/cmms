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
import { NewSpecPayload } from './useAssetSpecs';

interface PropsType {
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: NewSpecPayload) => Promise<any>;
}

/**
 * Adding a spec: one form, five fields, no catalog.
 *
 * A value that reads as a number is stored as a number, so units and numeric
 * comparisons keep working without asking the user to declare a type.
 */
const SpecFormDialog: FC<PropsType> = ({ open, onClose, onSubmit }) => {
  const { t }: { t: any } = useTranslation();
  const [specKey, setSpecKey] = useState('');
  const [label, setLabel] = useState('');
  const [value, setValue] = useState('');
  const [unit, setUnit] = useState('');
  const [group, setGroup] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setSpecKey('');
    setLabel('');
    setValue('');
    setUnit('');
    setGroup('');
    setError(null);
    setSaving(false);
  }, [open]);

  const submit = () => {
    const key = specKey.trim();
    const name = label.trim();
    if (!key && !name) {
      setError(t('spec_name_required'));
      return;
    }
    const trimmed = value.trim();
    const asNumber = trimmed !== '' && !Number.isNaN(Number(trimmed));

    setSaving(true);
    onSubmit({
      // The key is the stable identifier; if the user only gave a name, derive
      // one from it so nothing depends on them inventing a slug.
      specKey: key || name.toLowerCase().replace(/[^a-z0-9]+/g, '_'),
      specGroup: group.trim() || t('general'),
      label: name || key,
      unit: unit.trim(),
      valueText: asNumber || trimmed === '' ? null : trimmed,
      valueNum: asNumber ? Number(trimmed) : null
    })
      .then(onClose)
      .catch(() => {})
      .finally(() => setSaving(false));
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        <Typography variant="h4">{t('add_spec')}</Typography>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label={t('label')}
            value={label}
            onChange={(e) => {
              setLabel(e.target.value);
              setError(null);
            }}
            placeholder={t('spec_label_placeholder')}
            fullWidth
            autoFocus
          />
          <Stack direction="row" spacing={2}>
            <TextField
              label={t('value')}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              fullWidth
            />
            <TextField
              label={t('unit')}
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
              sx={{ width: 120 }}
            />
          </Stack>
          <TextField
            label={t('spec_group')}
            value={group}
            onChange={(e) => setGroup(e.target.value)}
            placeholder={t('general')}
            fullWidth
          />
          <TextField
            label={t('spec_key')}
            value={specKey}
            onChange={(e) => setSpecKey(e.target.value)}
            helperText={t('spec_key_helper')}
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
          {t('add_spec')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SpecFormDialog;
