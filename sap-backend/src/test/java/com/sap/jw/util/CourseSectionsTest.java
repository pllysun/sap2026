package com.sap.jw.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class CourseSectionsTest {
    @Test void parsesRangesListsAndFullWidthSectionsWithoutAddingGaps() {
        for (String raw : List.of("1-8", "第１～８节", "[1至8节]"))
            assertEquals(IntStream.rangeClosed(1,8).boxed().toList(),CourseSections.parse(raw));
        assertEquals(List.of(1,2,5,7,8),CourseSections.parse("1-2、5、7-8"));
        assertEquals(List.of(3),CourseSections.parse("3"));
        assertEquals(List.of(5,6),CourseSections.fromRow("第3节",3));
        assertEquals(List.of(5,6,7),CourseSections.fromRow("第5-7节",3));
        for (String raw : List.of("", "待定", "0-8", "1-999", "8-1", "1,", "1-3foo"))
            assertTrue(CourseSections.parse(raw).isEmpty(),raw);
    }
}
