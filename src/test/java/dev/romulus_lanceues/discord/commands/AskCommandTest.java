package dev.romulus_lanceues.discord.commands;

import dev.romulus_lanceues.ai.ChatService;
import dev.romulus_lanceues.util.RateLimiter;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AskCommandTest {

    private ChatService chatService;
    private RateLimiter rateLimiter;
    private AskCommand command;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;
    private ReplyCallbackAction deferAction;
    private InteractionHook hook;
    private WebhookMessageEditAction editAction;
    private WebhookMessageCreateAction createAction;
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatService = mock(ChatService.class);
        rateLimiter = mock(RateLimiter.class);
        command = new AskCommand(chatService, rateLimiter);

        event = mock(SlashCommandInteractionEvent.class);
        replyAction = mock(ReplyCallbackAction.class);
        deferAction = mock(ReplyCallbackAction.class);
        hook = mock(InteractionHook.class);
        editAction = mock(WebhookMessageEditAction.class);
        createAction = mock(WebhookMessageCreateAction.class);
        user = mock(User.class);

        when(event.getUser()).thenReturn(user);
        when(user.getIdLong()).thenReturn(12345L);

        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);

        when(event.deferReply()).thenReturn(deferAction);

        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginal(anyString())).thenReturn(editAction);
        when(editAction.setAllowedMentions(any())).thenReturn(editAction);

        when(hook.sendMessage(anyString())).thenReturn(createAction);
        when(createAction.setAllowedMentions(any())).thenReturn(createAction);

        when(rateLimiter.checkCooldown(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Has correct command name, description, and required prompt option attributes")
    void hasCorrectNameAndCommandData() {
        assertEquals("ask", command.name());

        SlashCommandData data = command.commandData();
        assertEquals("ask", data.getName());
        assertEquals("Ask Cloud a question or prompt.", data.getDescription());
        assertEquals(1, data.getOptions().size());

        OptionData promptOption = data.getOptions().get(0);
        assertEquals("prompt", promptOption.getName());
        assertEquals(OptionType.STRING, promptOption.getType());
        assertEquals("The question or prompt to send to Cloud.", promptOption.getDescription());
        assertTrue(promptOption.isRequired());
    }

    @ParameterizedTest(name = "Rejects invalid whitespace prompt: {0}")
    @ValueSource(strings = {
            "",
            "   ",
            "\t",
            "\n",
            "\r\n\t  \n",
            "\u2000\u2003\u3000"
    })
    @DisplayName("Rejects blank prompts with ephemeral warning")
    void rejectsBlankPromptWithEphemeralWarning(String invalidPrompt) {
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn(invalidPrompt);
        when(event.getOption("prompt")).thenReturn(promptOption);

        command.handle(event);

        verify(event).reply("Please provide a non-empty prompt.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(event, never()).deferReply();
        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("Rejects null prompt option with ephemeral warning")
    void rejectsNullPromptWithEphemeralWarning() {
        when(event.getOption("prompt")).thenReturn(null);

        command.handle(event);

        verify(event).reply("Please provide a non-empty prompt.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(event, never()).deferReply();
        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("Replies ephemerally when user is in cooldown")
    void blocksUserInCooldown() {
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("Valid question");
        when(event.getOption("prompt")).thenReturn(promptOption);

        when(rateLimiter.checkCooldown(12345L)).thenReturn(Optional.of(Duration.ofSeconds(4)));

        command.handle(event);

        verify(event).reply("⏳ Please wait 4 seconds before asking another question.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(event, never()).deferReply();
        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("Defers reply and edits original with answer under 2000 characters")
    void defersAndEditsOriginalOnSingleChunk() {
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("What is Java?");
        when(event.getOption("prompt")).thenReturn(promptOption);

        when(chatService.ask("What is Java?"))
                .thenReturn(CompletableFuture.completedFuture("Java is an object-oriented programming language."));

        command.handle(event);

        verify(event.deferReply()).queue();
        verify(chatService).ask("What is Java?");
        verify(hook).editOriginal("Java is an object-oriented programming language.");
        verify(editAction).setAllowedMentions(Collections.emptyList());
        verify(editAction).queue();
        verify(hook, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Splits long response into multiple chunks and sends subsequent messages")
    void splitsAndSendsSubsequentMessagesForLongResponse() {
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("Explain quantum mechanics");
        when(event.getOption("prompt")).thenReturn(promptOption);

        String chunk1 = "A".repeat(1500);
        String chunk2 = "B".repeat(1000);
        String fullResponse = chunk1 + "\n\n" + chunk2;

        when(chatService.ask(anyString()))
                .thenReturn(CompletableFuture.completedFuture(fullResponse));

        command.handle(event);

        verify(event.deferReply()).queue();
        ArgumentCaptor<String> editCaptor = ArgumentCaptor.forClass(String.class);
        verify(hook).editOriginal(editCaptor.capture());
        verify(editAction).setAllowedMentions(Collections.emptyList());
        verify(editAction).queue();

        ArgumentCaptor<String> sendCaptor = ArgumentCaptor.forClass(String.class);
        verify(hook).sendMessage(sendCaptor.capture());
        verify(createAction).setAllowedMentions(Collections.emptyList());
        verify(createAction).queue();

        assertEquals(chunk1, editCaptor.getValue());
        assertEquals(chunk2, sendCaptor.getValue());
    }

    @Test
    @DisplayName("Edits original with friendly error when chatService future fails")
    void handlesChatServiceFailureGracefully() {
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("Fail question");
        when(event.getOption("prompt")).thenReturn(promptOption);

        when(chatService.ask(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Crash")));

        command.handle(event);

        verify(event.deferReply()).queue();
        verify(hook).editOriginal("I encountered an unexpected issue while generating a response. Please try again later.");
        verify(editAction).setAllowedMentions(Collections.emptyList());
        verify(editAction).queue();
    }

    @Test
    @DisplayName("Edits original with friendly error when chatService returns empty string")
    void handlesChatServiceEmptyResponseGracefully() {
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("Empty response question");
        when(event.getOption("prompt")).thenReturn(promptOption);

        when(chatService.ask(anyString()))
                .thenReturn(CompletableFuture.completedFuture(""));

        command.handle(event);

        verify(event.deferReply()).queue();
        verify(hook).editOriginal("I encountered an unexpected issue while generating a response. Please try again later.");
        verify(editAction).setAllowedMentions(Collections.emptyList());
        verify(editAction).queue();
    }

    @Test
    @DisplayName("Validates constructor parameters for null checks")
    void validatesConstructorParameters() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> new AskCommand(null, rateLimiter));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> new AskCommand(chatService, null));
    }
}
