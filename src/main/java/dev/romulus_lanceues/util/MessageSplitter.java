package dev.romulus_lanceues.util;

import java.util.ArrayList;
import java.util.List;

public final class MessageSplitter {

    public static final int DEFAULT_MAX_CHUNKS = 3;
    public static final String TRUNCATION_SUFFIX = "... [response truncated]";

    private MessageSplitter() {
        // Pure utility class
    }

    public static List<String> split(String text, int maxLength) {
        return split(text, maxLength, DEFAULT_MAX_CHUNKS);
    }

    public static List<String> split(String text, int maxLength, int maxChunks) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        if (maxChunks <= 0) {
            throw new IllegalArgumentException("maxChunks must be positive");
        }

        List<String> chunks = new ArrayList<>();
        int currentStart = 0;
        int totalLength = text.length();

        while (currentStart < totalLength && chunks.size() < maxChunks) {
            int remaining = totalLength - currentStart;
            boolean isLastAllowedChunk = (chunks.size() == maxChunks - 1);

            if (isLastAllowedChunk) {
                if (remaining <= maxLength) {
                    chunks.add(text.substring(currentStart));
                } else {
                    int allowedContentLength = maxLength - TRUNCATION_SUFFIX.length();
                    if (allowedContentLength <= 0) {
                        chunks.add(text.substring(currentStart, currentStart + maxLength));
                    } else {
                        int splitIndex = findSplitIndex(text, currentStart, allowedContentLength);
                        String truncated = text.substring(currentStart, splitIndex).stripTrailing() + TRUNCATION_SUFFIX;
                        chunks.add(truncated);
                    }
                }
                break;
            } else {
                if (remaining <= maxLength) {
                    chunks.add(text.substring(currentStart));
                    break;
                }

                int splitIndex = findSplitIndex(text, currentStart, maxLength);
                chunks.add(text.substring(currentStart, splitIndex).stripTrailing());
                currentStart = splitIndex;
                while (currentStart < totalLength && Character.isWhitespace(text.charAt(currentStart))) {
                    currentStart++;
                }
            }
        }

        return chunks;
    }

    private static int findSplitIndex(String text, int start, int maxLen) {
        int candidateEnd = start + maxLen;
        if (candidateEnd >= text.length()) {
            return text.length();
        }

        // Try to split on newline first
        int lastNewline = text.lastIndexOf('\n', candidateEnd - 1);
        if (lastNewline > start) {
            return lastNewline;
        }

        // Try to split on whitespace / space next
        int lastSpace = text.lastIndexOf(' ', candidateEnd - 1);
        if (lastSpace > start) {
            return lastSpace;
        }

        // Hard break if no suitable delimiter found
        return candidateEnd;
    }
}
