import { useCallback, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../../../../../utils/api';
import { CustomSnackBarContext } from '../../../../../contexts/CustomSnackBarContext';
import {
  ComponentInstance,
  FailureEvent,
  FailureMode
} from '../../../../../models/owns/dossier';
import useMutations from '../dossier/useMutations';

export interface HistoryResponse {
  workOrders: {
    id: number;
    title?: string;
    status?: string;
    priority?: string;
    createdAt?: string;
    completedOn?: string;
    description?: string;
  }[];
  failureEvents: any[];
  pareto: {
    code: string;
    name: string;
    count: number;
    downtimeMinutes: number;
    repairCost: number;
    mtbfDays?: number;
    mttrMinutes?: number;
  }[];
}

export interface FailureEventPayload {
  failureModeId?: number;
  componentId?: number;
  workOrderId?: number;
  occurredAt?: string;
  mechanism?: string;
  cause?: string;
  detectionMethod?: string;
  detectedAt?: string;
  severity?: number;
  downtimeMinutes?: number;
  repairCost?: number;
  correctiveAction?: string;
  preventiveRecommendation?: string;
}

const reference = (id?: number) => (id ? { id } : null);

/**
 * The machine's history, and the writes that build it.
 *
 * Failures are read from /assets/{id}/failures rather than from the flattened
 * maintenance-history payload, because editing one needs the ids it references
 * — the failure mode, the component, the work order — and the flat payload
 * carries only their labels.
 */
const useAssetHistory = (assetId: number, equipmentClass?: string) => {
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [history, setHistory] = useState<HistoryResponse | null>(null);
  const [failures, setFailures] = useState<FailureEvent[]>([]);
  const [componentEvents, setComponentEvents] = useState<any[]>([]);
  const [modes, setModes] = useState<FailureMode[]>([]);
  const [components, setComponents] = useState<ComponentInstance[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    return Promise.all([
      api.get<HistoryResponse>(`assets/${assetId}/maintenance-history?limit=100`),
      api.get<FailureEvent[]>(`assets/${assetId}/failures`).catch(() => []),
      api.get<any[]>(`components/position/${assetId}/history`).catch(() => []),
      // Ranked by what has happened to this machine before, which is what makes
      // the dropdown usable rather than a list to scroll past.
      api
        .get<FailureMode[]>(`assets/${assetId}/failure-modes`)
        .catch(() => [] as FailureMode[]),
      api
        .get<ComponentInstance[]>(`assets/${assetId}/components`)
        .catch(() => [] as ComponentInstance[])
    ])
      .then(
        ([
          loadedHistory,
          loadedFailures,
          events,
          loadedModes,
          loadedComponents
        ]) => {
          setHistory(loadedHistory);
          setFailures(loadedFailures);
          setComponentEvents(events);
          setModes(loadedModes);
          setComponents(loadedComponents);
        }
      )
      .catch(() => showSnackBar(t('could_not_load_history'), 'error'))
      .finally(() => setLoading(false));
  }, [assetId]);

  useEffect(() => {
    load();
  }, [load]);

  const { run } = useMutations(load);

  const body = (payload: FailureEventPayload) => ({
    failureMode: reference(payload.failureModeId),
    component: reference(payload.componentId),
    workOrder: reference(payload.workOrderId),
    occurredAt: payload.occurredAt ?? null,
    mechanism: payload.mechanism ?? null,
    cause: payload.cause ?? null,
    detectionMethod: payload.detectionMethod ?? null,
    detectedAt: payload.detectedAt ?? null,
    severity: payload.severity ?? null,
    downtimeMinutes: payload.downtimeMinutes ?? null,
    repairCost: payload.repairCost ?? null,
    correctiveAction: payload.correctiveAction ?? null,
    preventiveRecommendation: payload.preventiveRecommendation ?? null
  });

  const saveFailure = (id: number | null, payload: FailureEventPayload) =>
    run(
      id
        ? api.patch(`failure-events/${id}`, body(payload))
        : api.post('failure-events', {
            ...body(payload),
            asset: { id: assetId }
          }),
      'failure_saved',
      'could_not_save_failure'
    );

  const deleteFailure = (id: number) =>
    run(
      api.deletes(`failure-events/${id}`),
      'failure_deleted',
      'could_not_delete_failure'
    );

  // --- the catalogue --------------------------------------------------

  const saveMode = (id: number | null, mode: Partial<FailureMode>) =>
    run(
      id
        ? api.patch(`failure-modes/${id}`, mode)
        : api.post('failure-modes', { ...mode, equipmentClass }),
      'failure_mode_saved',
      'could_not_save_failure_mode'
    );

  const deleteMode = (id: number) =>
    run(
      api.deletes(`failure-modes/${id}`),
      'failure_mode_deleted',
      'could_not_delete_failure_mode'
    );

  return {
    history,
    failures,
    componentEvents,
    modes,
    components,
    workOrders: history?.workOrders ?? [],
    loading,
    reload: load,
    saveFailure,
    deleteFailure,
    saveMode,
    deleteMode
  };
};

export default useAssetHistory;
