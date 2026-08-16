import { FC, useContext, useEffect, useState } from 'react';
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
import api from '../../../../utils/api';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';

interface PropsType {
  assetId: number;
}

interface DocumentRow {
  id: number;
  title: string;
  docType?: string;
  revision?: string;
  pageCount?: number;
  ingestStatus?: string;
  chunkCount?: number;
  ingestError?: string;
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
 * Documents and the search over them.
 *
 * Every hit shows its document and page, and clicking it opens the file at
 * that page. That is what closes the trust loop — a technician who can jump
 * to the page and see the sentence believes the next answer too.
 */
const AssetDocuments: FC<PropsType> = ({ assetId }) => {
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [documents, setDocuments] = useState<DocumentRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [searching, setSearching] = useState(false);

  const load = () => {
    setLoading(true);
    api
      .get<DocumentRow[]>(`documents?assetId=${assetId}`)
      .then(setDocuments)
      .catch(() => showSnackBar(t('could_not_load_documents'), 'error'))
      .finally(() => setLoading(false));
  };

  useEffect(load, [assetId]);

  const search = () => {
    if (!query.trim()) {
      setHits(null);
      return;
    }
    setSearching(true);
    api
      .post<{ results: SearchHit[]; note?: string }>('knowledge/search', {
        query,
        assetId,
        limit: 10
      })
      .then((response) => setHits(response.results))
      .catch(() => showSnackBar(t('search_failed'), 'error'))
      .finally(() => setSearching(false));
  };

  const openAtPage = (documentId: number, page?: number) => {
    api
      .get<{ url: string }>(
        `documents/${documentId}/url${page ? `?page=${page}` : ''}`
      )
      .then((response) => window.open(response.url, '_blank', 'noopener'))
      .catch(() => showSnackBar(t('could_not_open_document'), 'error'));
  };

  const reindex = (documentId: number) => {
    api
      .post(`documents/${documentId}/reindex`, {})
      .then(() => {
        showSnackBar(t('queued_for_reindexing'), 'success');
        load();
      })
      .catch(() => showSnackBar(t('could_not_reindex'), 'error'));
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
                      onClick={() => openAtPage(hit.documentId, hit.pageFrom)}
                      sx={{ height: 20, fontSize: 11, cursor: 'pointer' }}
                    />
                    {hit.section && (
                      <Typography variant="caption" color="text.secondary">
                        {hit.section}
                      </Typography>
                    )}
                  </Stack>
                  <Typography variant="body2" sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}>
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

      {!documents.length ? (
        <Alert severity="info">{t('no_documents_indexed_for_this_machine')}</Alert>
      ) : (
        <Card>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('title')}</TableCell>
                <TableCell>{t('type')}</TableCell>
                <TableCell>{t('revision')}</TableCell>
                <TableCell align="right">{t('pages')}</TableCell>
                <TableCell>{t('ingest_status')}</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {documents.map((document) => (
                <TableRow key={document.id} hover>
                  <TableCell>{document.title}</TableCell>
                  <TableCell>
                    {document.docType && (
                      <Chip size="small" variant="outlined" label={t(document.docType)} />
                    )}
                  </TableCell>
                  <TableCell>{document.revision ?? '—'}</TableCell>
                  <TableCell align="right">{document.pageCount ?? '—'}</TableCell>
                  <TableCell>
                    <Tooltip
                      title={
                        document.ingestError ??
                        (document.chunkCount
                          ? `${document.chunkCount} ${t('searchable_sections')}`
                          : '')
                      }
                    >
                      <Chip
                        size="small"
                        color={STATUS_COLOUR[document.ingestStatus ?? 'PENDING']}
                        label={t(document.ingestStatus ?? 'PENDING')}
                        sx={{ height: 20, fontSize: 11 }}
                      />
                    </Tooltip>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title={t('open')}>
                      <IconButton size="small" onClick={() => openAtPage(document.id)}>
                        <OpenInNewTwoToneIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title={t('reindex')}>
                      <IconButton size="small" onClick={() => reindex(document.id)}>
                        <RefreshTwoToneIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
    </Stack>
  );
};

export default AssetDocuments;
