package com.modeltech.datamasteryhub.modules.communication.dto.request;

import com.modeltech.datamasteryhub.modules.communication.enums.PeopleCount;
import com.modeltech.datamasteryhub.modules.communication.enums.TrainingNeed;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Demande de diagnostic gratuit d'une entreprise (page /entreprises). */
@Data
@Schema(description = "Demande de diagnostic gratuit d'une entreprise")
public class DiagnosticRequestDTO {

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100)
    private String firstName;

    /** Facultatif (un seul mot saisi dans « Nom et prénom »). */
    @Size(max = 100)
    private String lastName;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    @Size(max = 255)
    private String email;

    @Size(max = 50)
    private String phone;

    @NotBlank(message = "L'entreprise est obligatoire")
    @Size(max = 255)
    private String company;

    /** Fonction du demandeur : DRH, DAF, manager… */
    @NotBlank(message = "La fonction est obligatoire")
    @Size(max = 150)
    private String role;

    @Schema(description = "RANGE_1_5 (« 1 à 5 »), RANGE_6_15, RANGE_16_50, OVER_50 (« Plus de 50 »)")
    @NotNull(message = "Le nombre de personnes à former est obligatoire")
    private PeopleCount peopleCount;

    @Schema(description = "EXCEL_UPGRADE, POWER_BI_CUSTOM, DATA_PATH_CUSTOM ou UNDECIDED (« Je ne sais pas encore »)")
    @NotNull(message = "Le besoin principal est obligatoire")
    private TrainingNeed need;

    /** Outils utilisés, difficultés, échéance… */
    @Size(max = 4000)
    private String context;
}
