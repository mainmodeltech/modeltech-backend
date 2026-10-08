package com.modeltech.datamasteryhub.modules.cms.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeltech.datamasteryhub.exception.ResourceNotFoundException;
import com.modeltech.datamasteryhub.modules.cms.dto.response.SiteSettingResponse;
import com.modeltech.datamasteryhub.modules.cms.entity.SiteSetting;
import com.modeltech.datamasteryhub.modules.cms.repository.SiteSettingRepository;
import com.modeltech.datamasteryhub.modules.cms.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SiteSettingServiceImpl implements SiteSettingService {

    private static final Pattern KEY_FORMAT = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,99}$");
    private static final int MAX_VALUE_BYTES = 20 * 1024;

    private final SiteSettingRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, JsonNode> findAllPublic() {
        Map<String, JsonNode> all = new LinkedHashMap<>();
        repository.findAllByIsDeletedFalseOrderByKeyAsc().forEach(s -> all.put(s.getKey(), s.getValue()));
        return all;
    }

    @Override
    public List<SiteSettingResponse> findAllForAdmin() {
        return repository.findAllByIsDeletedFalseOrderByKeyAsc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SiteSettingResponse upsert(String key, JsonNode value) {
        requireValidKey(key);
        if (value == null || value.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La valeur est obligatoire : utilisez DELETE pour retirer un contenu.");
        }
        if (value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Contenu trop volumineux (20 Ko max).");
        }

        SiteSetting setting = repository.findByKey(key).orElseGet(() -> {
            SiteSetting created = new SiteSetting();
            created.setKey(key);
            return created;
        });
        setting.setValue(value);
        setting.setDeleted(false);
        setting.setDeletedAt(null);
        setting.setDeletedBy(null);
        log.info("Contenu du site « {} » enregistré", key);
        return toResponse(repository.save(setting));
    }

    @Override
    @Transactional
    public void delete(String key) {
        SiteSetting setting = repository.findByKey(key)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Contenu du site", "clé", key));
        setting.setDeleted(true);
        setting.setDeletedAt(LocalDateTime.now());
        setting.setDeletedBy("system");
        repository.save(setting);
    }

    private void requireValidKey(String key) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Clé invalide : minuscules, chiffres, points, tirets et soulignés (100 caractères max).");
        }
    }

    private SiteSettingResponse toResponse(SiteSetting s) {
        return SiteSettingResponse.builder()
                .key(s.getKey())
                .value(s.getValue())
                .updatedAt(s.getUpdatedAt() != null ? s.getUpdatedAt() : s.getCreatedAt())
                .updatedBy(s.getUpdatedBy() != null ? s.getUpdatedBy() : s.getCreatedBy())
                .build();
    }
}
