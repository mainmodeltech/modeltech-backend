package com.modeltech.datamasteryhub.modules.communication.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import com.modeltech.datamasteryhub.modules.communication.enums.ContactType;
import com.modeltech.datamasteryhub.modules.communication.enums.RequesterType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Table(name = "contact_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class ContactMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;
    private String company;
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    private ContactMessageStatus status = ContactMessageStatus.unread;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Origine du message (contact, diagnostic entreprise, candidature partenaire). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContactType type;

    /** Particulier ou entreprise (facultatif). */
    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", length = 20)
    private RequesterType requesterType;

    /** Champs structurés des formulaires dédiés (diagnostic, candidature partenaire). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;
}
