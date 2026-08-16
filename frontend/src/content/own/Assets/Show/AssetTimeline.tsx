import { FC, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
  useTheme
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import BuildTwoToneIcon from '@mui/icons-material/BuildTwoTone';
import ReportProblemTwoToneIcon from '@mui/icons-material/ReportProblemTwoTone';
import SwapHorizTwoToneIcon from '@mui/icons-material/SwapHorizTwoTone';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import ListAltTwoToneIcon from '@mui/icons-material/ListAltTwoTone';
import { useNavigate } from 'react-router-dom';
import { FailureEvent } from '../../../../models/owns/dossier';
import ConfirmDialog from '../../components/ConfirmDialog';
import useAssetHistory from './history/useAssetHistory';
import FailureEventFormDialog from './history/FailureEventFormDialog';
import FailureModesDialog from './history/FailureModesDialog';

interface PropsType {
  assetId: number;
  equipmentClass?: string;
  canEdit?: boolean;
}

type TimelineEntry = {
  id: string;
  kind: 'WORK_ORDER' | 'FAILURE' | 'COMPONENT';
  at?: string;
  title: string;
  detail?: string;
  chips: string[];
  link?: string;
  failure?: FailureEvent;
};

/**
 * One timeline for the machine: work orders, failures and component swaps
 * together, because "what happened to this machine" is one question.
 *
 * The Pareto beneath it answers the question that actually matters — which
 * failure modes are costing this machine its uptime — and it is only as good as
 * what gets written down, which is why recording a failure lives on this tab
 * rather than somewhere a person would have to go looking for it.
 */
const AssetTimeline: FC<PropsType> = ({
  assetId,
  equipmentClass,
  canEdit = false
}) => {
  const { t }: { t: any } = useTranslation();
  const theme = useTheme();
  const navigate = useNavigate();
  const {
    history,
    failures,
    componentEvents,
    modes,
    components,
    workOrders,
    loading,
    saveFailure,
    deleteFailure,
    saveMode,
    deleteMode
  } = useAssetHistory(assetId, equipmentClass);

  const [editing, setEditing] = useState<{ event: FailureEvent | null } | null>(
    null
  );
  const [modesOpen, setModesOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<FailureEvent | null>(null);

  const timeline: TimelineEntry[] = useMemo(() => {
    const entries: TimelineEntry[] = [];

    (history?.workOrders ?? []).forEach((workOrder) =>
      entries.push({
        id: `wo-${workOrder.id}`,
        kind: 'WORK_ORDER',
        at: workOrder.createdAt,
        title: workOrder.title ?? `#${workOrder.id}`,
        detail: workOrder.description,
        chips: [workOrder.status, workOrder.priority].filter(
          Boolean
        ) as string[],
        link: `/app/work-orders/${workOrder.id}`
      })
    );

    failures.forEach((failure) =>
      entries.push({
        id: `fe-${failure.id}`,
        kind: 'FAILURE',
        at: failure.occurredAt ?? failure.createdAt,
        title: failure.failureMode?.nameEn ?? failure.failureMode?.code ?? t('failure'),
        detail: [failure.cause, failure.correctiveAction]
          .filter(Boolean)
          .join(' → '),
        chips: [
          failure.failureMode?.code,
          failure.detectedAt ? t(failure.detectedAt) : null,
          failure.severity ? `S${failure.severity}` : null,
          failure.downtimeMinutes
            ? `${Math.round(failure.downtimeMinutes / 60)} h ${t('down')}`
            : null
        ].filter(Boolean) as string[],
        failure
      })
    );

    componentEvents.forEach((event) =>
      entries.push({
        id: `ce-${event.id}`,
        kind: 'COMPONENT',
        at: event.occurredAt,
        title: `${t(event.type)} — ${event.component?.serialNumber ?? ''}`,
        detail: event.reason,
        chips: [
          event.position?.name,
          event.positionMeterValue != null
            ? `${Math.round(event.positionMeterValue).toLocaleString()} h`
            : null
        ].filter(Boolean) as string[]
      })
    );

    return entries.sort((a, b) => {
      const aTime = a.at ? new Date(a.at).getTime() : 0;
      const bTime = b.at ? new Date(b.at).getTime() : 0;
      return bTime - aTime;
    });
  }, [history, failures, componentEvents, t]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  const icon = (kind: TimelineEntry['kind']) => {
    if (kind === 'FAILURE')
      return <ReportProblemTwoToneIcon color="error" fontSize="small" />;
    if (kind === 'COMPONENT')
      return <SwapHorizTwoToneIcon color="info" fontSize="small" />;
    return <BuildTwoToneIcon color="primary" fontSize="small" />;
  };

  return (
    <Stack spacing={2}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h6">{t('history')}</Typography>
        {canEdit && (
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              startIcon={<ListAltTwoToneIcon />}
              onClick={() => setModesOpen(true)}
            >
              {t('manage_failure_modes')}
            </Button>
            <Button
              variant="contained"
              startIcon={<AddTwoToneIcon />}
              onClick={() => setEditing({ event: null })}
            >
              {t('record_failure')}
            </Button>
          </Stack>
        )}
      </Stack>

      {history?.pareto?.length > 0 && (
        <Card sx={{ p: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            {t('what_costs_this_machine_uptime')}
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('failure_mode')}</TableCell>
                <TableCell align="right">{t('occurrences')}</TableCell>
                <TableCell align="right">{t('downtime')}</TableCell>
                <TableCell align="right">{t('mtbf')}</TableCell>
                <TableCell align="right">{t('mttr')}</TableCell>
                <TableCell align="right">{t('repair_cost')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {history.pareto.map((row) => (
                <TableRow key={row.code}>
                  <TableCell>
                    <Typography variant="body2">{row.name}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {row.code}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">{row.count}</TableCell>
                  <TableCell align="right">
                    {Math.round(row.downtimeMinutes / 60)} h
                  </TableCell>
                  <TableCell align="right">
                    {row.mtbfDays != null
                      ? `${Math.round(row.mtbfDays)} ${t('days')}`
                      : '—'}
                  </TableCell>
                  <TableCell align="right">
                    {row.mttrMinutes != null
                      ? `${Math.round(row.mttrMinutes)} min`
                      : '—'}
                  </TableCell>
                  <TableCell align="right">
                    {row.repairCost?.toFixed(2) ?? '—'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      <Card sx={{ p: 2 }}>
        <Divider sx={{ mb: 1 }} />
        {!timeline.length ? (
          <Alert severity="info">{t('nothing_recorded_yet')}</Alert>
        ) : (
          timeline.map((entry) => (
            <Stack
              key={entry.id}
              direction="row"
              spacing={1.5}
              alignItems="flex-start"
              sx={{
                py: 1,
                borderBottom: `1px solid ${theme.colors.alpha.black[5]}`,
                cursor: entry.link ? 'pointer' : 'default',
                '&:hover .timeline-actions': { opacity: 1 }
              }}
              onClick={() => entry.link && navigate(entry.link)}
            >
              <Box sx={{ pt: 0.25 }}>{icon(entry.kind)}</Box>
              <Box sx={{ flex: 1 }}>
                <Stack
                  direction="row"
                  spacing={1}
                  alignItems="center"
                  flexWrap="wrap"
                >
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {entry.title}
                  </Typography>
                  {entry.chips.map((chip) => (
                    <Chip
                      key={chip}
                      size="small"
                      variant="outlined"
                      label={chip}
                      sx={{ height: 18, fontSize: 10 }}
                    />
                  ))}
                </Stack>
                {entry.detail && (
                  <Typography variant="caption" color="text.secondary">
                    {entry.detail}
                  </Typography>
                )}
              </Box>

              {canEdit && entry.failure && (
                <Stack
                  direction="row"
                  className="timeline-actions"
                  sx={{ opacity: { xs: 1, md: 0 }, transition: 'opacity 150ms' }}
                >
                  <Tooltip title={t('edit_failure')}>
                    <IconButton
                      size="small"
                      onClick={(clickEvent) => {
                        clickEvent.stopPropagation();
                        setEditing({ event: entry.failure });
                      }}
                    >
                      <EditTwoToneIcon sx={{ fontSize: 15 }} />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title={t('to_delete')}>
                    <IconButton
                      size="small"
                      onClick={(clickEvent) => {
                        clickEvent.stopPropagation();
                        setPendingDelete(entry.failure);
                      }}
                    >
                      <DeleteTwoToneIcon sx={{ fontSize: 15 }} />
                    </IconButton>
                  </Tooltip>
                </Stack>
              )}

              <Typography variant="caption" color="text.secondary">
                {entry.at ? new Date(entry.at).toLocaleDateString() : ''}
              </Typography>
            </Stack>
          ))
        )}
      </Card>

      <FailureEventFormDialog
        open={!!editing}
        onClose={() => setEditing(null)}
        event={editing?.event ?? null}
        modes={modes}
        components={components}
        workOrders={workOrders}
        equipmentClass={equipmentClass}
        onManageModes={() => {
          setEditing(null);
          setModesOpen(true);
        }}
        onSubmit={(payload) =>
          saveFailure(editing?.event?.id ?? null, payload)
        }
      />

      <FailureModesDialog
        open={modesOpen}
        onClose={() => setModesOpen(false)}
        modes={modes}
        equipmentClass={equipmentClass}
        onSave={saveMode}
        onDelete={deleteMode}
      />

      <ConfirmDialog
        open={!!pendingDelete}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const target = pendingDelete;
          setPendingDelete(null);
          if (target) deleteFailure(target.id).catch(() => {});
        }}
        confirmText={t('to_delete')}
        question={t('confirm_delete_failure')}
      />
    </Stack>
  );
};

export default AssetTimeline;
