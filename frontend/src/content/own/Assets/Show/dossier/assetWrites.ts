import api from '../../../../../utils/api';
import { AssetDTO } from '../../../../../models/owns/asset';

/**
 * Writing to an asset without destroying it.
 *
 * `PATCH /assets/{id}` is a full replace: AssetMapper.updateAsset maps every
 * field of AssetPatchDTO onto the entity, and only the eight
 * machine-specialist fields (level, positionCode, functionalDescription,
 * criticality, downtimeCostPerHour, replacementCost, trackingClass,
 * equipmentClass) are IGNORE-on-null. Everything else — name, location, files,
 * parts, teams, vendors — is written as null when the body omits it.
 *
 * On top of that, AssetController.patch dereferences `status` without a null
 * check, so a body missing it is a 500 rather than a validation error.
 *
 * So there is exactly one safe way to change one field of an asset: send the
 * whole asset back with that field overridden. Every asset write in the dossier
 * tabs goes through here. A round-trip of GET → PATCH was confirmed lossless
 * against the running API before this was written.
 */

/** Send the whole asset back with `overrides` applied on top. */
export const patchAsset = (
  asset: AssetDTO,
  overrides: Partial<AssetDTO>
): Promise<AssetDTO> =>
  api.patch<AssetDTO>(`assets/${asset.id}`, {
    ...asset,
    // Never let this reach the server absent; see above.
    status: overrides.status ?? asset.status ?? 'OPERATIONAL',
    ...overrides
  });

/**
 * The same, for an asset we do not already hold — a child position edited from
 * the structure tree, where the tree only carries a summary node.
 *
 * Fetching first is not an optimisation to remove later: patching from the
 * summary alone would null every field the summary does not carry.
 */
export const patchAssetById = (
  assetId: number,
  overrides: Partial<AssetDTO>
): Promise<AssetDTO> =>
  api
    .get<AssetDTO>(`assets/${assetId}`)
    .then((asset) => patchAsset(asset, overrides));
