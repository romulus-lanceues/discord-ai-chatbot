package dev.romulus_lanceues.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageSplitterTest {

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Returns empty list for null or empty input")
    void returnsEmptyListForNullOrEmpty(String input) {
        assertTrue(MessageSplitter.split(input, 2000).isEmpty());
    }

    @Test
    @DisplayName("Throws exception if maxLength is not positive")
    void throwsOnInvalidMaxLength() {
        assertThrows(IllegalArgumentException.class, () -> MessageSplitter.split("hello", 0));
        assertThrows(IllegalArgumentException.class, () -> MessageSplitter.split("hello", -5));
    }

    @Test
    @DisplayName("Single chunk when message length is strictly under or equal to maxLength")
    void singleChunkUnderLimit() {
        String msg = "Hello world!";
        List<String> chunks = MessageSplitter.split(msg, 2000);
        assertEquals(1, chunks.size());
        assertEquals("Hello world!", chunks.get(0));

        String exact = "a".repeat(2000);
        List<String> exactChunks = MessageSplitter.split(exact, 2000);
        assertEquals(1, exactChunks.size());
        assertEquals(2000, exactChunks.get(0).length());
    }

    @Test
    @DisplayName("Splits cleanly at newline boundary")
    void splitsAtNewline() {
        String p1 = "First paragraph of text.";
        String p2 = "Second paragraph of text.";
        String text = p1 + "\n" + p2;

        List<String> chunks = MessageSplitter.split(text, 30);
        assertEquals(2, chunks.size());
        assertEquals(p1, chunks.get(0));
        assertEquals(p2, chunks.get(1));
    }

    @Test
    @DisplayName("Splits cleanly at space boundary when no newline is available")
    void splitsAtSpaceBoundary() {
        String text = "The quick brown fox jumps over the lazy dog";
        List<String> chunks = MessageSplitter.split(text, 20);
        assertEquals(3, chunks.size());
        assertEquals("The quick brown fox", chunks.get(0));
        assertEquals("jumps over the lazy", chunks.get(1));
        assertEquals("dog", chunks.get(2));
    }

    @Test
    @DisplayName("Hard splits when a single word exceeds maxLength")
    void hardSplitsOverlongWord() {
        String longWord = "A".repeat(60);
        List<String> chunks = MessageSplitter.split(longWord, 20);
        assertEquals(3, chunks.size());
        assertEquals("A".repeat(20), chunks.get(0));
        assertEquals("A".repeat(20), chunks.get(1));
        assertEquals("A".repeat(20), chunks.get(2));
    }

    @Test
    @DisplayName("Hard splits and truncates when single word exceeds total capacity of maxChunks")
    void hardSplitsOverlongWordExceedingCapacity() {
        String overlong = "A".repeat(150);
        List<String> chunks = MessageSplitter.split(overlong, 40);
        assertEquals(3, chunks.size());
        assertEquals("A".repeat(40), chunks.get(0));
        assertEquals("A".repeat(40), chunks.get(1));
        assertTrue(chunks.get(2).endsWith(MessageSplitter.TRUNCATION_SUFFIX));
        assertTrue(chunks.get(2).length() <= 40);
    }

    @Test
    @DisplayName("Caps at default maxChunks (3) and appends truncation notice")
    void capsAtMaxChunksWithTruncationNotice() {
        String longText = "Paragraph line to be repeated.\n".repeat(300); // ~9000 chars
        List<String> chunks = MessageSplitter.split(longText, 2000);

        assertEquals(3, chunks.size());
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 2000, "Chunk exceeds 2000 chars: " + chunk.length());
        }
        assertTrue(chunks.get(2).endsWith(MessageSplitter.TRUNCATION_SUFFIX));
    }

    @Test
    @DisplayName("Throws exception if maxChunks is not positive")
    void throwsOnInvalidMaxChunks() {
        assertThrows(IllegalArgumentException.class, () -> MessageSplitter.split("hello", 2000, 0));
        assertThrows(IllegalArgumentException.class, () -> MessageSplitter.split("hello", 2000, -3));
    }

    @Test
    @DisplayName("Splits multiple paragraphs separated by double newlines without empty chunks")
    void splitsMultipleParagraphs() {
        String p1 = "First paragraph here.";
        String p2 = "Second paragraph follows.";
        String p3 = "Third paragraph ends it.";
        String text = p1 + "\n\n" + p2 + "\n\n" + p3;

        List<String> chunks = MessageSplitter.split(text, 30);
        assertEquals(3, chunks.size());
        assertEquals(p1, chunks.get(0));
        assertEquals(p2, chunks.get(1));
        assertEquals(p3, chunks.get(2));
    }

    @Test
    @DisplayName("Handles maxLength smaller than truncation suffix length gracefully")
    void handlesMaxLengthSmallerThanTruncationSuffix() {
        String text = "1234567890abcdefghij";
        // TRUNCATION_SUFFIX is 23 chars, maxLength is 10
        List<String> chunks = MessageSplitter.split(text, 10, 1);
        assertEquals(1, chunks.size());
        assertEquals("1234567890", chunks.get(0));
    }

    @Test
    @DisplayName("Handles unicode multi-byte characters and emojis properly")
    void handlesUnicodeAndEmojis() {
        String emojiText = "🤖 Bot response line one \n☁️ Cloud assistant response line two";
        List<String> chunks = MessageSplitter.split(emojiText, 30);
        assertTrue(chunks.size() >= 2);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 30);
        }
    }
}
