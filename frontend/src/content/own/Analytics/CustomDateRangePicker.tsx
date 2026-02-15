import { Box, Card, CardContent, TextField } from '@mui/material';
import { LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { useTranslation } from 'react-i18next';

interface OwnProps {
  start: Date;
  end: Date;
  setStart: (date: Date) => void;
  setEnd: (date: Date) => void;
}

export default function ({ start, end, setEnd, setStart }: OwnProps) {
  const { t }: { t: any } = useTranslation();

  return (
    <Card>
      <CardContent sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <LocalizationProvider
          dateAdapter={AdapterDayjs}
          localeText={{ start: t('start'), end: t('end') }}
        >
          <DatePicker
            label={t('start')}
            value={start}
            onChange={(newValue) => {
              setStart(newValue);
            }}
            renderInput={(params) => <TextField {...params} />}
          />
          <Box sx={{ mx: 2 }}> {t('to')} </Box>
          <DatePicker
            label={t('end')}
            value={end}
            onChange={(newValue) => {
              setEnd(newValue);
            }}
            renderInput={(params) => <TextField {...params} />}
          />
        </LocalizationProvider>
      </CardContent>
    </Card>
  );
}