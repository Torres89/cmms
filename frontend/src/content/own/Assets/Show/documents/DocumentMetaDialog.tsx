import { FC, useEffect, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { DocumentMeta, DocumentRow } from './useAssetDocuments';

interface PropsType {
  open: boolean;
  onClose: () => void;
  document: DocumentRow | null;
  onSubmit: (meta: DocumentMeta) => Promise<any>;
}

const DOC_TYPES = [
  'MANUAL',
  'PARTS_CATALOG',
  'SCHEMATIC',
  'INSPECTION_REPORT',
  'OIL_ANALYSIS',
  'CERTIFICATE',
  'DRAWING',
  'VIDEO',
  'CAD',
  'IMAGE',
  'OTHER'
];

/**
 * Correcting what a document is.
 *
 * Worth having on its own because the type decides whether the file gets
 * parsed at all: a manual uploaded as OTHER is stored and never indexed, and
 * fixing the type here plus a reindex is the whole recovery path.
 */
const DocumentMetaDialog: FC<PropsType> = ({
  open,
  onClose,
  document,
  onSubmit
}) => {
  const { t }: { t: any } = useTranslation();
  const [title, setTitle] = useState('');
  const [docType, setDocType] = useState('OTHER');
  const [revision, setRevision] = useState('');
  const [language, setLanguage] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTitle(document?.title ?? '');
    setDocType(document?.docType ?? 'OTHER');
    setRevision(document?.revision ?? '');
    setLanguage(document?.language ?? '');
    setSaving(false);
  }, [open, document]);

  const submit = () => {
    setSaving(true);
    onSubmit({
      title: title.trim() || undefined,
      docType,
      revision: revision.trim() || undefined,
      language: language.trim() || undefined
    })
      .then(onClose)
      .catch(() => {})
      .finally(() => setSaving(false));
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        <Typography variant="h4">{t('edit_document')}</Typography>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label={t('title')}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            fullWidth
            autoFocus
          />
          <TextField
            select
            label={t('document_type')}
            value={docType}
            onChange={(event) => setDocType(event.target.value)}
            fullWidth
          >
            {DOC_TYPES.map((type) => (
              <MenuItem key={type} value={type}>
                {t(type)}
              </MenuItem>
            ))}
          </TextField>
          <Stack direction="row" spacing={2}>
            <TextField
              label={t('revision')}
              value={revision}
              onChange={(event) => setRevision(event.target.value)}
              fullWidth
            />
            <TextField
              label={t('language')}
              value={language}
              onChange={(event) => setLanguage(event.target.value)}
              placeholder="en"
              sx={{ width: 140 }}
            />
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          {t('cancel')}
        </Button>
        <Button variant="contained" onClick={submit} disabled={saving}>
          {t('save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default DocumentMetaDialog;
