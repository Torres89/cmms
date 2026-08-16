import { FC } from 'react';
import {
  alpha,
  Box,
  Button,
  Chip,
  Grid,
  LinearProgress,
  Stack,
  Tooltip,
  Typography,
  useTheme
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import SmartToyTwoToneIcon from '@mui/icons-material/SmartToyTwoTone';
import WarningAmberTwoToneIcon from '@mui/icons-material/WarningAmberTwoTone';
import { AssetDossier } from '../../../../models/owns/dossier';

interface PropsType {
  dossier: AssetDossier;
}

/**
 * The band at the top of the dossier: who this machine is, how healthy it is,
 * and how completely it has been documented.
 *
 * The completeness bar is here rather than buried in the Specs tab on purpose.
 * Commissioning is a billed service, so "27 of 34 captured" is the number the
 * customer should be looking at while it moves.
 */
const AssetDossierHeader: FC<PropsType> = ({ dossier }) => {
  const { t }: { t: any } = useTranslation();
  const theme = useTheme();

  const completeness = dossier.specCompleteness;
  const dueSoon = dossier.upcomingMaintenance.filter(
    (pm) => pm.due || pm.warning
  );
  const lowLife = dossier.components.filter(
    (component) =>
      component.remainingLifeFraction != null &&
      component.remainingLifeFraction <= 0.15
  );

  const askAboutThis = () => {
    // The chat widget listens for this and pins the conversation to the
    // machine, injecting a fresh dossier card on every turn.
    window.dispatchEvent(
      new CustomEvent('atlas:chat-scope', {
        detail: { assetId: dossier.id, assetName: dossier.name }
      })
    );
  };

  const identity = [
    dossier.manufacturer,
    dossier.serialNumber ? `SN ${dossier.serialNumber}` : null,
    dossier.locationPath,
    dossier.inServiceDate
      ? `${t('in_service')} ${new Date(dossier.inServiceDate).toLocaleDateString()}`
      : null,
    dossier.downtimeCostPerHour != null
      ? `${dossier.downtimeCostPerHour}/h ${t('downtime_cost_short')}`
      : null
  ]
    .filter(Boolean)
    .join(' · ');

  const primaryMeter = dossier.meters.find((meter) => meter.lastValue != null);
  const nextPm = dueSoon[0];

  return (
    <Box
      sx={{
        p: 2.5,
        mb: 2,
        borderRadius: 2,
        border: `1px solid ${theme.colors.alpha.black[10]}`,
        bgcolor: alpha(theme.colors.primary.main, 0.03)
      }}
    >
      <Grid container spacing={2} alignItems="flex-start">
        <Grid item xs={12} md={7}>
          <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap">
            <Typography variant="h4">{dossier.name}</Typography>
            {dossier.model && (
              <Typography variant="h5" color="text.secondary">
                {dossier.model}
              </Typography>
            )}
            {dossier.status && (
              <Chip
                size="small"
                label={t(dossier.status)}
                color={dossier.status === 'OPERATIONAL' ? 'success' : 'error'}
              />
            )}
            {dossier.criticality != null && (
              <Tooltip title={t('criticality')}>
                <Chip
                  size="small"
                  variant="outlined"
                  label={`${t('criticality')} ${dossier.criticality}/5`}
                  color={dossier.criticality >= 4 ? 'warning' : 'default'}
                />
              </Tooltip>
            )}
            {dossier.equipmentClass && (
              <Chip size="small" variant="outlined" label={dossier.equipmentClass} />
            )}
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
            {identity}
          </Typography>

          <Stack direction="row" spacing={3} sx={{ mt: 1.5 }} flexWrap="wrap">
            {primaryMeter && (
              <Typography variant="body2">
                <strong>
                  {primaryMeter.lastValue?.toLocaleString()} {primaryMeter.unit}
                </strong>{' '}
                {primaryMeter.name.toLowerCase()}
                {primaryMeter.overdue && (
                  <Chip
                    size="small"
                    color="warning"
                    variant="outlined"
                    sx={{ ml: 1, height: 18, fontSize: 10 }}
                    label={t('reading_overdue')}
                  />
                )}
              </Typography>
            )}
            {nextPm && (
              <Typography variant="body2">
                {t('next_pm')}:{' '}
                <strong>
                  {nextPm.remaining != null
                    ? `~${Math.round(nextPm.remaining)} ${nextPm.remainingUnit ?? ''}`
                    : nextPm.title}
                </strong>
              </Typography>
            )}
          </Stack>
        </Grid>

        <Grid item xs={12} md={5}>
          <Stack spacing={1.25}>
            <Button
              variant="contained"
              startIcon={<SmartToyTwoToneIcon />}
              onClick={askAboutThis}
              sx={{ alignSelf: { xs: 'stretch', md: 'flex-end' } }}
            >
              {t('ask_about_this_machine')}
            </Button>

            <Stack direction="row" spacing={1} flexWrap="wrap" justifyContent={{ md: 'flex-end' }}>
              <Chip
                size="small"
                variant="outlined"
                label={`${dossier.openWorkOrders.length} ${t('open_wos')}`}
              />
              <Chip
                size="small"
                variant="outlined"
                color={dueSoon.length ? 'warning' : 'default'}
                label={`${dueSoon.length} ${t('pm_due_soon')}`}
              />
              {lowLife.length > 0 && (
                <Chip
                  size="small"
                  color="error"
                  icon={<WarningAmberTwoToneIcon />}
                  label={`${lowLife.length} ${t('components_near_limit')}`}
                />
              )}
            </Stack>

            {completeness && completeness.expected > 0 && (
              <Box>
                <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                  <Typography variant="caption" color="text.secondary">
                    {t('profile_completeness')}
                  </Typography>
                  <Typography variant="caption" sx={{ fontWeight: 600 }}>
                    {completeness.captured}/{completeness.expected} {t('specs_captured')}
                  </Typography>
                </Stack>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(100, completeness.percent)}
                  color={completeness.complete ? 'success' : 'primary'}
                  sx={{ height: 8, borderRadius: 4 }}
                />
                {completeness.pendingVerification > 0 && (
                  <Typography variant="caption" color="warning.main">
                    {completeness.pendingVerification} {t('awaiting_verification')}
                  </Typography>
                )}
              </Box>
            )}
          </Stack>
        </Grid>
      </Grid>
    </Box>
  );
};

export default AssetDossierHeader;
