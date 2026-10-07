package com.sap.vo.judger;

import lombok.Data;

/** A catalogue projection deliberately excludes statements, tests and source code. */
@Data
public class OjCatalogRow {
    private Long id;
    private String slug;
    private String title;
    private String difficulty;
    private String status;
    private Long sortOrder;
    private Long revision;
    private String tagsJson;
    private String modesJson;
    private String sourcePlatform;
}
