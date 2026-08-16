import { useCallback, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import api, { authHeader, getErrorMessage } from '../../../../../utils/api';
import { apiUrl } from '../../../../../config';
import { CustomSnackBarContext } from '../../../../../contexts/CustomSnackBarContext';
import { AssetDTO } from '../../../../../models/owns/asset';
import { FileMiniDTO } from '../../../../../models/owns/file';
import { patchAsset } from '../dossier/assetWrites';
import useMutations from '../dossier/useMutations';

export interface DocumentRow {
  id: number;
  title: string;
  docType?: string;
  revision?: string;
  language?: string;
  pageCount?: number;
  ingestStatus?: string;
  chunkCount?: number;
  ingestError?: string;
  file?: { id: number; name?: string };
}

/**
 * One row of the merged tab: a stored file, and the document record it became
 * if it was worth indexing.
 */
export interface FileRow {
  key: string;
  fileId?: number;
  name: string;
  url?: string;
  document?: DocumentRow;
}

export interface DocumentMeta {
  title?: string;
  docType?: string;
  revision?: string;
  language?: string;
}

/**
 * Files and documents for one machine, as one list.
 *
 * They were two tabs because they are two tables: a File is bytes in a bucket,
 * a Document is what those bytes mean. That distinction matters to the ingest
 * pipeline and to nobody else — a technician looking for the hydraulic schematic
 * should not have to know which tab it landed in. So they are merged on file id
 * here, and a manual appears once, as a file that happens to be searchable.
 */
const useAssetDocuments = (asset: AssetDTO) => {
  const assetId = asset?.id;
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [documents, setDocuments] = useState<DocumentRow[]>([]);
  const [files, setFiles] = useState<FileMiniDTO[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    if (!assetId) return Promise.resolve();
    setLoading(true);
    return Promise.all([
      api.get<DocumentRow[]>(`documents?assetId=${assetId}`),
      // The asset carries its own attachments; re-read rather than trusting the
      // redux copy, which an upload has just invalidated.
      api.get<AssetDTO>(`assets/${assetId}`)
    ])
      .then(([loadedDocuments, loadedAsset]) => {
        setDocuments(loadedDocuments);
        setFiles(loadedAsset.files ?? []);
      })
      .catch(() => showSnackBar(t('could_not_load_documents'), 'error'))
      .finally(() => setLoading(false));
  }, [assetId]);

  useEffect(() => {
    load();
  }, [load]);

  const { run } = useMutations(load);

  /** Files first, then any document whose file is not among them. */
  const rows: FileRow[] = [
    ...files.map((file) => ({
      key: `file-${file.id}`,
      fileId: file.id,
      name: file.name,
      url: file.url,
      document: documents.find((doc) => doc.file?.id === file.id)
    })),
    ...documents
      .filter((doc) => !files.some((file) => file.id === doc.file?.id))
      .map((doc) => ({
        key: `doc-${doc.id}`,
        fileId: doc.file?.id,
        name: doc.file?.name ?? doc.title,
        document: doc
      }))
  ];

  /**
   * Upload, then attach.
   *
   * `POST /files/upload` stores the bytes and registers a Document for the
   * indexable types, but it does not touch the asset's own file list — so
   * without the second step the upload would be searchable and invisible.
   */
  const upload = (selected: FileList, docType: string) => {
    const form = new FormData();
    Array.from(selected).forEach((file) => form.append('files', file));
    form.append('folder', `company/assets/${assetId}`);
    form.append('hidden', 'false');
    form.append('type', docType);
    form.append('assetId', String(assetId));

    // FormData sets its own multipart boundary, so authHeader's JSON
    // content-type has to come off or the request is rejected.
    const headers = { ...(authHeader(false) as Record<string, string>) };
    delete headers['Content-Type'];

    const request = fetch(`${apiUrl}files/upload`, {
      method: 'POST',
      body: form,
      headers
    })
      .then(async (response) => {
        if (!response.ok) throw new Error(await response.text());
        return response.json() as Promise<FileMiniDTO[]>;
      })
      .then((uploaded) =>
        patchAsset(asset, {
          files: [...(asset.files ?? []), ...uploaded.map(({ id }) => ({ id }))]
        } as Partial<AssetDTO>)
      );

    return run(request, 'files_uploaded', 'could_not_upload');
  };

  const updateMeta = (documentId: number, meta: DocumentMeta) =>
    run(
      api.patch(`documents/${documentId}`, meta),
      'document_saved',
      'could_not_save_document'
    );

  const reindex = (documentId: number) =>
    run(
      api.post(`documents/${documentId}/reindex`, {}),
      'queued_for_reindexing',
      'could_not_reindex'
    );

  /**
   * Delete the document first, then the file, then the asset's reference.
   *
   * That order is deliberate: a file left behind after a failed document delete
   * is recoverable, whereas a document row pointing at bytes that no longer
   * exist poisons every search result that cites it.
   */
  const remove = (row: FileRow) => {
    let request: Promise<any> = Promise.resolve();
    if (row.document) {
      request = api.deletes(`documents/${row.document.id}`);
    }
    if (row.fileId) {
      request = request
        .then(() => api.deletes(`files/${row.fileId}`))
        // A file the current user did not upload may be refused; the document
        // is already gone, and losing the tab over it helps nobody.
        .catch((error) => {
          if (!row.document) throw error;
        })
        .then(() =>
          patchAsset(asset, {
            files: (asset.files ?? []).filter((file) => file.id !== row.fileId)
          } as Partial<AssetDTO>)
        );
    }
    return run(request, 'document_deleted', 'could_not_delete_document');
  };

  const openFile = (row: FileRow, page?: number) => {
    if (row.document) {
      api
        .get<{ url: string }>(
          `documents/${row.document.id}/url${page ? `?page=${page}` : ''}`
        )
        .then((response) => window.open(response.url, '_blank', 'noopener'))
        .catch((error) =>
          showSnackBar(
            getErrorMessage(error, t('could_not_open_document')),
            'error'
          )
        );
    } else if (row.url) {
      window.open(row.url, '_blank', 'noopener');
    }
  };

  const openDocumentAtPage = (documentId: number, page?: number) =>
    api
      .get<{ url: string }>(
        `documents/${documentId}/url${page ? `?page=${page}` : ''}`
      )
      .then((response) => window.open(response.url, '_blank', 'noopener'))
      .catch(() => showSnackBar(t('could_not_open_document'), 'error'));

  return {
    rows,
    loading,
    reload: load,
    upload,
    updateMeta,
    reindex,
    remove,
    openFile,
    openDocumentAtPage
  };
};

export default useAssetDocuments;
