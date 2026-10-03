package com.xauat.oj.api.contest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PackageServiceCanonicalTest {
    private final PackageService service = new PackageService(null, null, null, null, new ObjectMapper());

    @Test
    void sortsKeysRecursivelyDeterministically() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 1);
        first.put("a", Map.of("d", 2, "c", 3));
        assertEquals("{\"a\":{\"c\":3,\"d\":2},\"b\":1}", service.canonical(first));

        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("a", Map.of("c", 3, "d", 2));
        reordered.put("b", 1);
        assertEquals(service.canonical(first), service.canonical(reordered));
    }

    @Test
    void preservesArrayOrder() {
        assertEquals("{\"cases\":[1,2,3]}", service.canonical(Map.of("cases", List.of(1, 2, 3))));
        assertNotEquals(service.canonical(Map.of("cases", List.of(1, 2))), service.canonical(Map.of("cases", List.of(2, 1))));
    }
}
