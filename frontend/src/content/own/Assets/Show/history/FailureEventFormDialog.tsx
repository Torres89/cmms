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
  Link,
  MenuItem,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import {
  ComponentInstance,
  detectionStages,
  FailureEvent,
  FailureMode
} from '../../../../../models/owns/dossier';
import { FailureEventPayload } from './useAssetHistory';

interface PropsType {
  open: boolean;
  onClose: () => void;
  event: FailureEvent | null;
  modes: FailureMode[];
  components: ComponentInstance[];
  workOrders: { id: number; title?: string }[];
  onSubmit: (payload: FailureEventPayload) => Promise<any>;
  onManageModes: () => void;
  equipmentClass?: string;
}

const toDateInput = (value?: string) =>
  value ? new Date(value).toISOString().slice(0, 10) : '';

const numberOrUndefined = (value: string): number | undefined =>
  value.trim() === '' ? undefined : Number(value.trim());

/**
 * Recording what broke.
 *
 * Only the failure mode is required. The rest is offered but never demanded:
 * this form is filled in with a machine down and someone waiting, and one that
 * insists on eleven fields is a form that gets abandoned. A failure recorded
 * with three fields is worth incomparably more than one not recorded at all.
 *
 * The date defaults to today but is editable, because the honest moment to
 * write this down is often the following morning — and MTBF is computed from
 * these dates, so guessing them wrong quietly corrupts the only number on the
 * tab that a planner acts on.
 */
const FailureEventFormDialog: FC<PropsType> = ({
  open,
  onClose,
  event,
  modes,
  components,
  workOrders,
  onSubmit,
  onManageModes,
  equipmentClass
}) => {
  const { t }: { t: any } = useTranslation();
  const [mode, setMode] = useState<FailureMode | null>(null);
  const [component, setComponent] = useState<ComponentInstance | null>(null);
  const [workOrderId, setWorkOrderId] = useState<string>('');
  const [occurredAt, setOccurredAt] = useState('');
  const [severity, setSeverity] = useState('');
  const [downtimeMinutes, setDowntimeMinutes] = useState('');
  const [repairCost, setRepairCost] = useState('');
  const [detectedAt, setDetectedAt] = useState('');
  const [detectionMethod, setDetectionMethod] = useState('');
  const [mechanism, setMechanism] = useState('');
  const [cause, setCause] = useState('');
  const [correctiveAction, setCorrectiveAction] = useState('');
  const [preventiveRecommendation, setPreventiveRecommendation] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setMode(
      event?.failureMode
        ? modes.find((m) => m.id === event.failureMode.id) ?? event.failureMode
        : null
    );
    setComponent(
      event?.component
        ? components.find((c) => c.id === event.component.id) ?? null
        : null
    );
    setWorkOrderId(event?.workOrder ? String(event.workOrder.id) : '');
    setOccurredAt(
      toDateInput(event?.occurredAt ?? event?.createdAt) ||
        new Date().toISOString().slice(0, 10)
    );
    setSeverity(
      event?.severity != null
        ? String(event.severity)
        : event
        ? ''
        : ''
    );
    setDowntimeMinutes(
      event?.downtimeMinutes != null ? String(event.downtimeMinutes) : ''
    );
    setRepairCost(event?.repairCost != null ? String(event.repairCost) : '');
    setDetectedAt(event?.detectedAt ?? '');
    setDetectionMethod(event?.detectionMethod ?? '');
    setMechanism(event?.mechanism ?? '');
    setCause(event?.cause ?? '');
    setCorrectiveAction(event?.correctiveAction ?? '');
    setPreventiveRecommendation(event?.preventiveRecommendation ?? '');
    setError(null);
    setSaving(false);
  }, [open, event, modes, components]);

  // Picking a mode pre-fills its catalogue defaults, but never overwrites
  // something already typed.
  const chooseMode = (value: FailureMode | null) => {
    setMode(value);
    setError(null);
    if (!value) return;
    if (!severity && value.severityDefault != null) {
      setSeverity(String(value.severityDefault));
    }
    if (!mechanism && value.typicalMechanism) setMechanism(value.typicalMechanism);
  };

  const submit = () => {
    if (!mode) {
      setError(t('failure_mode_required'));
      return;
    }
    setSaving(true);
    onSubmit({
      failureModeId: mode.id,
      componentId: component?.id,
      workOrderId: workOrderId ? Number(workOrderId) : undefined,
      occurredAt: occurredAt
        ? new Date(occurredAt).toISOString()
        : undefined,
      severity: numberOrUndefined(severity),
      downtimeMinutes: numberOrUndefined(downtimeMinutes),
      repairCost: numberOrUndefined(repairCost),
      detectedAt: detectedAt || undefined,
      detectionMethod: detectionMethod.trim() || undefined,
      mechanism: mechanism.trim() || undefined,
      cause: cause.trim() || undefined,
      correctiveAction: correctiveAction.trim() || undefined,
      preventiveRecommendation: preventiveRecommendation.trim() || undefined
    })
      .then(onClose)
      .catch(() => {})
      .finally(() => setSaving(false));
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        <Typography variant="h4">
          {event ? t('edit_failure') : t('record_failure')}
        </Typography>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {!equipmentClass ? (
            <Alert severity="warning">{t('set_equipment_class_first')}</Alert>
          ) : (
            !modes.length && (
              <Alert severity="info">{t('no_failure_modes_for_class')}</Alert>
            )
          )}

          <Autocomplete<FailureMode, false, false, false>
            options={modes}
            value={mode}
            onChange={(_event, value) => chooseMode(value)}
            getOptionLabel={(option) => `${option.code} — ${option.nameEn}`}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderInput={(params) => (
              <TextField {...params} label={t('failure_mode')} required />
            )}
          />
          <Link
            component="button"
            type="button"
            variant="body2"
            onClick={onManageModes}
            sx={{ alignSelf: 'flex-start' }}
          >
            {t('manage_failure_modes')}
          </Link>

          <Stack direction="row" spacing={2}>
            <TextField
              label={t('occurred_at')}
              value={occurredAt}
              onChange={(e) => setOccurredAt(e.target.value)}
              type="date"
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
            <TextField
              select
              label={t('severity')}
              value={severity}
              onChange={(e) => setSeverity(e.target.value)}
              sx={{ width: 140 }}
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
            <TextField
              label={t('downtime_minutes')}
              value={downtimeMinutes}
              onChange={(e) => setDowntimeMinutes(e.target.value)}
              type="number"
              fullWidth
            />
            <TextField
              label={t('repair_cost')}
              value={repairCost}
              onChange={(e) => setRepairCost(e.target.value)}
              type="number"
              fullWidth
            />
          </Stack>

          <Divider />

          <Stack direction="row" spacing={2}>
            <Autocomplete<ComponentInstance, false, false, false>
              options={components}
              value={component}
              onChange={(_event, value) => setComponent(value)}
              getOptionLabel={(option) =>
                option.partType?.name
                  ? `${option.partType.name} — ${option.serialNumber}`
                  : option.serialNumber
              }
              isOptionEqualToValue={(a, b) => a.id === b.id}
              renderInput={(params) => (
                <TextField {...params} label={t('component')} />
              )}
              sx={{ flex: 1 }}
            />
            <TextField
              select
              label={t('work_order')}
              value={workOrderId}
              onChange={(e) => setWorkOrderId(e.target.value)}
              sx={{ flex: 1 }}
            >
              <MenuItem value="">—</MenuItem>
              {workOrders.map((workOrder) => (
                <MenuItem key={workOrder.id} value={String(workOrder.id)}>
                  #{workOrder.id} {workOrder.title}
                </MenuItem>
              ))}
            </TextField>
          </Stack>

          <Stack direction="row" spacing={2}>
            <TextField
              select
              label={t('detected_at')}
              value={detectedAt}
              onChange={(e) => setDetectedAt(e.target.value)}
              fullWidth
            >
              <MenuItem value="">—</MenuItem>
              {detectionStages.map((stage) => (
                <MenuItem key={stage} value={stage}>
                  {t(stage)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label={t('detection_method')}
              value={detectionMethod}
              onChange={(e) => setDetectionMethod(e.target.value)}
              fullWidth
            />
          </Stack>

          <TextField
            label={t('cause')}
            value={cause}
            onChange={(e) => setCause(e.target.value)}
            fullWidth
            multiline
            rows={2}
          />
          <TextField
            label={t('mechanism')}
            value={mechanism}
            onChange={(e) => setMechanism(e.target.value)}
            fullWidth
            multiline
            rows={2}
          />
          <TextField
            label={t('corrective_action')}
            value={correctiveAction}
            onChange={(e) => setCorrectiveAction(e.target.value)}
            fullWidth
            multiline
            rows={2}
          />
          <TextField
            label={t('preventive_recommendation')}
            value={preventiveRecommendation}
            onChange={(e) => setPreventiveRecommendation(e.target.value)}
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
        <Button variant="contained" onClick={submit} disabled={saving}>
          {t('save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default FailureEventFormDialog;
