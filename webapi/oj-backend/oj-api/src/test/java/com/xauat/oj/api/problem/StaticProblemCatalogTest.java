package com.xauat.oj.api.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticProblemCatalogTest {
    private final StaticProblemCatalog catalog = new StaticProblemCatalog(new ObjectMapper());

    @Test
    void loadsExportedStaticCatalog() {
        assertTrue(catalog.contains(1001));
        assertTrue(catalog.contains(2047));
        Map<String, Object> problem = catalog.find(1001).orElseThrow();
        assertEquals(1001, problem.get("id"));
        assertTrue(problem.get("testCases") instanceof java.util.List<?>);
    }
}
