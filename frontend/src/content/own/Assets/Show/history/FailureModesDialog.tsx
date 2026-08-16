import { FC, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import { FailureMode } from '../../../../../models/owns/dossier';

interface PropsType {
  open: boolean;
  onClose: () => void;
  modes: FailureMode[];
  equipmentClass?: string;
  onSave: (id: number | null, mode: Partial<FailureMode>) => Promise<any>;
  onDelete: (id: number) => Promise<any>;
}

const EMPTY: Partial<FailureMode> = {
  code: '',
  nameEn: '',
  subunit: '',
  typicalMechanism: '',
  typicalCauses: '',
  detectionMethods: ''
};

/**
 * The catalogue of ways this class of machine breaks.
 *
 * It lives here rather than in settings because the moment a person discovers
 * their machine has an uncatalogued failure mode is the moment they are trying
 * to record it. Without a way through, the event form's dropdown is a dead end
 * and the failure goes unrecorded.
 */
const FailureModesDialog: FC<PropsType> = ({
  open,
  onClose,
  modes,
  equipmentClass,
  onSave,
  onDelete
}) => {
  const { t }: { t: any } = useTranslation();
  const [editing, setEditing] = useState<Partial<FailureMode> | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setEditing(null);
    setError(null);
    setSaving(false);
  }, [open]);

  const submit = () => {
    if (!editing) return;
    const code = (editing.code ?? '').trim();
    const nameEn = (editing.nameEn ?? '').trim();
    if (!code || !nameEn) {
      setError(t('failure_mode_required'));
      return;
    }
    setSaving(true);
    onSave(editing.id ?? null, { ...editing, code, nameEn })
      .then(() => setEditing(null))
      .catch(() => {})
      .finally(() => setSaving(false));
  };

  const field = (key: keyof FailureMode, label: string, rows?: number) => (
    <TextField
      label={label}
      value={(editing?.[key] as string) ?? ''}
      onChange={(event) => {
        setEditing({ ...editing, [key]: event.target.value });
        setError(null);
      }}
      fullWidth
      multiline={!!rows}
      rows={rows}
    />
  );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>
        <Typography variant="h4">{t('manage_failure_modes')}</Typography>
        {equipmentClass && (
          <Typography variant="subtitle2">{equipmentClass}</Typography>
        )}
      </DialogTitle>
      <DialogContent dividers>
        {!editing ? (
          <>
            <Stack direction="row" justifyContent="flex-end" sx={{ mb: 1 }}>
              <Button
                startIcon={<AddTwoToneIcon />}
                onClick={() => setEditing({ ...EMPTY })}
                disabled={!equipmentClass}
              >
                {t('add_failure_mode')}
              </Button>
            </Stack>
            {!equipmentClass && (
              <Alert severity="warning" sx={{ mb: 1 }}>
                {t('set_equipment_class_first')}
              </Alert>
            )}
            {!modes.length ? (
              <Alert severity="info">{t('no_failure_modes_for_class')}</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('code')}</TableCell>
                    <TableCell>{t('name')}</TableCell>
                    <TableCell>{t('subunit')}</TableCell>
                    <TableCell align="right">{t('default_severity')}</TableCell>
                    <TableCell align="right" />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {modes.map((mode) => (
                    <TableRow key={mode.id} hover>
                      <TableCell sx={{ fontFamily: 'monospace' }}>
                        {mode.code}
                      </TableCell>
                      <TableCell>{mode.nameEn}</TableCell>
                      <TableCell>{mode.subunit ?? '—'}</TableCell>
                      <TableCell align="right">
                        {mode.severityDefault ?? '—'}
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        <Tooltip title={t('edit')}>
                          <IconButton
                            size="small"
                            onClick={() => setEditing({ ...mode })}
                          >
                            <EditTwoToneIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={t('to_delete')}>
                          <IconButton
                            size="small"
                            onClick={() => onDelete(mode.id).catch(() => {})}
                          >
                            <DeleteTwoToneIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </>
        ) : (
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Stack direction="row" spacing={2}>
              {field('code', t('code'))}
              {field('subunit', t('subunit'))}
              <TextField
                select
                label={t('default_severity')}
                value={
                  editing.severityDefault != null
                    ? String(editing.severityDefault)
                    : ''
                }
                onChange={(event) =>
                  setEditing({
                    ...editing,
                    severityDefault: event.target.value
                      ? Number(event.target.value)
                      : undefined
                  })
                }
                sx={{ width: 160 }}
              >
                <MenuItem value="">—</MenuItem>
                {[1, 2, 3, 4, 5].map((level) => (
                  <MenuItem key={level} value={String(level)}>
                    {level}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
            <Stack direction="row" spacing={2}>
              {field('nameEn', t('name_en'))}
              {field('nameEs', t('name_es'))}
            </Stack>
            {field('typicalMechanism', t('typical_mechanism'), 2)}
            {field('typicalCauses', t('typical_causes'), 2)}
            {field('detectionMethods', t('detection_methods'), 2)}
            {error && <Alert severity="error">{error}</Alert>}
            <Divider />
            <Stack direction="row" justifyContent="flex-end" spacing={1}>
              <Button onClick={() => setEditing(null)} disabled={saving}>
                {t('cancel')}
              </Button>
              <Button variant="contained" onClick={submit} disabled={saving}>
                {t('save')}
              </Button>
            </Stack>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('close')}</Button>
      </DialogActions>
    </Dialog>
  );
};

export default FailureModesDialog;
