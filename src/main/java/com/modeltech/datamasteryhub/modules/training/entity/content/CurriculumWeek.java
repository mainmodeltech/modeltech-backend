package com.modeltech.datamasteryhub.modules.training.entity.content;

import java.util.List;

public record CurriculumWeek(String week, String title, String hours, List<String> topics, String project) {
}
