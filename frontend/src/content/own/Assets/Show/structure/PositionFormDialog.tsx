import { FC, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import {
  assetLevels,
  trackingClasses
} from '../../../../../models/owns/asset';
import { StructureNode } from '../../../../../models/owns/dossier';
import { PositionPayload } from './useAssetStructure';

interface PropsType {
  open: boolean;
  onClose: () => void;
  /** Set when editing; null when adding a child under `parentName`. */
  node: StructureNode | null;
  parentName?: string;
  onSubmit: (payload: PositionPayload) => Promise<any>;
}

/**
 * A position on the machine: a place, not a machine.
 *
 * Deliberately six fields. Location, company and equipment class come from the
 * parent, and a form that asked for them again would make adding a bearing
 * housing feel like commissioning a second lathe.
 */
const PositionFormDialog: FC<PropsType> = ({
  open,
  onClose,
  node,
  parentName,
  onSubmit
}) => {
  const { t }: { t: any } = useTranslation();
  const [name, setName] = useState('');
  const [positionCode, setPositionCode] = useState('');
  const [level, setLevel] = useState<string>('SUBUNIT');
  const [trackingClass, setTrackingClass] = useState<string>('NON_TRACKED');
  const [criticality, setCriticality] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setName(node?.name ?? '');
    setPositionCode(node?.positionCode ?? '');
    setLevel(node?.level ?? 'SUBUNIT');
    setTrackingClass(node?.trackingClass ?? 'NON_TRACKED');
    setCriticality(node?.criticality != null ? String(node.criticality) : '');
    setError(null);
    setSaving(false);
  }, [open, node]);

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setError(t('required_asset_name'));
      return;
    }
    const parsedCriticality = criticality.trim()
      ? Number(criticality.trim())
      : undefined;
    if (
      parsedCriticality != null &&
      (Number.isNaN(parsedCriticality) ||
        parsedCriticality < 1 ||
        parsedCriticality > 5)
    ) {
      setError(t('value_must_be_a_number'));
      return;
    }

    setSaving(true);
    onSubmit({
      name: trimmed,
      positionCode: positionCode.trim() || null,
      level,
      trackingClass,
      criticality: parsedCriticality
    })
      .then(onClose)
      // The hook already raised a snackbar; staying open keeps the input.
      .catch(() => {})
      .finally(() => setSaving(false));
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        <Typography variant="h4">
          {node ? t('edit_position') : t('add_position')}
        </Typography>
        {!node && parentName && (
          <Typography variant="subtitle2">{parentName}</Typography>
        )}
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
          <Stack direction="row" spacing={2}>
            <TextField
              label={t('position_code')}
              value={positionCode}
              onChange={(event) => setPositionCode(event.target.value)}
              placeholder="SPN"
              fullWidth
            />
            <TextField
              label={t('criticality')}
              value={criticality}
              onChange={(event) => {
                setCriticality(event.target.value);
                setError(null);
              }}
              type="number"
              inputProps={{ min: 1, max: 5 }}
              sx={{ width: 140 }}
            />
          </Stack>
          <Stack direction="row" spacing={2}>
            <TextField
              select
              label={t('asset_level')}
              value={level}
              onChange={(event) => setLevel(event.target.value)}
              fullWidth
            >
              {assetLevels.map((option) => (
                <MenuItem key={option} value={option}>
                  {t(option)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label={t('tracking_class')}
              value={trackingClass}
              onChange={(event) => setTrackingClass(event.target.value)}
              fullWidth
            >
              {trackingClasses.map((option) => (
                <MenuItem key={option} value={option}>
                  {t(option)}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
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

export default PositionFormDialog;
