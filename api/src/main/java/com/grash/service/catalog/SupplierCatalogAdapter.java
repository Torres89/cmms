package com.grash.service.catalog;

import java.util.Optional;

/**
 * A source of supplier offers for a part.
 * <p>
 * Deliberately optional. McMaster-Carr runs a Product Information API for
 * approved customers, and Grainger, Motion and MSC are punchout/EDI-oriented
 * and need an account plus a partner conversation — so no feature here is ever
 * gated on a vendor API. The data model is filled from documents, user input
 * and pasted URLs, and a catalogue is an accelerator on top of that. A shop
 * with no API access still gets 90 % of the value.
 */
public interface SupplierCatalogAdapter {

    /**
     * Identifier used in configuration and shown in the UI.
     */
    String key();

    /**
     * Human-readable name of the supplier.
     */
    String displayName();

    /**
     * Whether this adapter is usable right now — credentials present, etc.
     */
    boolean isConfigured();

    Optional<SupplierOffer> lookupByMpn(String manufacturer, String mpn);

    Optional<SupplierOffer> lookupBySku(String sku);

    /**
     * Whether this adapter can submit a purchase order, or only look prices up.
     */
    boolean supportsOrdering();
}
