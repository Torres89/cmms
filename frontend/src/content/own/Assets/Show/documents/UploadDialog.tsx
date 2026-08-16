import { FC, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  CircularProgress,
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
import UploadFileTwoToneIcon from '@mui/icons-material/UploadFileTwoTone';

interface PropsType {
  open: boolean;
  onClose: () => void;
  onUpload: (files: FileList, docType: string) => Promise<any>;
}

/**
 * Document types, in the order a machine file is likely to be one.
 *
 * The first seven are indexable — the API parses, chunks and embeds them, and
 * they become searchable. VIDEO and CAD never are: they are stored and served
 * by signed URL, because a 500 MB training video must not pass through the
 * API's heap. IMAGE and OTHER are plain attachments.
 */
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

const UploadDialog: FC<PropsType> = ({ open, onClose, onUpload }) => {
  const { t }: { t: any } = useTranslation();
  const inputRef = useRef<HTMLInputElement>(null);
  const [docType, setDocType] = useState('MANUAL');
  const [selected, setSelected] = useState<FileList | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setDocType('MANUAL');
    setSelected(null);
    setUploading(false);
    setError(null);
  }, [open]);

  const submit = () => {
    if (!selected?.length) {
      setError(t('drop_files_here'));
      return;
    }
    setUploading(true);
    onUpload(selected, docType)
      .then(onClose)
      .catch(() => {})
      .finally(() => setUploading(false));
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        <Typography variant="h4">{t('upload')}</Typography>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ pt: 1 }}>
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

          <Alert severity="info">{t('indexable_types_are_searchable')}</Alert>

          <input
            ref={inputRef}
            type="file"
            multiple
            hidden
            onChange={(event) => {
              setSelected(event.target.files);
              setError(null);
            }}
          />
          <Button
            variant="outlined"
            startIcon={<UploadFileTwoToneIcon />}
            onClick={() => inputRef.current?.click()}
            disabled={uploading}
          >
            {t('drop_files_here')}
          </Button>

          {selected?.length > 0 && (
            <Stack spacing={0.5}>
              {Array.from(selected).map((file) => (
                <Typography key={file.name} variant="body2">
                  {file.name}{' '}
                  <Typography
                    component="span"
                    variant="caption"
                    color="text.secondary"
                  >
                    {(file.size / 1024 / 1024).toFixed(1)} MB
                  </Typography>
                </Typography>
              ))}
            </Stack>
          )}

          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={uploading}>
          {t('cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={submit}
          disabled={uploading}
          startIcon={uploading ? <CircularProgress size="1rem" /> : null}
        >
          {t('upload')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default UploadDialog;
