package dev.romulus_lanceues.ai;

import java.util.concurrent.CompletableFuture;

public interface ChatService {
    CompletableFuture<String> ask(String prompt);
}
