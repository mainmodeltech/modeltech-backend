package com.modeltech.datamasteryhub.modules.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modeltech.datamasteryhub.modules.cms.dto.response.SiteSettingResponse;

import java.util.List;
import java.util.Map;

public interface SiteSettingService {

    /** Tous les contenus publiés, par clé (endpoint public du site). */
    Map<String, JsonNode> findAllPublic();

    List<SiteSettingResponse> findAllForAdmin();

    /** Crée la clé ou remplace sa valeur. */
    SiteSettingResponse upsert(String key, JsonNode value);

    /** Retire le contenu : le site masque alors le bloc correspondant. */
    void delete(String key);
}
