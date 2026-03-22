package com.modeltech.datamasteryhub.modules.training.mapper;

import com.modeltech.datamasteryhub.modules.training.dto.request.CreateRegistrationRequest;
import com.modeltech.datamasteryhub.modules.training.dto.response.PaymentResponse;
import com.modeltech.datamasteryhub.modules.training.dto.response.RegistrationResponse;
import com.modeltech.datamasteryhub.modules.training.entity.Payment;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {PaymentMapper.class})
public interface RegistrationMapper {

    @Mapping(target = "bootcampId", source = "bootcamp.id")
    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "payments", source = "payments")
    RegistrationResponse toResponse(Registration entity);

    // ── Champs gérés manuellement dans le service ──────────────────────────
    @Mapping(target = "id",              ignore = true)
    @Mapping(target = "bootcamp",        ignore = true)
    @Mapping(target = "session",         ignore = true)
    @Mapping(target = "sessionName",     ignore = true)
    @Mapping(target = "promoCodeId",     ignore = true)
    @Mapping(target = "promoCodeUsed",   ignore = true)
    @Mapping(target = "discountPercent", ignore = true)
    @Mapping(target = "status",          ignore = true)
    @Mapping(target = "createdAt",       ignore = true)
    @Mapping(target = "updatedAt",       ignore = true)
    @Mapping(target = "createdBy",       ignore = true)
    @Mapping(target = "updatedBy",       ignore = true)
    @Mapping(target = "deleted",         ignore = true)
    @Mapping(target = "deletedAt",       ignore = true)
    @Mapping(target = "deletedBy",       ignore = true)
    @Mapping(target = "paymentStatus",   ignore = true)
    @Mapping(target = "amountDue",       ignore = true)
    @Mapping(target = "amountPaid",      ignore = true)
    @Mapping(target = "payments",        ignore = true)

    // ── recaptchaToken : présent dans le DTO, absent de l'entité ──────────
    // MapStruct lèverait une erreur "unmapped source property" sans cet ignore.
    //    @Mapping(target = "recaptchaToken",  ignore = true) // pas de champ en entité

    // ── Nouveaux champs mappés automatiquement (noms identiques) ──────────
    // country  → Registration.country  ✓
    // profile  → Registration.profile  ✓  (enum RegistrantProfile)
    // school   → Registration.school   ✓
    Registration toEntity(CreateRegistrationRequest request);
}