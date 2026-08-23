package com.mychat.service.knowledge;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.IntArrayList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenSlidingSplitterTest {

    @Test
    void overlapSharesTrailingTokensWithNextChunk() {
        String text = "知识库切分重叠测试。".repeat(40);
        int chunkSize = 20;
        int overlap = 8;
        List<String> chunks = TokenSlidingSplitter.split(text, chunkSize, overlap);
        assertTrue(chunks.size() >= 2);

        Encoding enc = TokenSlidingSplitter.encoding();
        IntArrayList first = enc.encodeOrdinary(chunks.get(0));
        IntArrayList second = enc.encodeOrdinary(chunks.get(1));
        int shared = 0;
        for (int i = 0; i < overlap; i++) {
            int a = first.get(first.size() - overlap + i);
            int b = second.get(i);
            if (a == b) {
                shared++;
            }
        }
        assertEquals(overlap, shared);
    }

    @Test
    void invalidOverlapRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TokenSlidingSplitter.split("abc", 10, 10));
    }
}
