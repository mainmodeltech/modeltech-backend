package com.modeltech.datamasteryhub.modules.auth.controller;

import com.modeltech.datamasteryhub.common.dto.ApiResponse;
import com.modeltech.datamasteryhub.modules.auth.dto.response.AdminUserSummaryResponse;
import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.entity.RoleNames;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.TreeSet;

/** Formateurs disponibles (comptes actifs avec le rôle TRAINER) pour l'affectation aux sessions. */
@RestController
@RequestMapping("/api/v1/admin/trainers")
@RequiredArgsConstructor
@Tag(name = "Admin - Formateurs")
public class AdminTrainerController {

    private final AdminUserRepository adminUserRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<AdminUserSummaryResponse>>> getAll() {
        List<AdminUserSummaryResponse> trainers = adminUserRepository
                .findAllByRolesNameAndActiveTrueAndIsDeletedFalseOrderByFullNameAsc(RoleNames.TRAINER).stream()
                .map(this::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.ok(trainers.size() + " formateur(s)", trainers));
    }

    private AdminUserSummaryResponse toSummary(AdminUser a) {
        return AdminUserSummaryResponse.builder()
                .id(a.getId()).email(a.getEmail()).fullName(a.getFullName())
                .roles(a.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toCollection(TreeSet::new)))
                .active(a.isActive())
                .build();
    }
}
