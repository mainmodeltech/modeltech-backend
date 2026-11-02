package com.modeltech.datamasteryhub.modules.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "sim_exam_form_item")
@Getter @Setter @NoArgsConstructor
public class SimExamFormItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    private SimExamForm form;

    @Column(nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_version_id", nullable = false)
    private SimItemVersion itemVersion;

    @Column(name = "domain_code", nullable = false, length = 10)
    private String domainCode;

    @Column(name = "skill_code", nullable = false, length = 30)
    private String skillCode;
}
