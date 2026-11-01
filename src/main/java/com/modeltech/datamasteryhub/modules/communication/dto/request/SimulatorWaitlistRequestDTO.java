package com.modeltech.datamasteryhub.modules.communication.dto.request;

import com.modeltech.datamasteryhub.modules.communication.enums.ExamHorizon;
import com.modeltech.datamasteryhub.modules.communication.enums.SimulatorPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Inscription à la liste d'attente (pré-réservation, sans engagement) du simulateur de certification. */
@Data
@Schema(description = "Liste d'attente du simulateur de certification")
public class SimulatorWaitlistRequestDTO {

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Email(message = "Email invalide")
    @NotBlank(message = "L'email est obligatoire")
    @Size(max = 255)
    private String email;

    @Size(max = 50)
    private String phone;

    /** Facultatif : entreprise ou école (utile pour une licence). */
    @Size(max = 255)
    private String company;

    @Schema(description = "ESSENTIEL, PRO, PRO_COACHING, ENTREPRISE ou UNDECIDED")
    @NotNull(message = "Le forfait souhaité est obligatoire")
    private SimulatorPlan plan;

    @Schema(description = "WITHIN_1_MONTH, WITHIN_3_MONTHS, LATER ou UNDECIDED")
    @NotNull(message = "La date d'examen envisagée est obligatoire")
    private ExamHorizon examHorizon;

    /** Niveau actuel, outils utilisés, attentes… */
    @Size(max = 2000)
    private String context;

    @Schema(description = "Doit être vrai : la personne accepte d'être contactée au sujet du simulateur")
    @AssertTrue(message = "Votre accord pour être contacté est obligatoire")
    private boolean consent;
}
