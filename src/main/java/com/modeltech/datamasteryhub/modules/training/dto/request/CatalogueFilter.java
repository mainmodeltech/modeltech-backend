package com.modeltech.datamasteryhub.modules.training.dto.request;

import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.FormationFormat;
import com.modeltech.datamasteryhub.modules.training.enums.FormationLevel;

/**
 * Filtres facultatifs du catalogue public (GET /api/v1/formations). Un champ nul
 * signifie « pas de filtre ».
 *
 * @param domain      slug du domaine
 * @param deliveredBy INTERNAL ou PARTNER
 * @param level       niveau
 * @param format      format de la formation
 * @param targetRole  métier ciblé (insensible à la casse)
 */
public record CatalogueFilter(
        String domain,
        DeliveredBy deliveredBy,
        FormationLevel level,
        FormationFormat format,
        String targetRole
) {
    public static CatalogueFilter none() {
        return new CatalogueFilter(null, null, null, null, null);
    }
}
