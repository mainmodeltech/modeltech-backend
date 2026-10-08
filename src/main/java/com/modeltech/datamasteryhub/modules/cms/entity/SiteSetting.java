package com.modeltech.datamasteryhub.modules.cms.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Un contenu du site (clé → JSON libre) modifiable depuis le back-office.
 * Tout ce qui est stocké ici est PUBLIC : jamais de secret ni de donnée interne.
 */
@Entity
@Table(name = "site_settings")
@Getter @Setter @NoArgsConstructor
public class SiteSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "setting_key", nullable = false, updatable = false, length = 100)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode value;
}
