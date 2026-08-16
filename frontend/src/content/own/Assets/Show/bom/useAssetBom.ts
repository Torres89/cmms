import { useCallback, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../../../../../utils/api';
import { CustomSnackBarContext } from '../../../../../contexts/CustomSnackBarContext';
import { BomLine, RestockKit } from '../../../../../models/owns/dossier';
import useMutations from '../dossier/useMutations';

export interface BomLinePayload {
  partId: number;
  positionCode?: string;
  qtyPerAssembly?: number;
  consumable: boolean;
  replaceIntervalHours?: number;
  replaceIntervalMonths?: number;
  notes?: string;
}

interface BomResponse {
  lines: BomLine[];
  note?: string;
}

/**
 * The bill of materials, and every write against it.
 *
 * One rule worth knowing: AssetBomController.patch assigns `consumable`,
 * `replaceIntervalHours` and `replaceIntervalMonths` unconditionally, so any of
 * the three omitted from a request body is written as null (or false). All
 * three are therefore always sent. This is the same destructive-PATCH shape the
 * Specs tab hit, and keeping it in one function is why this hook exists.
 */
const useAssetBom = (assetId: number) => {
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [bom, setBom] = useState<BomResponse | null>(null);
  const [kit, setKit] = useState<RestockKit | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    return api
      .get<BomResponse>(`assets/${assetId}/bom`)
      .then(setBom)
      .catch(() => showSnackBar(t('could_not_load_bom'), 'error'))
      .finally(() => setLoading(false));
  }, [assetId]);

  useEffect(() => {
    load();
  }, [load]);

  const { run } = useMutations(load);

  const body = (payload: BomLinePayload) => ({
    positionCode: payload.positionCode ?? null,
    qtyPerAssembly: payload.qtyPerAssembly ?? 1,
    // All three always sent — see the note above.
    consumable: payload.consumable,
    replaceIntervalHours: payload.replaceIntervalHours ?? null,
    replaceIntervalMonths: payload.replaceIntervalMonths ?? null,
    notes: payload.notes ?? null
  });

  const create = (payload: BomLinePayload) =>
    run(
      api.post('bom-lines', {
        ...body(payload),
        asset: { id: assetId },
        part: { id: payload.partId }
      }),
      'bom_line_saved',
      'could_not_save_bom_line'
    );

  const update = (id: number, payload: BomLinePayload) =>
    run(
      api.patch(`bom-lines/${id}`, {
        ...body(payload),
        part: { id: payload.partId }
      }),
      'bom_line_saved',
      'could_not_save_bom_line'
    );

  const remove = (id: number) =>
    run(
      api.deletes(`bom-lines/${id}`),
      'bom_line_deleted',
      'could_not_delete_bom_line'
    );

  /** Create a part that has never been stocked, so a BOM can be written first. */
  const createPart = (part: {
    name: string;
    manufacturer?: string;
    mpn?: string;
    unit?: string;
  }) => api.post<{ id: number; name: string }>('parts', part);

  const loadKit = () =>
    api
      .get<RestockKit>(`assets/${assetId}/restock-kit?horizonDays=60`)
      .then(setKit)
      .catch(() => showSnackBar(t('could_not_load_restock_kit'), 'error'));

  return {
    lines: bom?.lines ?? [],
    note: bom?.note,
    kit,
    loading,
    reload: load,
    create,
    update,
    remove,
    createPart,
    loadKit
  };
};

export default useAssetBom;
