import { useContext, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import { Helmet } from 'react-helmet-async';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';
import api, { getErrorMessage } from '../../../utils/api';
import useAuth from '../../../hooks/useAuth';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { TitleContext } from '../../../contexts/TitleContext';

interface CustomerCompany {
  id: number;
  name: string;
  createdAt: string;
  adminEmail?: string;
  adminName?: string;
  usersCount: number;
  demo: boolean;
}

interface NewCustomer {
  companyName: string;
  employeesCount: number;
  language: string;
  adminFirstName: string;
  adminLastName: string;
  adminEmail: string;
  adminPhone: string;
  adminPassword: string;
}

const EMPTY: NewCustomer = {
  companyName: '',
  employeesCount: 5,
  language: 'ES',
  adminFirstName: '',
  adminLastName: '',
  adminEmail: '',
  adminPhone: '',
  adminPassword: ''
};

const LANGUAGES = ['ES', 'EN'];

/**
 * Customer onboarding for operators: the people named in OPERATOR_EMAILS.
 *
 * There is no self-serve signup - a customer is commissioned in person, so the
 * operator creates the company and its first administrator here, hands over
 * the temporary password, and the customer is in.
 */
function CustomerCompanies() {
  const { t }: { t: any } = useTranslation();
  const { user } = useAuth();
  const { setTitle } = useContext(TitleContext);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [companies, setCompanies] = useState<CustomerCompany[]>([]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<NewCustomer>(EMPTY);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState<{
    company: CustomerCompany;
    password: string;
  } | null>(null);

  useEffect(() => setTitle(t('customer_companies')), [t]);

  const load = () =>
    api
      .get<CustomerCompany[]>('operator/companies')
      .then(setCompanies)
      .catch((error) =>
        showSnackBar(
          getErrorMessage(error, t('could_not_load_customers')),
          'error'
        )
      );

  useEffect(() => {
    if (user.operator) load();
  }, [user.operator]);

  if (!user.operator) return <Navigate to="/app/work-orders" replace />;

  const set = (field: keyof NewCustomer) => (event) =>
    setForm({ ...form, [field]: event.target.value });

  const valid =
    form.companyName.trim() &&
    form.adminFirstName.trim() &&
    form.adminLastName.trim() &&
    /^\S+@\S+\.\S+$/.test(form.adminEmail.trim()) &&
    form.adminPassword.length >= 8;

  const submit = () => {
    setSubmitting(true);
    api
      .post<CustomerCompany>('operator/companies', {
        ...form,
        employeesCount: Number(form.employeesCount) || 0
      })
      .then((company) => {
        setCreated({ company, password: form.adminPassword });
        setOpen(false);
        setForm(EMPTY);
        load();
      })
      .catch((error) =>
        showSnackBar(
          getErrorMessage(error, t('could_not_create_customer')),
          'error'
        )
      )
      .finally(() => setSubmitting(false));
  };

  return (
    <>
      <Helmet>
        <title>{t('customer_companies')}</title>
      </Helmet>
      <Box sx={{ p: 3 }}>
        <Stack
          direction="row"
          justifyContent="space-between"
          alignItems="center"
          sx={{ mb: 2 }}
        >
          <Box>
            <Typography variant="h3">{t('customer_companies')}</Typography>
            <Typography variant="subtitle2">
              {t('customer_companies_description')}
            </Typography>
          </Box>
          <Button
            variant="contained"
            startIcon={<AddTwoToneIcon />}
            onClick={() => setOpen(true)}
          >
            {t('new_customer')}
          </Button>
        </Stack>

        {created && (
          <Alert
            severity="success"
            sx={{ mb: 2 }}
            onClose={() => setCreated(null)}
          >
            <Typography fontWeight="bold">
              {t('customer_created', { name: created.company.name })}
            </Typography>
            <Typography>
              {t('customer_created_handover', {
                email: created.company.adminEmail,
                password: created.password
              })}
            </Typography>
          </Alert>
        )}

        <Card>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>{t('company')}</TableCell>
                <TableCell>{t('administrator')}</TableCell>
                <TableCell align="right">{t('active_users')}</TableCell>
                <TableCell>{t('created_at')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {companies.map((company) => (
                <TableRow key={company.id}>
                  <TableCell>
                    <Typography fontWeight="bold">{company.name}</Typography>
                    {company.demo && (
                      <Chip size="small" label={t('demo')} sx={{ ml: 1 }} />
                    )}
                  </TableCell>
                  <TableCell>
                    <Typography>{company.adminName}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {company.adminEmail}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">{company.usersCount}</TableCell>
                  <TableCell>
                    {company.createdAt
                      ? new Date(company.createdAt).toLocaleDateString()
                      : ''}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      </Box>

      <Dialog
        open={open}
        onClose={() => !submitting && setOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>{t('new_customer')}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('new_customer_description')}
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField
                fullWidth
                required
                label={t('company_name')}
                value={form.companyName}
                onChange={set('companyName')}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                fullWidth
                type="number"
                label={t('employees_count')}
                value={form.employeesCount}
                onChange={set('employeesCount')}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                fullWidth
                select
                label={t('language')}
                value={form.language}
                onChange={set('language')}
              >
                {LANGUAGES.map((language) => (
                  <MenuItem key={language} value={language}>
                    {language}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12}>
              <Typography variant="subtitle2">
                {t('customer_administrator')}
              </Typography>
            </Grid>
            <Grid item xs={6}>
              <TextField
                fullWidth
                required
                label={t('first_name')}
                value={form.adminFirstName}
                onChange={set('adminFirstName')}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                fullWidth
                required
                label={t('last_name')}
                value={form.adminLastName}
                onChange={set('adminLastName')}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                required
                type="email"
                label={t('email')}
                value={form.adminEmail}
                onChange={set('adminEmail')}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                fullWidth
                label={t('phone')}
                value={form.adminPhone}
                onChange={set('adminPhone')}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                fullWidth
                required
                label={t('temporary_password')}
                value={form.adminPassword}
                onChange={set('adminPassword')}
                helperText={t('temporary_password_help')}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)} disabled={submitting}>
            {t('cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={submit}
            disabled={!valid || submitting}
          >
            {t('create_customer')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

export default CustomerCompanies;
