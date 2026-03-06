import {
  Box,
  Button,
  Card, CardContent, CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle, Divider,
  Grid,
  MenuItem,
  Select,
  Stack, TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import CustomDataGrid from '../../components/CustomDatagrid';
import {
  GridActionsCellItem,
  GridRowParams,
  GridToolbar,
  GridValueGetterParams
} from '@mui/x-data-grid';
import { GridEnrichedColDef } from '@mui/x-data-grid/models/colDef/gridColDef';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import { AssetDTO, AssetStatus } from '../../../../models/owns/asset';
import { useDispatch, useSelector } from '../../../../store';
import { editAsset } from '../../../../slices/asset';
import useAuth from '../../../../hooks/useAuth';
import { PermissionEntity } from '../../../../models/owns/role';
import {
  createAssetDowntime,
  deleteAssetDowntime,
  getAssetDowntimes
} from '../../../../slices/assetDowntime';
import { useContext, useEffect, useState } from 'react';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import Form from '../../components/form';
import * as Yup from 'yup';
import { IField } from '../../type';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import {
  getHMSString,
  getHoursAndMinutesAndSeconds
} from '../../../../utils/formatters';
import { CompanySettingsContext } from '../../../../contexts/CompanySettingsContext';
import AssetDowntime from '../../../../models/owns/assetDowntime';
import { getAssetDetailsOverview } from '../../../../slices/analytics/asset';
import DateTimePicker from '@mui/lab/DateTimePicker';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers';
import { Filter } from '../../Analytics/WorkOrder/WOModal';
import Loading from '../../Analytics/Loading';

interface PropsType {
  id: number;
}

const AssetDowntimes = ({ id }: PropsType) => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { getFormattedCurrency } = useContext(CompanySettingsContext);
  const { assetDetailsOverview, loading } = useSelector(state => state.assetAnalytics);
  const [end, setEnd] = useState(new Date());
  const nowMinusMonth = new Date();
  nowMinusMonth.setMonth(nowMinusMonth.getMonth() - 1);
  const [start, setStart] = useState(nowMinusMonth);

  useEffect(() => {
    if (id && start && end) dispatch(getAssetDetailsOverview(id, start, end));
  }, [id]);
  const datas: {
    label: string;
    value: string;
  }[] = [
    {
      label: t('MTBF'),
      value: assetDetailsOverview.mtbf.toString()
    },
    {
      label: t('MTTR'),
      value: assetDetailsOverview.mttr.toString()
    },
    {
      label: t('downtime_hours'),
      value: (assetDetailsOverview.downtime / 3600).toFixed(2)
    },
    {
      label: t('uptime_hours'),
      value: (assetDetailsOverview.uptime / 3600).toFixed(2)
    },
    {
      label: t('total_cost'),
      value: getFormattedCurrency(assetDetailsOverview.totalCost.toString())
    }
  ];
  return (
    <Box sx={{ px: 4 }}>
      <Grid container spacing={2}>
        <Grid item xs={12}>
          <Card sx={{ p: 2 }}>
            <Box sx={{ height: 550, width: '95%' }}>
              <Stack direction="row" justifyContent="space-between" py={3}>
                <LocalizationProvider dateAdapter={AdapterDayjs}>
                  <DatePicker
                    label={t('start')}
                    value={start}
                    onChange={(newValue) => setStart(newValue)}
                    renderInput={(params) => <TextField {...params} />}
                  />
                  <Box sx={{ mx: 2 }}> {t('to')} </Box>
                  <DatePicker
                    label={t('end')}
                    value={end}
                    onChange={(newValue) => setEnd(newValue)}
                    renderInput={(params) => <TextField {...params} />}
                  />
                </LocalizationProvider>
                <Button disabled={loading.assetDetailsOverview}
                        onClick={() => dispatch(getAssetDetailsOverview(id, start, end))}
                        startIcon={loading.assetDetailsOverview && <CircularProgress size="1rem" />}
                        variant={'contained'} color={'primary'}>{t('show')}</Button>
              </Stack>
              <Divider sx={{ mb: 2 }} />
              <Card>
                <CardContent>
                  <Stack sx={{ height: '100%', justifyContent: 'center', alignItems: 'center' }}>
                    {loading.assetDetailsOverview ? (
                      <Loading />
                    ) : (
                      <Stack direction="row" spacing={2}>
                        {datas.map((data) => (
                          <Stack key={data.label} alignItems="center">
                            <Typography
                              variant="h2"
                              fontWeight="bold"
                            >
                              {data.value}
                            </Typography>
                            <Typography>{data.label}</Typography>
                          </Stack>
                        ))}
                      </Stack>)}</Stack>
                </CardContent>
              </Card>
            </Box>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default AssetDowntimes;
