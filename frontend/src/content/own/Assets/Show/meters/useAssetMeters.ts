import { useCallback, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../../../../../utils/api';
import { CustomSnackBarContext } from '../../../../../contexts/CustomSnackBarContext';
import Meter from '../../../../../models/owns/meter';
import Reading from '../../../../../models/owns/reading';
import useMutations from '../dossier/useMutations';

export interface MeterPayload {
  name: string;
  unit?: string;
  updateFrequency: number;
}

/**
 * Meters on one machine, and their readings.
 *
 * Readings are what make the rest of the dossier move: component hour counters
 * roll forward from them, and every usage-based PM interval is measured against
 * them. A meter nobody can add, correct or delete is a counter that drifts.
 *
 * PATCH /meters/{id} maps a DTO covering name, unit, updateFrequency, category,
 * location and users, so all of those are always sent — an omitted one is
 * written as null. Fields outside that DTO (the asset, usageBasis) are not
 * mapped at all and are safe.
 */
const useAssetMeters = (assetId: number) => {
  const { t }: { t: any } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [meters, setMeters] = useState<Meter[]>([]);
  const [readings, setReadings] = useState<Record<number, Reading[]>>({});
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    if (!assetId) return Promise.resolve();
    setLoading(true);
    return api
      .get<Meter[]>(`meters/asset/${assetId}`)
      .then((loadedMeters) => {
        setMeters(loadedMeters);
        return Promise.all(
          loadedMeters.map((meter) =>
            api
              .get<Reading[]>(`readings/meter/${meter.id}`)
              .catch(() => [] as Reading[])
              .then((loaded) => [meter.id, loaded] as [number, Reading[]])
          )
        );
      })
      .then((pairs) => setReadings(Object.fromEntries(pairs)))
      .catch(() => showSnackBar(t('could_not_load_meters'), 'error'))
      .finally(() => setLoading(false));
  }, [assetId]);

  useEffect(() => {
    load();
  }, [load]);

  const { run } = useMutations(load);

  const saveMeter = (id: number | null, payload: MeterPayload, existing?: Meter) =>
    run(
      id
        ? api.patch(`meters/${id}`, {
            ...payload,
            unit: payload.unit ?? '',
            // Carried over rather than omitted: the patch DTO covers these, so
            // leaving them out clears them.
            meterCategory: existing?.meterCategory ?? null,
            location: existing?.location ?? null,
            users: existing?.users ?? []
          })
        : api.post('meters', {
            ...payload,
            unit: payload.unit ?? '',
            asset: { id: assetId },
            users: []
          }),
      'meter_saved',
      'could_not_save_meter'
    );

  const deleteMeter = (id: number) =>
    run(api.deletes(`meters/${id}`), 'meter_deleted', 'could_not_delete_meter');

  const addReading = (meterId: number, value: number) =>
    run(
      api.post('readings', { value, meter: { id: meterId } }),
      'reading_saved',
      'could_not_save_reading'
    );

  const updateReading = (readingId: number, meterId: number, value: number) =>
    run(
      api.patch(`readings/${readingId}`, { value, meter: { id: meterId } }),
      'reading_saved',
      'could_not_save_reading'
    );

  const deleteReading = (readingId: number) =>
    run(
      api.deletes(`readings/${readingId}`),
      'reading_deleted',
      'could_not_delete_reading'
    );

  return {
    meters,
    readings,
    loading,
    reload: load,
    saveMeter,
    deleteMeter,
    addReading,
    updateReading,
    deleteReading
  };
};

export default useAssetMeters;
