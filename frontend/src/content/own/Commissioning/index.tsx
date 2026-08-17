import { FC, useContext, useEffect, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  LinearProgress,
  Link,
  MenuItem,
  Stack,
  Step,
  StepContent,
  StepLabel,
  Stepper,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { Helmet } from 'react-helmet-async';
import CheckCircleTwoToneIcon from '@mui/icons-material/CheckCircleTwoTone';
import UploadFileTwoToneIcon from '@mui/icons-material/UploadFileTwoTone';
import { useNavigate } from 'react-router-dom';
import api, { authHeader } from '../../../utils/api';
import { apiUrl } from '../../../config';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { TitleContext } from '../../../contexts/TitleContext';
import { AssetDossier, AssetSpec } from '../../../models/owns/dossier';

interface Pack {
  key: string;
  version: string;
  label: Record<string, string>;
  description?: string;
  ebs: any[];
  specKeys: any[];
  pmTemplates: any[];
  failureModes: any[];
  consumables: any[];
}

interface PackResult {
  packKey: string;
  dryRun: boolean;
  summary: string;
  totalCreated: number;
  positions: string[];
  specKeys: string[];
  meters: string[];
  preventiveMaintenances: string[];
  failureModes: string[];
  consumables: string[];
}

interface AssetOption {
  id: number;
  name: string;
}

const DOC_TYPES = [
  'MANUAL',
  'PARTS_CATALOG',
  'SCHEMATIC',
  'DRAWING',
  'CERTIFICATE',
  'INSPECTION_REPORT',
  'OIL_ANALYSIS'
];

/**
 * Commissioning: getting a machine fully documented in front of the customer.
 *
 * This is not an onboarding wizard for a nervous first-time user — there is no
 * self-serve signup. It is a power tool for whoever is on site billing by the
 * day, so it optimises for throughput: instantiate the pack first so 80 % of
 * the structure exists before anyone types, drop every document at once, then
 * approve the extracted values in a batch.
 *
 * Target: a machine documented in under 30 minutes, a shop in under two days.
 * That number is the commissioning margin.
 */
const Commissioning: FC = () => {
  const { t }: { t: any } = useTranslation();
  const navigate = useNavigate();
  const { setTitle } = useContext(TitleContext);
  const { showSnackBar } = useContext(CustomSnackBarContext);

  const [packs, setPacks] = useState<Pack[]>([]);
  const [assets, setAssets] = useState<AssetOption[]>([]);
  const [selectedAsset, setSelectedAsset] = useState<AssetOption | null>(null);
  const [selectedPack, setSelectedPack] = useState<Pack | null>(null);
  const [preview, setPreview] = useState<PackResult | null>(null);
  const [applied, setApplied] = useState<PackResult | null>(null);
  const [busy, setBusy] = useState(false);

  const [docType, setDocType] = useState('MANUAL');
  const [uploading, setUploading] = useState(false);
  const [queue, setQueue] = useState<Record<string, number> | null>(null);

  const [unverified, setUnverified] = useState<AssetSpec[]>([]);
  const [dossier, setDossier] = useState<AssetDossier | null>(null);

  useEffect(() => setTitle(t('commissioning')), [t]);

  useEffect(() => {
    api.get<Pack[]>('asset-templates').then(setPacks).catch(() => setPacks([]));
    api
      .post<{ content: AssetOption[] }>('assets/search', {
        filterFields: [],
        pageSize: 200,
        pageNum: 0,
        direction: 'ASC',
        sortField: 'name'
      })
      .then((page) => setAssets(page.content ?? []))
      .catch(() => setAssets([]));
  }, []);

  const refreshAsset = () => {
    if (!selectedAsset) return;
    api
      .get<AssetDossier>(`assets/${selectedAsset.id}/dossier`)
      .then(setDossier)
      .catch(() => setDossier(null));
    api
      .get<AssetSpec[]>('asset-specs/unverified')
      .then((specs) => setUnverified(specs))
      .catch(() => setUnverified([]));
    api
      .get<Record<string, number>>('documents/queue')
      .then(setQueue)
      .catch(() => setQueue(null));
  };

  useEffect(refreshAsset, [selectedAsset?.id]);

  const runPack = (dryRun: boolean) => {
    if (!selectedAsset || !selectedPack) return;
    setBusy(true);
    api
      .post<PackResult>(
        `asset-templates/${selectedPack.key}/instantiate?assetId=${selectedAsset.id}&dryRun=${dryRun}`,
        {}
      )
      .then((result) => {
        if (dryRun) {
          setPreview(result);
        } else {
          setApplied(result);
          setPreview(null);
          showSnackBar(result.summary, 'success');
          refreshAsset();
        }
      })
      .catch(() => showSnackBar(t('could_not_instantiate_pack'), 'error'))
      .finally(() => setBusy(false));
  };

  const uploadDocuments = (files: FileList | null) => {
    if (!files?.length || !selectedAsset) return;
    setUploading(true);
    const form = new FormData();
    Array.from(files).forEach((file) => form.append('files', file));
    form.append('folder', `company/assets/${selectedAsset.id}`);
    form.append('hidden', 'false');
    form.append('type', docType);
    form.append('assetId', String(selectedAsset.id));

    // FormData sets its own multipart boundary, so the JSON content-type from
    // authHeader has to come off.
    const headers = { ...(authHeader(false) as Record<string, string>) };
    delete headers['Content-Type'];

    fetch(`${apiUrl}files/upload`, { method: 'POST', body: form, headers })
      .then((response) => {
        if (!response.ok) throw new Error('upload failed');
        return response.json();
      })
      .then((uploaded: any[]) => {
        showSnackBar(
          `${uploaded.length} ${t('documents_queued_for_indexing')}`,
          'success'
        );
        refreshAsset();
      })
      .catch(() => showSnackBar(t('upload_failed'), 'error'))
      .finally(() => setUploading(false));
  };

  const approveAll = () => {
    const ids = unverified.map((spec) => spec.id);
    if (!ids.length) return;
    api
      .post('asset-specs/verify', ids)
      .then(() => {
        showSnackBar(t('specs_verified'), 'success');
        refreshAsset();
      })
      .catch(() => showSnackBar(t('could_not_verify_specs'), 'error'));
  };

  const completeness = dossier?.specCompleteness;
  const checklist = [
    {
      label: t('pack_instantiated'),
      done: Boolean(dossier?.equipmentClass && dossier?.structure?.length)
    },
    {
      label: t('documents_indexed'),
      done: Boolean(dossier?.documents?.some((doc) => doc.ingestStatus === 'READY'))
    },
    {
      label: t('required_specs_captured'),
      done: Boolean(completeness?.complete)
    },
    {
      label: t('serialized_components_recorded'),
      done: Boolean(dossier?.components?.length)
    },
    {
      label: t('maintenance_plans_active'),
      done: Boolean(dossier?.upcomingMaintenance?.length)
    },
    {
      label: t('meters_reading'),
      done: Boolean(dossier?.meters?.some((meter) => meter.lastValue != null))
    }
  ];
  const done = checklist.filter((item) => item.done).length;

  return (
    <>
      <Helmet>
        <title>{t('commissioning')}</title>
      </Helmet>
      <Box sx={{ p: 3 }}>
        <Typography variant="h3" sx={{ mb: 0.5 }}>
          {t('commissioning')}
        </Typography>
        <Typography variant="subtitle2" sx={{ mb: 3 }}>
          {t('commissioning_description')}
        </Typography>

        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Autocomplete<AssetOption, false, false, false>
              options={assets}
              value={selectedAsset}
              onChange={(event, value) => {
                setSelectedAsset(value);
                setPreview(null);
                setApplied(null);
              }}
              getOptionLabel={(option) => option.name}
              renderInput={(params) => (
                <TextField {...params} label={t('which_machine')} />
              )}
            />
          </CardContent>
        </Card>

        {!selectedAsset ? (
          <Alert severity="info">{t('pick_a_machine_to_start')}</Alert>
        ) : (
          <Grid container spacing={3}>
            <Grid item xs={12} md={8}>
              <Stepper orientation="vertical" nonLinear activeStep={-1}>
                {/* 1. Pack first: structure before data entry. */}
                <Step active expanded>
                  <StepLabel>
                    <Typography variant="h6">{t('step_instantiate_pack')}</Typography>
                  </StepLabel>
                  <StepContent>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                      {t('step_instantiate_pack_description')}
                    </Typography>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                      <TextField
                        select
                        size="small"
                        sx={{ minWidth: 280 }}
                        label={t('pack')}
                        value={selectedPack?.key ?? ''}
                        onChange={(event) =>
                          setSelectedPack(
                            packs.find((pack) => pack.key === event.target.value) ?? null
                          )
                        }
                      >
                        {packs.map((pack) => (
                          <MenuItem key={pack.key} value={pack.key}>
                            {pack.label?.en ?? pack.key} · v{pack.version}
                          </MenuItem>
                        ))}
                      </TextField>
                      <Button
                        variant="outlined"
                        disabled={!selectedPack || busy}
                        onClick={() => runPack(true)}
                      >
                        {t('preview')}
                      </Button>
                      <Button
                        variant="contained"
                        disabled={!selectedPack || busy}
                        onClick={() => runPack(false)}
                      >
                        {t('build_it_out')}
                      </Button>
                      {busy && <CircularProgress size={20} />}
                    </Stack>

                    {(preview || applied) && (
                      <Alert
                        severity={applied ? 'success' : 'info'}
                        sx={{ mt: 2 }}
                      >
                        <Typography variant="subtitle2">
                          {(applied ?? preview)?.summary}
                        </Typography>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap" sx={{ mt: 1 }}>
                          {(applied ?? preview)?.positions.slice(0, 12).map((position) => (
                            <Chip
                              key={position}
                              size="small"
                              variant="outlined"
                              label={position}
                              sx={{ height: 20, fontSize: 10, mb: 0.5 }}
                            />
                          ))}
                        </Stack>
                      </Alert>
                    )}
                  </StepContent>
                </Step>

                {/* 2. Every document at once, not one at a time. */}
                <Step active expanded>
                  <StepLabel>
                    <Typography variant="h6">{t('step_drop_documents')}</Typography>
                  </StepLabel>
                  <StepContent>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                      {t('step_drop_documents_description')}
                    </Typography>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                      <TextField
                        select
                        size="small"
                        sx={{ minWidth: 200 }}
                        label={t('type')}
                        value={docType}
                        onChange={(event) => setDocType(event.target.value)}
                      >
                        {DOC_TYPES.map((type) => (
                          <MenuItem key={type} value={type}>
                            {t(type)}
                          </MenuItem>
                        ))}
                      </TextField>
                      <Button
                        component="label"
                        variant="contained"
                        startIcon={<UploadFileTwoToneIcon />}
                        disabled={uploading}
                      >
                        {t('choose_files')}
                        <input
                          hidden
                          multiple
                          type="file"
                          onChange={(event) => uploadDocuments(event.target.files)}
                        />
                      </Button>
                      {uploading && <CircularProgress size={20} />}
                    </Stack>

                    {queue && (
                      <Stack direction="row" spacing={0.5} sx={{ mt: 1.5 }} flexWrap="wrap">
                        {Object.entries(queue)
                          .filter(([, count]) => count > 0)
                          .map(([status, count]) => (
                            <Chip
                              key={status}
                              size="small"
                              variant="outlined"
                              color={status === 'FAILED' ? 'error' : 'default'}
                              label={`${t(status)}: ${count}`}
                              sx={{ height: 20, fontSize: 11, mb: 0.5 }}
                            />
                          ))}
                      </Stack>
                    )}
                  </StepContent>
                </Step>

                {/* 3. Approve in a batch; correct the few that are wrong. */}
                <Step active expanded>
                  <StepLabel>
                    <Typography variant="h6">{t('step_review_extracted')}</Typography>
                  </StepLabel>
                  <StepContent>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                      {t('step_review_extracted_description')}
                    </Typography>
                    {!unverified.length ? (
                      <Alert severity="success">{t('nothing_awaiting_review')}</Alert>
                    ) : (
                      <>
                        <Stack direction="row" spacing={1} sx={{ mb: 1.5 }}>
                          <Button
                            variant="contained"
                            startIcon={<CheckCircleTwoToneIcon />}
                            onClick={approveAll}
                          >
                            {t('approve_all')} ({unverified.length})
                          </Button>
                          <Button
                            variant="outlined"
                            onClick={() =>
                              navigate(`/app/assets/${selectedAsset.id}/specs`)
                            }
                          >
                            {t('review_one_by_one')}
                          </Button>
                        </Stack>
                        {unverified.slice(0, 8).map((spec) => (
                          <Typography key={spec.id} variant="caption" display="block">
                            {spec.label ?? spec.specKey}:{' '}
                            <strong>{spec.valueText ?? spec.valueNum}</strong>
                            {spec.sourceDocument &&
                              ` — ${spec.sourceDocument.title}${
                                spec.sourcePage ? ` p. ${spec.sourcePage}` : ''
                              }`}
                          </Typography>
                        ))}
                      </>
                    )}
                  </StepContent>
                </Step>
              </Stepper>
            </Grid>

            {/* The handover document, filling in as the day goes. */}
            <Grid item xs={12} md={4}>
              <Card>
                <CardHeader
                  title={t('commissioning_checklist')}
                  subheader={`${done}/${checklist.length} ${t('complete')}`}
                />
                <Divider />
                <CardContent>
                  <LinearProgress
                    variant="determinate"
                    value={(100 * done) / checklist.length}
                    color={done === checklist.length ? 'success' : 'primary'}
                    sx={{ height: 8, borderRadius: 4, mb: 2 }}
                  />
                  {checklist.map((item) => (
                    <FormControlLabel
                      key={item.label}
                      control={<Checkbox checked={item.done} readOnly size="small" />}
                      label={
                        <Typography
                          variant="body2"
                          color={item.done ? 'text.primary' : 'text.secondary'}
                        >
                          {item.label}
                        </Typography>
                      }
                      sx={{ display: 'flex' }}
                    />
                  ))}

                  {completeness && completeness.expected > 0 && (
                    <Box sx={{ mt: 2 }}>
                      <Typography variant="caption" color="text.secondary">
                        {completeness.captured}/{completeness.expected}{' '}
                        {t('specs_captured')}
                      </Typography>
                      <LinearProgress
                        variant="determinate"
                        value={Math.min(100, completeness.percent)}
                        sx={{ height: 6, borderRadius: 3, mt: 0.5 }}
                      />
                    </Box>
                  )}

                  <Button
                    fullWidth
                    variant="outlined"
                    sx={{ mt: 2 }}
                    onClick={() => navigate(`/app/assets/${selectedAsset.id}/details`)}
                  >
                    {t('open_the_dossier')}
                  </Button>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        )}
      </Box>
    </>
  );
};

export default Commissioning;
