import { FC, useContext, useEffect, useMemo, useState } from 'react';
import {
  Autocomplete,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import api from '../../../../utils/api';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';

interface FailureMode {
  id: number;
  code: string;
  nameEn: string;
  nameEs?: string;
  subunit?: string;
  typicalMechanism?: string;
  typicalCauses?: string;
  severityDefault?: number;
}

interface PropsType {
  open: boolean;
  workOrderId: number;
  assetId: number;
  onClose: () => void;
}

const DETECTION_STAGES = [
  'OPERATOR',
  'PM_INSPECTION',
  'CONDITION_MONITORING',
  'BREAKDOWN'
];

/**
 * "What actually broke?" — asked once, at work-order close, which is the only
 * moment anyone knows the answer.
 *
 * The candidate list arrives pre-filtered by the machine's equipment class and
 * ranked by what has already happened to *this* machine, so the right answer is
 * usually the first one. That ranking is the entire design: if closing a work
 * order costs fifteen extra seconds it gets filled in, and if it costs two
 * minutes it doesn't — and then there is no reliability data at all.
 */
const FailureCaptureDialog: FC<PropsType> = ({
  open,
  workOrderId,
  assetId,
  onClose
}) => {
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [candidates, setCandidates] = useState<FailureMode[]>([]);
  const [mode, setMode] = useState<FailureMode | null>(null);
  const [mechanism, setMechanism] = useState('');
  const [cause, setCause] = useState('');
  const [detectedAt, setDetectedAt] = useState('BREAKDOWN');
  const [downtimeMinutes, setDowntimeMinutes] = useState('');
  const [correctiveAction, setCorrectiveAction] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open || !assetId) return;
    api
      .get<FailureMode[]>(`assets/${assetId}/failure-modes`)
      .then(setCandidates)
      .catch(() => setCandidates([]));
  }, [open, assetId]);

  // Prefill from the catalogue so the common case is three taps and done.
  useEffect(() => {
    if (!mode) return;
    if (!mechanism) setMechanism(mode.typicalMechanism ?? '');
    if (!cause) setCause(mode.typicalCauses ?? '');
  }, [mode]);

  const reset = () => {
    setMode(null);
    setMechanism('');
    setCause('');
    setDetectedAt('BREAKDOWN');
    setDowntimeMinutes('');
    setCorrectiveAction('');
  };

  const save = () => {
    if (!mode) {
      onClose();
      return;
    }
    setSaving(true);
    api
      .post('failure-events', {
        workOrder: { id: workOrderId },
        asset: { id: assetId },
        failureMode: { id: mode.id },
        mechanism: mechanism || null,
        cause: cause || null,
        detectedAt,
        severity: mode.severityDefault ?? null,
        downtimeMinutes: downtimeMinutes ? Number(downtimeMinutes) : null,
        correctiveAction: correctiveAction || null
      })
      .then(() => {
        showSnackBar(t('failure_recorded'), 'success');
        reset();
        onClose();
      })
      .catch(() => showSnackBar(t('could_not_record_failure'), 'error'))
      .finally(() => setSaving(false));
  };

  const skip = () => {
    reset();
    onClose();
  };

  const options = useMemo(() => candidates, [candidates]);

  return (
    <Dialog open={open} onClose={skip} fullWidth maxWidth="sm">
      <DialogTitle>
        <Typography variant="h4">{t('what_failed')}</Typography>
        <Typography variant="subtitle2">
          {t('failure_capture_description')}
        </Typography>
      </DialogTitle>
      <DialogContent dividers>
        <Grid container spacing={2} sx={{ mt: 0 }}>
          <Grid item xs={12}>
            <Autocomplete<FailureMode, false, false, false>
              options={options}
              value={mode}
              onChange={(event, value) => setMode(value)}
              getOptionLabel={(option) => `${option.code} — ${option.nameEn}`}
              renderOption={(props, option) => (
                <Box component="li" {...props}>
                  <Stack>
                    <Typography variant="body2">{option.nameEn}</Typography>
                    <Stack direction="row" spacing={0.5}>
                      <Chip
                        size="small"
                        label={option.code}
                        sx={{ height: 18, fontSize: 10, fontFamily: 'monospace' }}
                      />
                      {option.subunit && (
                        <Chip
                          size="small"
                          variant="outlined"
                          label={option.subunit}
                          sx={{ height: 18, fontSize: 10 }}
                        />
                      )}
                    </Stack>
                  </Stack>
                </Box>
              )}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label={t('failure_mode')}
                  helperText={
                    options.length
                      ? t('ranked_by_this_machines_history')
                      : t('no_failure_modes_for_this_class')
                  }
                />
              )}
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              select
              fullWidth
              label={t('how_was_it_detected')}
              value={detectedAt}
              onChange={(event) => setDetectedAt(event.target.value)}
            >
              {DETECTION_STAGES.map((stage) => (
                <MenuItem key={stage} value={stage}>
                  {t(stage)}
                </MenuItem>
              ))}
            </TextField>
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              type="number"
              label={t('downtime_minutes')}
              value={downtimeMinutes}
              onChange={(event) => setDowntimeMinutes(event.target.value)}
            />
          </Grid>

          <Grid item xs={12}>
            <TextField
              fullWidth
              label={t('cause')}
              value={cause}
              onChange={(event) => setCause(event.target.value)}
              helperText={t('cause_helper')}
            />
          </Grid>

          <Grid item xs={12}>
            <TextField
              fullWidth
              multiline
              rows={2}
              label={t('corrective_action')}
              value={correctiveAction}
              onChange={(event) => setCorrectiveAction(event.target.value)}
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={skip} color="inherit">
          {t('skip')}
        </Button>
        <Button variant="contained" onClick={save} disabled={saving || !mode}>
          {t('record_failure')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default FailureCaptureDialog;
