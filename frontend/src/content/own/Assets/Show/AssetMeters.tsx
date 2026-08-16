import { FC, useContext, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
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
import DoneIcon from '@mui/icons-material/Done';
import CloseIcon from '@mui/icons-material/Close';
import { AssetDTO } from '../../../../models/owns/asset';
import Meter from '../../../../models/owns/meter';
import Reading from '../../../../models/owns/reading';
import { CompanySettingsContext } from '../../../../contexts/CompanySettingsContext';
import ConfirmDialog from '../../components/ConfirmDialog';
import useAssetMeters from './meters/useAssetMeters';
import MeterFormDialog from './meters/MeterFormDialog';

interface PropsType {
  asset: AssetDTO;
  canEdit?: boolean;
}

/**
 * The machine's meters and their readings.
 *
 * Readings are the input the rest of the dossier runs on — installed component
 * hour counters roll forward from them, and usage-based PM intervals are
 * measured against them. So this tab has to allow correcting a fat-fingered
 * reading, not only adding one: a wrong reading silently ages every component
 * on the machine.
 */
const AssetMeters: FC<PropsType> = ({ asset, canEdit = false }) => {
  const { t }: { t: any } = useTranslation();
  const { getFormattedDate, getUserNameById } = useContext(
    CompanySettingsContext
  );
  const {
    meters,
    readings,
    loading,
    saveMeter,
    deleteMeter,
    addReading,
    updateReading,
    deleteReading
  } = useAssetMeters(asset?.id);

  const [selectedId, setSelectedId] = useState<number | ''>('');
  const [meterDialog, setMeterDialog] = useState<{ meter: Meter | null } | null>(
    null
  );
  const [pendingMeterDelete, setPendingMeterDelete] = useState<Meter | null>(
    null
  );
  const [pendingReadingDelete, setPendingReadingDelete] =
    useState<Reading | null>(null);
  const [newValue, setNewValue] = useState('');
  const [editingReading, setEditingReading] = useState<number | null>(null);
  const [draftValue, setDraftValue] = useState('');

  useEffect(() => {
    if (meters.length && !meters.some((meter) => meter.id === selectedId)) {
      setSelectedId(meters[0].id);
    }
    if (!meters.length) setSelectedId('');
  }, [meters]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  const selected = meters.find((meter) => meter.id === selectedId);
  const rows = selected ? readings[selected.id] ?? [] : [];

  const submitReading = () => {
    const value = Number(newValue.trim());
    if (!newValue.trim() || Number.isNaN(value) || !selected) return;
    addReading(selected.id, value)
      .then(() => setNewValue(''))
      .catch(() => {});
  };

  const saveEdit = (reading: Reading) => {
    const value = Number(draftValue.trim());
    if (Number.isNaN(value) || !selected) return;
    updateReading(reading.id, selected.id, value)
      .then(() => setEditingReading(null))
      .catch(() => {});
  };

  return (
    <Stack spacing={2}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h6">{t('meters')}</Typography>
        {canEdit && (
          <Button
            variant="contained"
            startIcon={<AddTwoToneIcon />}
            onClick={() => setMeterDialog({ meter: null })}
          >
            {t('add_meter')}
          </Button>
        )}
      </Stack>

      {!meters.length ? (
        <Alert severity="info">{t('no_meters_yet')}</Alert>
      ) : (
        <Card sx={{ p: 2 }}>
          <Stack
            direction="row"
            spacing={1}
            alignItems="center"
            sx={{ mb: 1.5 }}
            flexWrap="wrap"
          >
            <TextField
              select
              size="small"
              label={t('select_meter')}
              value={selectedId}
              onChange={(event) => {
                setSelectedId(Number(event.target.value));
                setEditingReading(null);
              }}
              sx={{ minWidth: 240 }}
            >
              {meters.map((meter) => (
                <MenuItem key={meter.id} value={meter.id}>
                  {meter.name}
                  {meter.unit ? ` (${meter.unit})` : ''}
                </MenuItem>
              ))}
            </TextField>

            {selected?.updateFrequency > 0 && (
              <Chip
                size="small"
                variant="outlined"
                label={`${t('update_frequency')}: ${
                  selected.updateFrequency
                } ${t('days')}`}
                sx={{ height: 24 }}
              />
            )}

            <Box sx={{ flex: 1 }} />

            {canEdit && selected && (
              <>
                <Tooltip title={t('edit_meter')}>
                  <IconButton
                    size="small"
                    onClick={() => setMeterDialog({ meter: selected })}
                  >
                    <EditTwoToneIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title={t('to_delete')}>
                  <IconButton
                    size="small"
                    onClick={() => setPendingMeterDelete(selected)}
                  >
                    <DeleteTwoToneIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </>
            )}
          </Stack>

          {canEdit && selected && (
            <>
              <Stack direction="row" spacing={1} sx={{ mb: 1.5 }}>
                <TextField
                  size="small"
                  label={t('reading')}
                  value={newValue}
                  onChange={(event) => setNewValue(event.target.value)}
                  onKeyDown={(event) =>
                    event.key === 'Enter' && submitReading()
                  }
                  type="number"
                  InputProps={{
                    endAdornment: selected.unit ? (
                      <Typography variant="caption" color="text.secondary">
                        {selected.unit}
                      </Typography>
                    ) : null
                  }}
                />
                <Button
                  variant="outlined"
                  startIcon={<AddTwoToneIcon />}
                  onClick={submitReading}
                  disabled={!newValue.trim()}
                >
                  {t('add_reading')}
                </Button>
              </Stack>
              <Divider sx={{ mb: 1 }} />
            </>
          )}

          {!rows.length ? (
            <Alert severity="info">{t('nothing_to_show_yet')}</Alert>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t('reading')}</TableCell>
                  <TableCell>{t('date')}</TableCell>
                  <TableCell>{t('added_by')}</TableCell>
                  {canEdit && <TableCell align="right" />}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((reading) => (
                  <TableRow key={reading.id} hover>
                    <TableCell>
                      {editingReading === reading.id ? (
                        <TextField
                          size="small"
                          value={draftValue}
                          onChange={(event) =>
                            setDraftValue(event.target.value)
                          }
                          onKeyDown={(event) =>
                            event.key === 'Enter' && saveEdit(reading)
                          }
                          type="number"
                          autoFocus
                          sx={{ width: 140 }}
                        />
                      ) : (
                        `${reading.value} ${selected?.unit ?? ''}`
                      )}
                    </TableCell>
                    <TableCell>{getFormattedDate(reading.createdAt)}</TableCell>
                    <TableCell>{getUserNameById(reading.createdBy)}</TableCell>
                    {canEdit && (
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        {editingReading === reading.id ? (
                          <>
                            <IconButton
                              size="small"
                              onClick={() => setEditingReading(null)}
                            >
                              <CloseIcon sx={{ fontSize: 16 }} />
                            </IconButton>
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={() => saveEdit(reading)}
                            >
                              <DoneIcon sx={{ fontSize: 16 }} />
                            </IconButton>
                          </>
                        ) : (
                          <>
                            <Tooltip title={t('edit_reading')}>
                              <IconButton
                                size="small"
                                onClick={() => {
                                  setEditingReading(reading.id);
                                  setDraftValue(String(reading.value));
                                }}
                              >
                                <EditTwoToneIcon sx={{ fontSize: 16 }} />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title={t('to_delete')}>
                              <IconButton
                                size="small"
                                onClick={() => setPendingReadingDelete(reading)}
                              >
                                <DeleteTwoToneIcon sx={{ fontSize: 16 }} />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Card>
      )}

      <MeterFormDialog
        open={!!meterDialog}
        onClose={() => setMeterDialog(null)}
        meter={meterDialog?.meter ?? null}
        onSubmit={(payload) =>
          saveMeter(
            meterDialog?.meter?.id ?? null,
            payload,
            meterDialog?.meter ?? undefined
          )
        }
      />

      <ConfirmDialog
        open={!!pendingMeterDelete}
        onCancel={() => setPendingMeterDelete(null)}
        onConfirm={() => {
          const target = pendingMeterDelete;
          setPendingMeterDelete(null);
          if (target) deleteMeter(target.id).catch(() => {});
        }}
        confirmText={t('to_delete')}
        question={t('confirm_delete_meter')}
      />

      <ConfirmDialog
        open={!!pendingReadingDelete}
        onCancel={() => setPendingReadingDelete(null)}
        onConfirm={() => {
          const target = pendingReadingDelete;
          setPendingReadingDelete(null);
          if (target) deleteReading(target.id).catch(() => {});
        }}
        confirmText={t('to_delete')}
        question={t('confirm_delete_reading')}
      />
    </Stack>
  );
};

export default AssetMeters;
