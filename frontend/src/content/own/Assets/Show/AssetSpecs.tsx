import { FC, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  LinearProgress,
  Stack,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import CheckCircleTwoToneIcon from '@mui/icons-material/CheckCircleTwoTone';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import { AssetSpec } from '../../../../models/owns/dossier';
import ConfirmDialog from '../../components/ConfirmDialog';
import useAssetSpecs from './specs/useAssetSpecs';
import SpecRow from './specs/SpecRow';
import SpecFormDialog from './specs/SpecFormDialog';

interface PropsType {
  assetId: number;
  canEdit?: boolean;
}

/**
 * The spec sheet, grouped, with provenance visible on every value.
 *
 * Anything a machine produced carries a "from Maintenance Manual p. 12 -
 * verify" chip until a person confirms it. That chip is the whole reason
 * extraction is safe to run: it never lets a guess be mistaken for a fact.
 *
 * The "still missing" chips are the intended way to fill a sheet in: click one
 * and you are adding exactly that key.
 */
const AssetSpecs: FC<PropsType> = ({ assetId, canEdit = false }) => {
  const { t }: { t: any } = useTranslation();
  const {
    specs,
    completeness,
    loading,
    create,
    update,
    remove,
    verify,
    unverify,
    verifyAll
  } = useAssetSpecs(assetId);

  const [verifying, setVerifying] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<AssetSpec | null>(null);

  const unverified = useMemo(
    () => specs.filter((spec) => spec.needsVerification),
    [specs]
  );

  const grouped = useMemo(() => {
    const groups = new Map<string, AssetSpec[]>();
    specs.forEach((spec) => {
      const group = spec.specGroup || t('general');
      groups.set(group, [...(groups.get(group) ?? []), spec]);
    });
    return Array.from(groups.entries());
  }, [specs, t]);

  const handleVerifyAll = () => {
    setVerifying(true);
    // Approve-all-then-correct, not confirm-each: a queue that costs two
    // minutes per value is a queue nobody works through.
    verifyAll(unverified.map((spec) => spec.id))
      .catch(() => {})
      .finally(() => setVerifying(false));
  };

  const confirmDelete = () => {
    if (!pendingDelete) return;
    remove(pendingDelete.id)
      .catch(() => {})
      .finally(() => setPendingDelete(null));
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Stack spacing={2}>
      {canEdit && (
        <Stack direction="row" justifyContent="flex-end">
          <Button
            variant="contained"
            startIcon={<AddTwoToneIcon />}
            onClick={() => setAddOpen(true)}
          >
            {t('add_spec')}
          </Button>
        </Stack>
      )}

      {completeness && completeness.expected > 0 && (
        <Card sx={{ p: 2 }}>
          <Stack
            direction="row"
            justifyContent="space-between"
            alignItems="center"
          >
            <Typography variant="h6">
              {completeness.captured} / {completeness.expected}{' '}
              {t('specs_captured')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {completeness.requiredCaptured}/{completeness.requiredExpected}{' '}
              {t('required_captured')}
            </Typography>
          </Stack>
          <LinearProgress
            variant="determinate"
            value={Math.min(100, completeness.percent)}
            color={completeness.complete ? 'success' : 'primary'}
            sx={{ height: 8, borderRadius: 4, mt: 1 }}
          />
          {completeness.missingKeys.length > 0 && (
            <Box sx={{ mt: 1.5 }}>
              <Typography variant="caption" color="text.secondary">
                {t('still_missing')}:
              </Typography>
              <Stack
                direction="row"
                spacing={0.5}
                flexWrap="wrap"
                sx={{ mt: 0.5 }}
              >
                {completeness.missingKeys.slice(0, 20).map((key) => (
                  <Chip
                    key={key.specKey}
                    size="small"
                    variant="outlined"
                    color={key.required ? 'warning' : 'default'}
                    label={key.label ?? key.specKey}
                    sx={{ height: 20, fontSize: 11, mb: 0.5 }}
                  />
                ))}
              </Stack>
            </Box>
          )}
        </Card>
      )}

      {unverified.length > 0 && (
        <Alert
          severity="warning"
          action={
            canEdit ? (
              <Button
                size="small"
                disabled={verifying}
                onClick={handleVerifyAll}
                startIcon={<CheckCircleTwoToneIcon />}
              >
                {t('verify_all')}
              </Button>
            ) : undefined
          }
        >
          {unverified.length} {t('values_extracted_awaiting_verification')}
        </Alert>
      )}

      {grouped.map(([group, groupSpecs]) => (
        <Card key={group} sx={{ p: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            {group}
          </Typography>
          <Divider sx={{ mb: 1.5 }} />
          <Grid container spacing={1.5}>
            {groupSpecs.map((spec) => (
              <Grid item xs={12} sm={6} md={4} key={spec.id}>
                <SpecRow
                  spec={spec}
                  canEdit={canEdit}
                  onSave={update}
                  onDelete={setPendingDelete}
                  onVerify={(target) => verify(target.id).catch(() => {})}
                  onUnverify={(target) => unverify(target.id).catch(() => {})}
                />
              </Grid>
            ))}
          </Grid>
        </Card>
      ))}

      {!specs.length && (
        <Alert severity="info">{t('no_specs_captured_yet')}</Alert>
      )}

      <SpecFormDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onSubmit={create}
      />

      <ConfirmDialog
        open={!!pendingDelete}
        onCancel={() => setPendingDelete(null)}
        onConfirm={confirmDelete}
        confirmText={t('to_delete')}
        question={t('confirm_delete_spec')}
      />
    </Stack>
  );
};

export default AssetSpecs;
