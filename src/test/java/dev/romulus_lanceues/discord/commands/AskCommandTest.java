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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AskCommandTest {

    private AskCommand command;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        command = new AskCommand();
        event = mock(SlashCommandInteractionEvent.class);
        replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
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

    @Test
    @DisplayName("Replies with placeholder echoing valid prompt and does not set ephemeral")
    void repliesWithPlaceholderEchoingPrompt() {
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("What is Java?");
        when(event.getOption("prompt")).thenReturn(promptOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();
        verify(replyAction, never()).setEphemeral(anyBoolean());

        String message = captor.getValue();
        assertTrue(message.contains("What is Java?"));
        assertTrue(message.contains("Gemini AI generation will be connected here"));
    }

    @Test
    @DisplayName("Strips surrounding whitespace from prompt before echoing")
    void stripsSurroundingWhitespaceFromPrompt() {
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("   Tell me a fun fact   ");
        when(event.getOption("prompt")).thenReturn(promptOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());

        String message = captor.getValue();
        assertTrue(message.contains("\"Tell me a fun fact\""));
        assertFalse(message.contains("\"   Tell me a fun fact   \""));
    }

    @ParameterizedTest(name = "Rejects invalid whitespace prompt: {0}")
    @ValueSource(strings = {
            "",
            "   ",
            "\t",
            "\n",
            "\r\n\t  \n",
            "\u2000\u2003\u3000" // Unicode whitespace (en quad, em space, ideographic space)
    })
    @DisplayName("Rejects blank prompts consisting only of spaces, tabs, or newlines with ephemeral warning")
    void rejectsBlankPromptWithEphemeralError(String invalidPrompt) {
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn(invalidPrompt);
        when(event.getOption("prompt")).thenReturn(promptOption);

        command.handle(event);

        verify(event).reply("⚠️ Please provide a non-empty prompt.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Rejects null prompt option with ephemeral error")
    void rejectsNullPromptWithEphemeralError() {
        when(event.getOption("prompt")).thenReturn(null);

        command.handle(event);

        verify(event).reply("⚠️ Please provide a non-empty prompt.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Echoes special Unicode characters, emojis, and markdown correctly")
    void echoesSpecialUnicodeCharactersAndMarkdown() {
        String unicodePrompt = "🤖 AI & Cloud ☁️: 日本語 (Japanese) مرحبا (Arabic) **bold** `code`";
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn(unicodePrompt);
        when(event.getOption("prompt")).thenReturn(promptOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();

        String message = captor.getValue();
        assertTrue(message.contains(unicodePrompt));
    }

    @Test
    @DisplayName("Safely truncates prompt at max length so Discord message does not exceed 2000 chars")
    void truncatesExcessivelyLongPromptToStayWithinDiscordLimit() {
        String longPrompt = "A".repeat(2500);
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn(longPrompt);
        when(event.getOption("prompt")).thenReturn(promptOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();

        String message = captor.getValue();
        assertTrue(message.length() <= 2000, "Message length must not exceed Discord's 2000 char limit: " + message.length());
        assertTrue(message.contains("..."));
    }

    @Test
    @DisplayName("Handles maximum Discord slash command option length (6000 chars) safely")
    void handlesMaxDiscordOptionLengthSafely() {
        String maxOptionPrompt = "X".repeat(6000);
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn(maxOptionPrompt);
        when(event.getOption("prompt")).thenReturn(promptOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();

        String message = captor.getValue();
        assertTrue(message.length() <= 2000, "Reply must stay within 2000 characters: " + message.length());
        assertTrue(message.contains("..."));
    }
}
