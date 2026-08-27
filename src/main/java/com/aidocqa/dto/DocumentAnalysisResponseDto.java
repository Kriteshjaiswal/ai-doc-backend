package com.aidocqa.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAnalysisResponseDto {

    private Long documentId;
    private String fileName;
    private Integer pageCount;
    private String documentType;
    private String language;
    private String summary;
    private String fullSummary;
    private String confidence;
    private String analysisStatus;

    @Builder.Default
    private List<TopicDto> topics = new ArrayList<>();

    @Builder.Default
    private List<ImportantDateDto> dates = new ArrayList<>();

    @Builder.Default
    private List<FinancialFigureDto> financialFigures = new ArrayList<>();

    @Builder.Default
    private List<RiskDto> risks = new ArrayList<>();

    @Builder.Default
    private List<EntityDto> entities = new ArrayList<>();

    @Builder.Default
    private List<ClauseDto> clauses = new ArrayList<>();

    @Builder.Default
    private List<SectionDto> sections = new ArrayList<>();

    @Builder.Default
    private List<ActionItemDto> actionItems = new ArrayList<>();

    private DocumentStatsDto stats;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicDto {
        private String name;
        private Integer count;
        @Builder.Default
        private List<Integer> pages = new ArrayList<>();
        private String description;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportantDateDto {
        private String date;
        private String event;
        private Integer page;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FinancialFigureDto {
        private String label;
        private String value;
        private String category;
        private Integer page;
        private String trend;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskDto {
        private String title;
        private String severity; // Critical, High, Medium, Low
        private String description;
        private Integer page;
        private String mitigation;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EntityDto {
        private String name;
        private String type; // Organization, Person, Location, Product
        private Integer mentions;
        private String context;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClauseDto {
        private String title;
        private String category;
        private String summary;
        private Integer page;
        private String importance; // High, Medium, Low
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SectionDto {
        private String title;
        private Integer startPage;
        private Integer endPage;
        private String summary;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActionItemDto {
        private String task;
        private String assignee;
        private String deadline;
        private Integer page;
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentStatsDto {
        private Integer pages;
        private Integer summaryCount;
        private Integer keyTopicsCount;
        private Integer datesCount;
        private Integer financialsCount;
        private Integer risksCount;
        private Integer entitiesCount;
        private Integer clausesCount;
    }
}
