package com.kopite.devspace;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Compare every persisted snapshot field, excluding only the nonserialized observation counter. */
final class SnapshotAssertions {
    private SnapshotAssertions() {}

    static void assertDataEquals(Object expected, Object actual) {
        assertEquals(payload(expected), payload(actual));
    }

    private static Object payload(Object value) {
        if (value instanceof List<?> list) return list.stream().map(SnapshotAssertions::payload).toList();
        if (value == null || !value.getClass().isRecord()) return value;
        var fields = new LinkedHashMap<String, Object>();
        fields.put("$type", value.getClass().getName());
        for (var component : value.getClass().getRecordComponents()) {
            if (component.getName().equals("dataRevision")) continue;
            try {
                fields.put(component.getName(), payload(component.getAccessor().invoke(value)));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
        return fields;
    }
}
