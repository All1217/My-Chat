package com.mychat.service.knowledge;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

import java.util.ArrayList;
import java.util.List;

/**
 * Token 滑动窗口切分。Spring AI 2.0 的 {@code TokenTextSplitter} 无 overlap，overlap&gt;0 时用本类。
 */
public final class TokenSlidingSplitter {

    private static final Encoding ENCODING =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private TokenSlidingSplitter() {
    }

    /**
     * 按 token 窗口切分；步长为 {@code chunkSize - chunkOverlap}。
     *
     * @return 文本片段；原文为空则空列表
     */
    public static List<String> split(String text, int chunkSize, int chunkOverlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize 必须 ≥ 1");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap 须 ≥ 0 且小于 chunkSize");
        }
        IntArrayList tokens = ENCODING.encodeOrdinary(text);
        int tokenCount = tokens.size();
        if (tokenCount == 0) {
            return List.of();
        }
        int step = chunkSize - chunkOverlap;
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < tokenCount; start += step) {
            int end = Math.min(start + chunkSize, tokenCount);
            chunks.add(ENCODING.decode(slice(tokens, start, end)));
            if (end >= tokenCount) {
                break;
            }
        }
        return chunks;
    }

    /** 复制 [start, end) 的 token。 */
    static IntArrayList slice(IntArrayList tokens, int start, int end) {
        IntArrayList out = new IntArrayList(end - start);
        for (int i = start; i < end; i++) {
            out.add(tokens.get(i));
        }
        return out;
    }

    /** 供单测核对 overlap：相邻窗口共享的 token 数。 */
    static Encoding encoding() {
        return ENCODING;
    }
}
