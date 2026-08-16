package com.grash.service;

import com.grash.model.AssetBomLine;
import com.grash.repository.AssetBomLineRepository;
import com.grash.repository.AssetRepository;
import com.grash.repository.PartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The bill of materials for a machine.
 * <p>
 * Note the deliberate absence of any "best guess" behaviour: if a machine has
 * no BOM, the answer is an empty list. A model reading this must be able to say
 * "that hasn't been captured yet" rather than produce a plausible part number.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetBomService {

    private final AssetBomLineRepository assetBomLineRepository;
    private final PartRepository partRepository;
    private final AssetRepository assetRepository;

    /**
     * The whole machine's BOM, including everything on its subunits.
     * <p>
     * Falls back to the asset's own lines if the recursive query is unavailable
     * (an H2 test harness, say) rather than returning nothing.
     */
    public List<AssetBomLine> findByAsset(Long assetId) {
        try {
            return assetBomLineRepository.findInAssetSubtree(assetId);
        } catch (Exception e) {
            log.debug("Falling back to direct BOM lookup for asset {}: {}", assetId, e.getMessage());
            return assetBomLineRepository.findByAsset_IdOrderByPositionCodeAsc(assetId);
        }
    }

    /** Lines at one position only. */
    public List<AssetBomLine> findByAssetAndPosition(Long assetId, String positionCode) {
        return findByAsset(assetId).stream()
                .filter(line -> positionCode.equalsIgnoreCase(line.getPositionCode()))
                .collect(Collectors.toList());
    }

    /** The lines a restock kit is built from. */
    public List<AssetBomLine> findConsumables(Long assetId) {
        try {
            return assetBomLineRepository.findConsumablesInAssetSubtree(assetId);
        } catch (Exception e) {
            log.debug("Falling back to direct consumable lookup for asset {}: {}",
                    assetId, e.getMessage());
            return assetBomLineRepository.findByAsset_IdAndConsumableTrue(assetId);
        }
    }

    public List<AssetBomLine> findByPart(Long partId) {
        return assetBomLineRepository.findByPart_Id(partId);
    }

    public Optional<AssetBomLine> findById(Long id) {
        return assetBomLineRepository.findById(id);
    }

    public AssetBomLine create(AssetBomLine line) {
        AssetBomLine saved = assetBomLineRepository.save(line);
        linkPartToAsset(saved);
        return saved;
    }

    public AssetBomLine save(AssetBomLine line) {
        AssetBomLine saved = assetBomLineRepository.save(line);
        linkPartToAsset(saved);
        return saved;
    }

    public void delete(Long id) {
        assetBomLineRepository.findById(id).ifPresent(this::unlinkPartFromAsset);
        assetBomLineRepository.deleteById(id);
    }

    /**
     * Keep the part's asset list in step with the BOM.
     * <p>
     * The BOM and {@code T_Asset_Part_Associations} are two different tables,
     * and until this existed a part could be on a machine's bill of materials
     * while the part's own page insisted it was used on no machine at all.
     * Inventory is where a buyer stands when they ask "what needs this?", so
     * the BOM has to answer there too.
     */
    private void linkPartToAsset(AssetBomLine line) {
        if (line.getPart() == null || line.getAsset() == null) {
            return;
        }
        partRepository.findById(line.getPart().getId()).ifPresent(part -> {
            boolean alreadyLinked = part.getAssets().stream()
                    .anyMatch(asset -> asset.getId().equals(line.getAsset().getId()));
            if (alreadyLinked) {
                return;
            }
            assetRepository.findById(line.getAsset().getId()).ifPresent(asset -> {
                part.getAssets().add(asset);
                partRepository.save(part);
            });
        });
    }

    /**
     * Unlink only when nothing else on that machine needs the part — a machine
     * can list the same part at two positions, and removing one must not make
     * the other vanish from the part's page.
     */
    private void unlinkPartFromAsset(AssetBomLine line) {
        if (line.getPart() == null || line.getAsset() == null) {
            return;
        }
        Long partId = line.getPart().getId();
        Long assetId = line.getAsset().getId();
        boolean stillNeeded = assetBomLineRepository.findByPart_Id(partId).stream()
                .anyMatch(other -> !other.getId().equals(line.getId())
                        && other.getAsset() != null
                        && other.getAsset().getId().equals(assetId));
        if (stillNeeded) {
            return;
        }
        partRepository.findById(partId).ifPresent(part -> {
            if (part.getAssets().removeIf(asset -> asset.getId().equals(assetId))) {
                partRepository.save(part);
            }
        });
    }
}
