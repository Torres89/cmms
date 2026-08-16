import { FC, useEffect, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import {
  ComponentAction,
  ComponentInstance,
  StructureNode
} from '../../../../../models/owns/dossier';

interface PropsType {
  open: boolean;
  onClose: () => void;
  position: StructureNode | null;
  /** Components with no current position — the only ones installable. */
  spares: ComponentInstance[];
  onInstallExisting: (
    componentId: number,
    action: ComponentAction
  ) => Promise<any>;
  onCreateAndInstall: (
    component: Partial<ComponentInstance>,
    action: ComponentAction
  ) => Promise<any>;
}

/** `datetime-local` gives a local string; the API wants an instant. */
const toInstant = (value: string): string | undefined =>
  value ? new Date(value).toISOString() : undefined;

const numberOrUndefined = (value: string): number | undefined =>
  value.trim() === '' ? undefined : Number(value.trim());

/**
 * Installing a component into a position.
 *
 * Two ways in, because both are real: on a running machine you fit a spare
 * that is already in stock, and on the day you commission a machine none of
 * its components exist in the system yet. A picker alone would be useless
 * exactly when the tab matters most.
 *
 * The date and meter reading are not decoration — they are what the ledger
 * records, and what makes "hours on the current spindle" answerable later.
 */
const InstallComponentDialog: FC<PropsType> = ({
  open,
  onClose,
  position,
  spares,
  onInstallExisting,
  onCreateAndInstall
}) => {
  const { t }: { t: any } = useTranslation();
  const [tab, setTab] = useState<'existing' | 'new'>('existing');
  const [selected, setSelected] = useState<ComponentInstance | null>(null);
  const [serialNumber, setSerialNumber] = useState('');
  const [manufacturer, setManufacturer] = useState('');
  const [mpn, setMpn] = useState('');
  const [hourLimit, setHourLimit] = useState('');
  const [cycleLimit, setCycleLimit] = useState('');
  const [calendarLimitMonths, setCalendarLimitMonths] = useState('');
  const [occurredAt, setOccurredAt] = useState('');
  const [meterValue, setMeterValue] = useState('');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    // Default to the spares list only when there is something in it.
    setTab(spares.length ? 'existing' : 'new');
    setSelected(null);
    setSerialNumber('');
    setManufacturer('');
    setMpn('');
    setHourLimit('');
    setCycleLimit('');
    setCalendarLimitMonths('');
    setOccurredAt('');
    setMeterValue('');
    setReason('');
    setError(null);
    setSaving(false);
  }, [open, spares.length]);

  const submit = () => {
    if (!position) return;
    const action: ComponentAction = {
      positionAssetId: position.id,
      occurredAt: toInstant(occurredAt),
      meterValue: numberOrUndefined(meterValue),
      reason: reason.trim() || undefined
    };

    let request: Promise<any>;
    if (tab === 'existing') {
      if (!selected) {
        setError(t('no_uninstalled_components'));
        return;
      }
      request = onInstallExisting(selected.id, action);
    } else {
      const serial = serialNumber.trim();
      if (!serial) {
        setError(t('serial_number_required'));
        return;
      }
      request = onCreateAndInstall(
        {
          serialNumber: serial,
          manufacturer: manufacturer.trim() || undefined,
          mpn: mpn.trim() || undefined,
          hourLimit: numberOrUndefined(hourLimit),
          cycleLimit: numberOrUndefined(cycleLimit),
          calendarLimitMonths: numberOrUndefined(calendarLimitMonths)
        },
        action
      );
    }

    setSaving(true);
    request
      .then(onClose)
      .catch(() => {})
      .finally(() => setSaving(false));
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        <Typography variant="h4">{t('install_component')}</Typography>
        {position && (
          <Typography variant="subtitle2">
            {position.positionCode
              ? `${position.positionCode} · ${position.name}`
              : position.name}
          </Typography>
        )}
      </DialogTitle>
      <DialogContent dividers>
        <Tabs
          value={tab}
          onChange={(_event, value) => {
            setTab(value);
            setError(null);
          }}
          sx={{ mb: 2 }}
        >
          <Tab value="existing" label={t('existing_component')} />
          <Tab value="new" label={t('new_component')} />
        </Tabs>

        <Stack spacing={2}>
          {tab === 'existing' ? (
            <>
              <Autocomplete<ComponentInstance, false, false, false>
                options={spares}
                value={selected}
                onChange={(_event, value) => {
                  setSelected(value);
                  setError(null);
                }}
                getOptionLabel={(option) =>
                  option.partType?.name
                    ? `${option.partType.name} — ${option.serialNumber}`
                    : option.serialNumber
                }
                isOptionEqualToValue={(a, b) => a.id === b.id}
                renderInput={(params) => (
                  <TextField {...params} label={t('serial_number')} />
                )}
              />
              {!spares.length && (
                <Alert severity="info">{t('no_uninstalled_components')}</Alert>
              )}
            </>
          ) : (
            <>
              <TextField
                label={t('serial_number')}
                value={serialNumber}
                onChange={(event) => {
                  setSerialNumber(event.target.value);
                  setError(null);
                }}
                fullWidth
                autoFocus
                required
              />
              <Stack direction="row" spacing={2}>
                <TextField
                  label={t('manufacturer')}
                  value={manufacturer}
                  onChange={(event) => setManufacturer(event.target.value)}
                  fullWidth
                />
                <TextField
                  label="MPN"
                  value={mpn}
                  onChange={(event) => setMpn(event.target.value)}
                  fullWidth
                />
              </Stack>
              <Stack direction="row" spacing={2}>
                <TextField
                  label={t('hour_limit')}
                  value={hourLimit}
                  onChange={(event) => setHourLimit(event.target.value)}
                  type="number"
                  fullWidth
                />
                <TextField
                  label={t('cycle_limit')}
                  value={cycleLimit}
                  onChange={(event) => setCycleLimit(event.target.value)}
                  type="number"
                  fullWidth
                />
                <TextField
                  label={t('calendar_limit_months')}
                  value={calendarLimitMonths}
                  onChange={(event) =>
                    setCalendarLimitMonths(event.target.value)
                  }
                  type="number"
                  fullWidth
                />
              </Stack>
            </>
          )}

          <Divider />

          <Stack direction="row" spacing={2}>
            <TextField
              label={t('occurred_at')}
              value={occurredAt}
              onChange={(event) => setOccurredAt(event.target.value)}
              type="datetime-local"
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
            <TextField
              label={t('meter_value')}
              value={meterValue}
              onChange={(event) => setMeterValue(event.target.value)}
              type="number"
              fullWidth
            />
          </Stack>
          <TextField
            label={t('reason')}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
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
          {t('install_component')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default InstallComponentDialog;
