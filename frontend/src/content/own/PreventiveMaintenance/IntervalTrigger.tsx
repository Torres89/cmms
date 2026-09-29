import {
  Box,
  Chip,
  Grid,
  LinearProgress,
  Stack,
  Typography,
  useTheme
} from '@mui/material';
import { useTranslation } from 'react-i18next';

export interface IntervalCounterStatus {
  intervalId: number;
  basis?: 'METER' | 'CALENDAR' | 'EVENT';
  label?: string;
  intervalValue?: number;
  intervalUnit?: string;
  unit?: string;
  elapsed?: number;
  percent?: number;
  remaining?: number;
  warnAtPercent?: number;
}

export interface IntervalStatus {
  preventiveMaintenanceId: number;
  triggerMode?: 'WHICHEVER_FIRST' | 'ALL_MUST_ELAPSE';
  percent?: number;
  due: boolean;
  warning: boolean;
  drivingCounter?: string;
  remaining?: number;
  remainingUnit?: string;
  counters: IntervalCounterStatus[];
}

const round = (value?: number) =>
  value === undefined || value === null
    ? '?'
    : Number.isInteger(value)
    ? String(value)
    : String(Math.round(value * 10) / 10);

/**
 * What an interval-driven PM is actually waiting for: "every 500 h or 3
 * months, whichever comes first", and how far along each counter is.
 *
 * Shown instead of the calendar schedule, which for these PMs is retired and
 * would otherwise read as "every 1 day".
 */
export default function IntervalTrigger({
  status
}: {
  status: IntervalStatus;
}) {
  const { t }: { t: any } = useTranslation();
  const theme = useTheme();

  const unitLabel = (unit?: string) =>
    t(`interval_unit_${(unit ?? 'days').toLowerCase()}`, {
      defaultValue: unit ?? t('interval_unit_days')
    });

  const describe = (counter: IntervalCounterStatus) =>
    counter.basis === 'EVENT'
      ? counter.label
      : `${round(counter.intervalValue)} ${unitLabel(counter.intervalUnit)}`;

  const joiner =
    status.triggerMode === 'ALL_MUST_ELAPSE'
      ? ` ${t('pm_interval_and')} `
      : ` ${t('pm_interval_or')} `;
  const summary = t('pm_interval_every', {
    intervals: status.counters.map(describe).join(joiner)
  });
  const modeSuffix =
    status.counters.length > 1
      ? `, ${t(
          status.triggerMode === 'ALL_MUST_ELAPSE'
            ? 'pm_interval_all_must_elapse'
            : 'pm_interval_whichever_first'
        )}`
      : '';

  return (
    <Grid item xs={12}>
      <Stack direction="row" spacing={1} alignItems="center">
        <Typography variant="h6">{`${summary}${modeSuffix}`}</Typography>
        {status.due ? (
          <Chip size="small" color="error" label={t('pm_interval_due')} />
        ) : status.warning ? (
          <Chip size="small" color="warning" label={t('pm_due_soon')} />
        ) : null}
      </Stack>
      <Stack spacing={1.5} sx={{ mt: 1.5 }}>
        {status.counters
          .filter((counter) => counter.basis !== 'EVENT')
          .map((counter) => {
            const percent = counter.percent ?? 0;
            const notStarted =
              counter.basis === 'METER' && !counter.elapsed && !percent;
            return (
              <Box key={counter.intervalId}>
                <Stack
                  direction="row"
                  justifyContent="space-between"
                  sx={{ mb: 0.5 }}
                >
                  <Typography
                    variant="caption"
                    sx={{ color: theme.colors.alpha.black[70] }}
                  >
                    {counter.label ?? describe(counter)}
                  </Typography>
                  <Typography variant="caption" sx={{ fontWeight: 600 }}>
                    {notStarted
                      ? t('pm_interval_not_started')
                      : t('pm_interval_progress', {
                          elapsed: round(counter.elapsed),
                          // Calendar progress comes back in days whatever
                          // the interval was written in; recover the total.
                          total:
                            counter.basis === 'CALENDAR'
                              ? round(
                                  percent > 0
                                    ? ((counter.elapsed ?? 0) * 100) / percent
                                    : (counter.elapsed ?? 0) +
                                        (counter.remaining ?? 0)
                                )
                              : round(counter.intervalValue),
                          unit: unitLabel(counter.unit)
                        })}
                  </Typography>
                </Stack>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(100, percent)}
                  color={
                    percent >= 100
                      ? 'error'
                      : percent >= (counter.warnAtPercent ?? 90)
                      ? 'warning'
                      : 'primary'
                  }
                  sx={{ height: 8, borderRadius: 4 }}
                />
              </Box>
            );
          })}
      </Stack>
      <Typography
        variant="caption"
        sx={{ display: 'block', mt: 1, color: theme.colors.alpha.black[70] }}
      >
        {t('pm_interval_generation_note')}
      </Typography>
    </Grid>
  );
}
