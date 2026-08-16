import { FC, useContext, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Card,
  Chip,
  CircularProgress,
  Divider,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  useTheme
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import BuildTwoToneIcon from '@mui/icons-material/BuildTwoTone';
import ReportProblemTwoToneIcon from '@mui/icons-material/ReportProblemTwoTone';
import SwapHorizTwoToneIcon from '@mui/icons-material/SwapHorizTwoTone';
import { useNavigate } from 'react-router-dom';
import api from '../../../../utils/api';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';

interface PropsType {
  assetId: number;
}

interface HistoryResponse {
  workOrders: {
    id: number;
    title?: string;
    status?: string;
    priority?: string;
    createdAt?: string;
    completedOn?: string;
    description?: string;
  }[];
  failureEvents: {
    id: number;
    code?: string;
    name?: string;
    occurredAt?: string;
    cause?: string;
    detectedAt?: string;
    severity?: number;
    downtimeMinutes?: number;
    repairCost?: number;
    correctiveAction?: string;
  }[];
  pareto: {
    code: string;
    name: string;
    count: number;
    downtimeMinutes: number;
    repairCost: number;
    mtbfDays?: number;
    mttrMinutes?: number;
  }[];
}

type TimelineEntry = {
  id: string;
  kind: 'WORK_ORDER' | 'FAILURE' | 'COMPONENT';
  at?: string;
  title: string;
  detail?: string;
  chips: string[];
  link?: string;
};

/**
 * One timeline for the machine: work orders, failures and component swaps
 * together, because "what happened to this machine" is one question.
 *
 * The Pareto beneath it is the answer to the question that actually matters —
 * which failure modes are costing this machine its uptime.
 */
const AssetTimeline: FC<PropsType> = ({ assetId }) => {
  const { t }: { t: any } = useTranslation();
  const theme = useTheme();
  const navigate = useNavigate();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [history, setHistory] = useState<HistoryResponse | null>(null);
  const [componentEvents, setComponentEvents] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get<HistoryResponse>(`assets/${assetId}/maintenance-history?limit=100`),
      api
        .get<any[]>(`components/position/${assetId}/history`)
        .catch(() => [] as any[])
    ])
      .then(([loadedHistory, events]) => {
        setHistory(loadedHistory);
        setComponentEvents(events);
      })
      .catch(() => showSnackBar(t('could_not_load_history'), 'error'))
      .finally(() => setLoading(false));
  }, [assetId]);

  const timeline: TimelineEntry[] = useMemo(() => {
    if (!history) return [];
    const entries: TimelineEntry[] = [];

    history.workOrders.forEach((workOrder) =>
      entries.push({
        id: `wo-${workOrder.id}`,
        kind: 'WORK_ORDER',
        at: workOrder.createdAt,
        title: workOrder.title ?? `#${workOrder.id}`,
        detail: workOrder.description,
        chips: [workOrder.status, workOrder.priority].filter(Boolean) as string[],
        link: `/app/work-orders/${workOrder.id}`
      })
    );

    history.failureEvents.forEach((failure) =>
      entries.push({
        id: `fe-${failure.id}`,
        kind: 'FAILURE',
        at: failure.occurredAt,
        title: failure.name ?? failure.code ?? t('failure'),
        detail: [failure.cause, failure.correctiveAction].filter(Boolean).join(' → '),
        chips: [
          failure.code,
          failure.detectedAt,
          failure.downtimeMinutes
            ? `${Math.round(failure.downtimeMinutes / 60)} h ${t('down')}`
            : null
        ].filter(Boolean) as string[]
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
  }, [history, componentEvents, t]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  const icon = (kind: TimelineEntry['kind']) => {
    if (kind === 'FAILURE') return <ReportProblemTwoToneIcon color="error" fontSize="small" />;
    if (kind === 'COMPONENT') return <SwapHorizTwoToneIcon color="info" fontSize="small" />;
    return <BuildTwoToneIcon color="primary" fontSize="small" />;
  };

  return (
    <Stack spacing={2}>
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
                    {row.mtbfDays != null ? `${Math.round(row.mtbfDays)} ${t('days')}` : '—'}
                  </TableCell>
                  <TableCell align="right">
                    {row.mttrMinutes != null ? `${Math.round(row.mttrMinutes)} min` : '—'}
                  </TableCell>
                  <TableCell align="right">{row.repairCost?.toFixed(2) ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      <Card sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>
          {t('history')}
        </Typography>
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
                cursor: entry.link ? 'pointer' : 'default'
              }}
              onClick={() => entry.link && navigate(entry.link)}
            >
              <Box sx={{ pt: 0.25 }}>{icon(entry.kind)}</Box>
              <Box sx={{ flex: 1 }}>
                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
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
              <Typography variant="caption" color="text.secondary">
                {entry.at ? new Date(entry.at).toLocaleDateString() : ''}
              </Typography>
            </Stack>
          ))
        )}
      </Card>
    </Stack>
  );
};

export default AssetTimeline;
