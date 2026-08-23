package com.mychat.service.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentIngestNamingTest {

    @Test
    void safeFilenameStripsPathAndIllegalChars() {
        assertEquals("notes.md", DocumentIngestService.safeFilename("C:/tmp/notes.md"));
        assertEquals("a_b.txt", DocumentIngestService.safeFilename("a:b.txt"));
        assertEquals("unnamed", DocumentIngestService.safeFilename("///"));
    }

    @Test
    void extensionOfIsLowercase() {
        assertEquals("pdf", DocumentIngestService.extensionOf("报告.PDF"));
        assertEquals("", DocumentIngestService.extensionOf("noext"));
    }
}
