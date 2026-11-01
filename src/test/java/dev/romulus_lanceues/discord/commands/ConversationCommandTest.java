package dev.romulus_lanceues.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConversationCommandTest {

    private ConversationCommand command;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        command = new ConversationCommand();
        event = mock(SlashCommandInteractionEvent.class);
        replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    }

    @Test
    @DisplayName("Has correct command name, description, and required prompt option")
    void hasCorrectNameAndCommandData() {
        assertEquals("conversation", command.name());

        SlashCommandData data = command.commandData();
        assertEquals("conversation", data.getName());
        assertEquals("Have a conversation with Cloud.", data.getDescription());

        List<OptionData> options = data.getOptions();
        assertEquals(1, options.size());

        OptionData promptOption = options.get(0);
        assertEquals("prompt", promptOption.getName());
        assertEquals(OptionType.STRING, promptOption.getType());
        assertTrue(promptOption.isRequired());
        assertEquals("The message or question to send to Cloud.", promptOption.getDescription());
    }

    @Test
    @DisplayName("Replies with placeholder message echoing valid prompt")
    void repliesWithPlaceholderEchoingPrompt() {
        OptionMapping mapping = mock(OptionMapping.class);
        when(mapping.getAsString()).thenReturn("Let's talk about space travel!");
        when(event.getOption("prompt")).thenReturn(mapping);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();
        verify(replyAction, never()).setEphemeral(anyBoolean());

        String message = captor.getValue();
        assertTrue(message.contains("Conversation prompt received"));
        assertTrue(message.contains("Let's talk about space travel!"));
        assertTrue(message.contains("Gemini AI conversation will be connected here"));
    }

    @Test
    @DisplayName("Strips surrounding whitespace from prompt before processing")
    void stripsSurroundingWhitespaceFromPrompt() {
        OptionMapping mapping = mock(OptionMapping.class);
        when(mapping.getAsString()).thenReturn("   hello in a conversation   ");
        when(event.getOption("prompt")).thenReturn(mapping);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();

        String message = captor.getValue();
        assertTrue(message.contains("\"hello in a conversation\""));
        assertFalse(message.contains("   hello"));
    }

    @ParameterizedTest(name = "Blank input: ''{0}''")
    @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n\t  \n", "\u2000\u2003\u3000"})
    @DisplayName("Rejects blank prompt inputs with ephemeral error message")
    void rejectsBlankPromptWithEphemeralError(String blankPrompt) {
        OptionMapping mapping = mock(OptionMapping.class);
        when(mapping.getAsString()).thenReturn(blankPrompt);
        when(event.getOption("prompt")).thenReturn(mapping);

        command.handle(event);

        verify(event).reply("⚠️ Please provide a non-empty prompt.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Rejects null option mapping with ephemeral error message")
    void rejectsNullPromptWithEphemeralError() {
        when(event.getOption("prompt")).thenReturn(null);

        command.handle(event);

        verify(event).reply("⚠️ Please provide a non-empty prompt.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Echoes special unicode characters and markdown without escaping failure")
    void echoesSpecialUnicodeCharactersAndMarkdown() {
        String complexPrompt = "🤖 **Hello** `code` ☁️ こんにちは \uD83D\uDE80";
        OptionMapping mapping = mock(OptionMapping.class);
        when(mapping.getAsString()).thenReturn(complexPrompt);
        when(event.getOption("prompt")).thenReturn(mapping);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        assertTrue(captor.getValue().contains(complexPrompt));
    }

    @Test
    @DisplayName("Truncates excessively long prompt to stay within Discord 2000 character limit")
    void truncatesExcessivelyLongPromptToStayWithinDiscordLimit() {
        String veryLongPrompt = "x".repeat(2500);
        OptionMapping mapping = mock(OptionMapping.class);
        when(mapping.getAsString()).thenReturn(veryLongPrompt);
        when(event.getOption("prompt")).thenReturn(mapping);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();

        String message = captor.getValue();
        assertTrue(message.length() <= 2000, "Message length must stay within 2000 characters");
        assertTrue(message.contains("..."));
    }
}
