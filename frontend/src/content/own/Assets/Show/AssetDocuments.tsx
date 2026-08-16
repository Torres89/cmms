import { FC, useContext, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  IconButton,
  InputAdornment,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import SearchTwoToneIcon from '@mui/icons-material/SearchTwoTone';
import RefreshTwoToneIcon from '@mui/icons-material/RefreshTwoTone';
import OpenInNewTwoToneIcon from '@mui/icons-material/OpenInNewTwoTone';
import UploadFileTwoToneIcon from '@mui/icons-material/UploadFileTwoTone';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import api from '../../../../utils/api';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import { AssetDTO } from '../../../../models/owns/asset';
import ConfirmDialog from '../../components/ConfirmDialog';
import useAssetDocuments, {
  DocumentRow,
  FileRow
} from './documents/useAssetDocuments';
import UploadDialog from './documents/UploadDialog';
import DocumentMetaDialog from './documents/DocumentMetaDialog';

interface PropsType {
  asset: AssetDTO;
  canEdit?: boolean;
}

interface SearchHit {
  chunkId: number;
  documentId: number;
  documentTitle: string;
  pageFrom?: number;
  pageTo?: number;
  section?: string;
  content: string;
  citation: string;
}

const STATUS_COLOUR: Record<string, any> = {
  READY: 'success',
  PENDING: 'default',
  PARSING: 'info',
  EMBEDDING: 'info',
  FAILED: 'error',
  SKIPPED: 'default'
};

/**
 * Everything attached to this machine, and the search over the part of it that
 * is searchable.
 *
 * Files and documents used to be two tabs because they are two tables. That is
 * an implementation detail: a technician hunting for the hydraulic schematic
 * should not have to know which one it landed in. One list, with the indexed
 * ones showing their ingest status.
 *
 * Every search hit shows its document and page, and clicking it opens the file
 * at that page. That is what closes the trust loop — someone who can jump to
 * the page and read the sentence believes the next answer too.
 */
const AssetDocuments: FC<PropsType> = ({ asset, canEdit = false }) => {
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const {
    rows,
    loading,
    upload,
    updateMeta,
    reindex,
    remove,
    openFile,
    openDocumentAtPage
  } = useAssetDocuments(asset);

  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [editing, setEditing] = useState<DocumentRow | null>(null);
  const [pendingDelete, setPendingDelete] = useState<FileRow | null>(null);

  const search = () => {
    if (!query.trim()) {
      setHits(null);
      return;
    }
    setSearching(true);
    api
      .post<{ results: SearchHit[]; note?: string }>('knowledge/search', {
        query,
        assetId: asset?.id,
        limit: 10
      })
      .then((response) => setHits(response.results))
      .catch(() => showSnackBar(t('search_failed'), 'error'))
      .finally(() => setSearching(false));
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
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h6">{t('files_and_documents')}</Typography>
        {canEdit && (
          <Button
            variant="contained"
            startIcon={<UploadFileTwoToneIcon />}
            onClick={() => setUploadOpen(true)}
          >
            {t('upload')}
          </Button>
        )}
      </Stack>

      <Card sx={{ p: 2 }}>
        <TextField
          fullWidth
          size="small"
          placeholder={t('search_this_machines_documents')}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => event.key === 'Enter' && search()}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchTwoToneIcon fontSize="small" />
              </InputAdornment>
            ),
            endAdornment: (
              <Button size="small" onClick={search} disabled={searching}>
                {t('search')}
              </Button>
            )
          }}
        />

        {hits !== null && (
          <Box sx={{ mt: 2 }}>
            {!hits.length ? (
              <Alert severity="info">{t('nothing_indexed_matches')}</Alert>
            ) : (
              hits.map((hit) => (
                <Box
                  key={hit.chunkId}
                  sx={{ py: 1.25, borderBottom: '1px solid rgba(0,0,0,0.06)' }}
                >
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip
                      size="small"
                      color="primary"
                      variant="outlined"
                      label={hit.citation}
                      onClick={() =>
                        openDocumentAtPage(hit.documentId, hit.pageFrom)
                      }
                      sx={{ height: 20, fontSize: 11, cursor: 'pointer' }}
                    />
                    {hit.section && (
                      <Typography variant="caption" color="text.secondary">
                        {hit.section}
                      </Typography>
                    )}
                  </Stack>
                  <Typography
                    variant="body2"
                    sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}
                  >
                    {hit.content.length > 600
                      ? `${hit.content.slice(0, 600)}…`
                      : hit.content}
                  </Typography>
                </Box>
              ))
            )}
          </Box>
        )}
      </Card>

      {!rows.length ? (
        <Alert severity="info">
          {t('no_documents_indexed_for_this_machine')}
        </Alert>
      ) : (
        <Card>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('title')}</TableCell>
                <TableCell>{t('document_type')}</TableCell>
                <TableCell>{t('revision')}</TableCell>
                <TableCell align="right">{t('pages')}</TableCell>
                <TableCell>{t('ingest_status')}</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.key} hover>
                  <TableCell>
                    <Typography variant="body2">
                      {row.document?.title ?? row.name}
                    </Typography>
                    {row.document && row.name !== row.document.title && (
                      <Typography variant="caption" color="text.secondary">
                        {row.name}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      label={t(row.document?.docType ?? 'attachment')}
                      sx={{ height: 20, fontSize: 11 }}
                    />
                  </TableCell>
                  <TableCell>{row.document?.revision ?? '—'}</TableCell>
                  <TableCell align="right">
                    {row.document?.pageCount ?? '—'}
                  </TableCell>
                  <TableCell>
                    {row.document ? (
                      <Tooltip
                        title={
                          row.document.ingestError ??
                          (row.document.chunkCount
                            ? `${row.document.chunkCount} ${t(
                                'searchable_sections'
                              )}`
                            : '')
                        }
                      >
                        <Chip
                          size="small"
                          color={
                            STATUS_COLOUR[row.document.ingestStatus ?? 'PENDING']
                          }
                          label={t(row.document.ingestStatus ?? 'PENDING')}
                          sx={{ height: 20, fontSize: 11 }}
                        />
                      </Tooltip>
                    ) : (
                      // Not a failure: video, CAD and plain attachments are
                      // stored deliberately without being parsed.
                      <Typography variant="caption" color="text.secondary">
                        {t('not_indexed')}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title={t('open')}>
                      <IconButton size="small" onClick={() => openFile(row)}>
                        <OpenInNewTwoToneIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    {canEdit && row.document && (
                      <>
                        <Tooltip title={t('edit_document')}>
                          <IconButton
                            size="small"
                            onClick={() => setEditing(row.document)}
                          >
                            <EditTwoToneIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={t('reindex')}>
                          <IconButton
                            size="small"
                            onClick={() =>
                              reindex(row.document.id).catch(() => {})
                            }
                          >
                            <RefreshTwoToneIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </>
                    )}
                    {canEdit && (
                      <Tooltip title={t('to_delete')}>
                        <IconButton
                          size="small"
                          onClick={() => setPendingDelete(row)}
                        >
                          <DeleteTwoToneIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      <UploadDialog
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        onUpload={upload}
      />

      <DocumentMetaDialog
        open={!!editing}
        onClose={() => setEditing(null)}
        document={editing}
        onSubmit={(meta) => updateMeta(editing.id, meta)}
      />

      <ConfirmDialog
        open={!!pendingDelete}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const target = pendingDelete;
          setPendingDelete(null);
          if (target) remove(target).catch(() => {});
        }}
        confirmText={t('to_delete')}
        question={t('confirm_delete_document')}
      />
    </Stack>
  );
};

export default AssetDocuments;
