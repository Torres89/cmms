import { useCallback, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../../../../../utils/api';
import { CustomSnackBarContext } from '../../../../../contexts/CustomSnackBarContext';
import { AssetSpec, SpecCompleteness } from '../../../../../models/owns/dossier';

/** A new spec, ready to POST. `asset` and `source` are added by the hook. */
export interface NewSpecPayload {
  specKey: string;
  specGroup: string;
  label?: string;
  unit?: string;
  valueText: string | null;
  valueNum: number | null;
}

/** An edit to an existing spec. Both value fields are mandatory - see below. */
export interface SpecValueUpdate {
  valueText: string | null;
  valueNum: number | null;
  unit?: string;
  label?: string;
  specGroup?: string;
  specKey?: string;
}

/**
 * Every HTTP call the Specs tab makes, in one place.
 *
 * The single rule worth knowing: AssetSpecController.patch writes valueText and
 * valueNum unconditionally, so an omitted field is stored as null. Both are
 * therefore always sent, with the unused one explicitly null. Keeping that in
 * one function is why this hook exists.
 */
const useAssetSpecs = (assetId: number) => {
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [specs, setSpecs] = useState<AssetSpec[]>([]);
  const [completeness, setCompleteness] = useState<SpecCompleteness | null>(
    null
  );
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    return Promise.all([
      api.get<AssetSpec[]>(`assets/${assetId}/specs`),
      api.get<SpecCompleteness>(`assets/${assetId}/specs/completeness`)
    ])
      .then(([loadedSpecs, loadedCompleteness]) => {
        setSpecs(loadedSpecs);
        setCompleteness(loadedCompleteness);
      })
      .catch(() => showSnackBar(t('could_not_load_specs'), 'error'))
      .finally(() => setLoading(false));
  }, [assetId]);

  useEffect(() => {
    load();
  }, [load]);

  const create = (payload: NewSpecPayload) =>
    api
      .post('asset-specs', {
        ...payload,
        asset: { id: assetId },
        source: 'MANUAL_ENTRY'
      })
      .then(() => {
        showSnackBar(t('spec_added'), 'success');
        return load();
      })
      .catch((err) => {
        showSnackBar(t('could_not_add_spec'), 'error');
        throw err;
      });

  const update = (id: number, changes: SpecValueUpdate) =>
    api
      .patch(`asset-specs/${id}`, {
        ...changes,
        valueText: changes.valueText ?? null,
        valueNum: changes.valueNum ?? null
      })
      .then(() => {
        showSnackBar(t('changes_saved_success'), 'success');
        return load();
      })
      .catch((err) => {
        showSnackBar(t('could_not_save_spec'), 'error');
        throw err;
      });

  const remove = (id: number) =>
    api
      .deletes(`asset-specs/${id}`)
      .then(() => {
        showSnackBar(t('spec_deleted'), 'success');
        return load();
      })
      .catch((err) => {
        showSnackBar(t('could_not_delete_spec'), 'error');
        throw err;
      });

  const verify = (id: number) =>
    api
      .post(`asset-specs/${id}/verify`, {})
      .then(load)
      .catch((err) => {
        showSnackBar(t('could_not_verify_specs'), 'error');
        throw err;
      });

  const unverify = (id: number) =>
    api
      .post(`asset-specs/${id}/unverify`, {})
      .then(load)
      .catch((err) => {
        showSnackBar(t('could_not_unverify_spec'), 'error');
        throw err;
      });

  const verifyAll = (ids: number[]) =>
    api
      .post('asset-specs/verify', ids)
      .then(() => {
        showSnackBar(t('specs_verified'), 'success');
        return load();
      })
      .catch((err) => {
        showSnackBar(t('could_not_verify_specs'), 'error');
        throw err;
      });

  return {
    specs,
    completeness,
    loading,
    reload: load,
    create,
    update,
    remove,
    verify,
    unverify,
    verifyAll
  };
};

export default useAssetSpecs;
