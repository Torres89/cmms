import { useCallback, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../../../../../utils/api';
import { CustomSnackBarContext } from '../../../../../contexts/CustomSnackBarContext';
import {
  AssetDossier,
  ComponentAction,
  ComponentInstance
} from '../../../../../models/owns/dossier';
import { AssetDTO } from '../../../../../models/owns/asset';
import { patchAssetById } from '../dossier/assetWrites';
import useMutations from '../dossier/useMutations';

/** The editable half of a position; the rest is inherited from its parent. */
export interface PositionPayload {
  name: string;
  positionCode?: string;
  level?: string;
  trackingClass?: string;
  criticality?: number;
  functionalDescription?: string;
}

/**
 * Everything the Structure tab writes.
 *
 * Two shapes of write live here and they are not interchangeable:
 *
 * - A **position** is an Asset row, so it is created and edited through the
 *   asset endpoints, with all the full-replace care that implies.
 * - A **component movement** is an event, so install / remove / overhaul /
 *   scrap are POSTs against actions rather than field edits. Modelling them as
 *   edits would lose the back-to-birth ledger that is the entire point of
 *   tracking components at all — which is also why position and counters are
 *   not offered as editable fields.
 */
const useAssetStructure = (asset: AssetDTO) => {
  const assetId = asset?.id;
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [dossier, setDossier] = useState<AssetDossier | null>(null);
  const [spares, setSpares] = useState<ComponentInstance[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    if (!assetId) return Promise.resolve();
    setLoading(true);
    return Promise.all([
      api.get<AssetDossier>(`assets/${assetId}/dossier`),
      // Components not currently in a position are what an install can offer.
      api.get<ComponentInstance[]>('components').catch(() => [])
    ])
      .then(([loadedDossier, allComponents]) => {
        setDossier(loadedDossier);
        setSpares(allComponents.filter((component) => !component.currentPosition));
      })
      .catch(() => showSnackBar(t('could_not_load_structure'), 'error'))
      .finally(() => setLoading(false));
  }, [assetId]);

  useEffect(() => {
    load();
  }, [load]);

  const { run } = useMutations(load);

  // --- positions ------------------------------------------------------

  const addPosition = (parentId: number, payload: PositionPayload) =>
    run(
      api.post('assets', {
        ...payload,
        parentAsset: { id: parentId },
        // A position with no location drops out of every location-scoped view,
        // and nobody wants to pick a location for a bearing housing. It sits
        // where its machine sits.
        location: asset?.location ? { id: asset.location.id } : null,
        equipmentClass: asset?.equipmentClass,
        status: 'OPERATIONAL'
      }),
      'position_saved',
      'could_not_save_position'
    );

  const updatePosition = (positionId: number, payload: PositionPayload) =>
    run(
      patchAssetById(positionId, payload as Partial<AssetDTO>),
      'position_saved',
      'could_not_save_position'
    );

  const deletePosition = (positionId: number) =>
    run(
      api.deletes(`assets/${positionId}`),
      'position_deleted',
      'could_not_delete_position'
    );

  // --- components -----------------------------------------------------

  const createComponent = (component: Partial<ComponentInstance>) =>
    api.post<ComponentInstance>('components', component);

  const saveComponent = (
    id: number | null,
    component: Partial<ComponentInstance>
  ) =>
    run(
      id
        ? api.patch(`components/${id}`, component)
        : createComponent(component),
      'component_saved',
      'could_not_save_component'
    );

  const deleteComponent = (id: number) =>
    run(
      api.deletes(`components/${id}`),
      'component_deleted',
      'could_not_delete_component'
    );

  const install = (componentId: number, action: ComponentAction) =>
    run(
      api.post(`components/${componentId}/install`, action),
      'component_installed',
      'could_not_save_component'
    );

  /**
   * Create and install in one go. Commissioning a machine means entering
   * components that have never existed in the system, so an install that could
   * only pick from stock would be unusable on the day it matters most.
   */
  const createAndInstall = (
    component: Partial<ComponentInstance>,
    action: ComponentAction
  ) =>
    run(
      createComponent(component).then((created) =>
        api.post(`components/${created.id}/install`, action)
      ),
      'component_installed',
      'could_not_save_component'
    );

  const removeComponent = (componentId: number, action: ComponentAction) =>
    run(
      api.post(`components/${componentId}/remove`, action),
      'component_removed',
      'could_not_save_component'
    );

  const overhaul = (componentId: number, action: ComponentAction) =>
    run(
      api.post(`components/${componentId}/overhaul`, action),
      'component_overhauled',
      'could_not_save_component'
    );

  const scrap = (componentId: number, action: ComponentAction) =>
    run(
      api.post(`components/${componentId}/scrap`, action),
      'component_scrapped',
      'could_not_save_component'
    );

  return {
    dossier,
    spares,
    loading,
    reload: load,
    addPosition,
    updatePosition,
    deletePosition,
    saveComponent,
    deleteComponent,
    install,
    createAndInstall,
    removeComponent,
    overhaul,
    scrap
  };
};

export default useAssetStructure;
