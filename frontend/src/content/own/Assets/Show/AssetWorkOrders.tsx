import { useContext, useEffect, useMemo } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  Stack,
  Typography,
  useTheme
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import { useNavigate } from 'react-router-dom';
import { AssetDTO } from '../../../../models/owns/asset';
import { useDispatch, useSelector } from '../../../../store';
import { getAssetWorkOrders } from '../../../../slices/asset';
import { CompanySettingsContext } from '../../../../contexts/CompanySettingsContext';
import Loading from '../../Analytics/Loading';

interface PropsType {
  asset: AssetDTO;
  canEdit?: boolean;
}

/**
 * Every work order raised against this machine.
 *
 * A view, not an editor — work orders are edited in the work-order module,
 * which is where their tasks, labour, parts and signatures live. What this tab
 * owes the reader is the split between what is still open and what is done,
 * because those are two different questions: "what is being worked on right
 * now" and "what has this machine cost us".
 */
const AssetWorkOrders = ({ asset, canEdit = false }: PropsType) => {
  const { t }: { t: any } = useTranslation();
  const theme = useTheme();
  const { getFormattedDate } = useContext(CompanySettingsContext);
  const { assetInfos, loadingGet } = useSelector((state) => state.assets);
  const workOrders = assetInfos[asset?.id]?.workOrders;
  const dispatch = useDispatch();
  const navigate = useNavigate();

  useEffect(() => {
    if (asset) dispatch(getAssetWorkOrders(asset.id));
  }, [asset]);

  const [open, complete] = useMemo(() => {
    const all = workOrders ?? [];
    return [
      all.filter((workOrder) => workOrder.status !== 'COMPLETE'),
      all.filter((workOrder) => workOrder.status === 'COMPLETE')
    ];
  }, [workOrders]);

  if (loadingGet)
    return (
      <Box
        sx={{
          height: '50vh',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center'
        }}
      >
        <Loading />
      </Box>
    );

  const overdue = (workOrder) =>
    workOrder.dueDate &&
    workOrder.status !== 'COMPLETE' &&
    new Date(workOrder.dueDate) < new Date();

  const renderRow = (workOrder) => (
    <Card
      key={workOrder.id}
      sx={{
        p: 2,
        mb: 1,
        cursor: 'pointer',
        borderLeft: `3px solid ${
          overdue(workOrder)
            ? theme.palette.error.main
            : workOrder.status === 'COMPLETE'
            ? theme.palette.success.main
            : theme.palette.primary.main
        }`
      }}
      onClick={() => navigate(`/app/work-orders/${workOrder.id}`)}
    >
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        justifyContent="space-between"
        alignItems={{ xs: 'flex-start', md: 'center' }}
        spacing={1}
      >
        <Box sx={{ flex: 1 }}>
          <Typography variant="h5">{workOrder.title}</Typography>
          <Typography variant="subtitle2" color="text.secondary">
            #{workOrder.id}
            {workOrder.primaryUser
              ? ` · ${workOrder.primaryUser.firstName} ${workOrder.primaryUser.lastName}`
              : ` · ${t('no_primary_worker')}`}
          </Typography>
        </Box>

        <Stack direction="row" spacing={1} alignItems="center">
          {workOrder.priority && workOrder.priority !== 'NONE' && (
            <Chip
              size="small"
              variant="outlined"
              label={t(workOrder.priority)}
              sx={{ height: 22 }}
            />
          )}
          <Chip size="small" label={t(workOrder.status)} sx={{ height: 22 }} />
          <Typography
            variant="body2"
            color={overdue(workOrder) ? 'error.main' : 'text.secondary'}
            sx={{ minWidth: 140, textAlign: 'right' }}
          >
            {workOrder.dueDate
              ? t('due_at_date', { date: getFormattedDate(workOrder.dueDate) })
              : t('no_due_date')}
          </Typography>
        </Stack>
      </Stack>
    </Card>
  );

  return (
    <Stack spacing={2}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h6">{t('work_orders')}</Typography>
        {canEdit && (
          <Button
            variant="contained"
            startIcon={<AddTwoToneIcon />}
            // The work-order module already opens its create form prefilled
            // when given an asset, so this is a way in rather than a second
            // implementation of the same form.
            onClick={() => navigate(`/app/work-orders?asset=${asset.id}`)}
          >
            {t('new_work_order')}
          </Button>
        )}
      </Stack>

      <Box>
        <Typography variant="subtitle1" sx={{ mb: 1 }}>
          {t('open_work_orders')} ({open.length})
        </Typography>
        {open.length ? (
          open.map(renderRow)
        ) : (
          <Alert severity="success">{t('no_wo_linked_asset')}</Alert>
        )}
      </Box>

      <Box>
        <Typography variant="subtitle1" sx={{ mb: 1 }}>
          {t('completed_work_orders')} ({complete.length})
        </Typography>
        {complete.length ? (
          complete.map(renderRow)
        ) : (
          <Alert severity="info">{t('no_completed_work_orders')}</Alert>
        )}
      </Box>
    </Stack>
  );
};

export default AssetWorkOrders;
