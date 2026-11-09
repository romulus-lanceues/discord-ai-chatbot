package dev.romulus_lanceues.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CloudCommandTest {

    private CloudCommand command;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        command = new CloudCommand();
        event = mock(SlashCommandInteractionEvent.class);
        replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
    }

    @Test
    @DisplayName("Has correct command name, description, optional topic flag, and predefined choices")
    void hasCorrectNameAndCommandData() {
        assertEquals("cloud", command.name());

        SlashCommandData data = command.commandData();
        assertEquals("cloud", data.getName());
        assertEquals("Learn about Cloud bot, features, and system status.", data.getDescription());
        assertEquals(1, data.getOptions().size());

        OptionData topicOption = data.getOptions().get(0);
        assertEquals("topic", topicOption.getName());
        assertEquals(OptionType.STRING, topicOption.getType());
        assertEquals("Specific topic to view (e.g. about, help, status)", topicOption.getDescription());
        assertFalse(topicOption.isRequired(), "Topic option should be optional");

        List<Command.Choice> choices = topicOption.getChoices();
        assertEquals(3, choices.size());
        assertEquals("about", choices.get(0).getAsString());
        assertEquals("About Cloud bot and its architecture", choices.get(0).getName());

        assertEquals("help", choices.get(1).getAsString());
        assertEquals("How to interact with Cloud", choices.get(1).getName());

        assertEquals("status", choices.get(2).getAsString());
        assertEquals("System status and connectivity", choices.get(2).getName());
    }

    @Test
    @DisplayName("Replies with general info when topic option is null or omitted")
    void repliesWithGeneralInfoWhenTopicOmitted() {
        when(event.getOption("topic")).thenReturn(null);

        command.handle(event);

        verify(event).reply("Hello there! The name is Cloud. If you want to know more about me, kindly pick certain topics on the topic tab.");
        verify(replyAction).queue();
        verify(replyAction, never()).setEphemeral(anyBoolean());
    }

    @ParameterizedTest(name = "Blank topic input: ''{0}''")
    @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n  \t"})
    @DisplayName("Replies with general info when topic option value is blank or only whitespace")
    void repliesWithGeneralInfoWhenTopicIsBlank(String blankTopic) {
        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn(blankTopic);
        when(event.getOption("topic")).thenReturn(topicOption);

        command.handle(event);

        verify(event).reply("Hello there! The name is Cloud. If you want to know more about me, kindly pick certain topics on the topic tab.");
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Replies with topic info for predefined choice 'help'")
    void repliesWithTopicInfoWhenTopicHelpProvided() {
        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("help");
        when(event.getOption("topic")).thenReturn(topicOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();

        String message = captor.getValue();
        assertEquals("To interact with you can use two commands! \"ask\": If you want a quick answer. \"/conversation\": If you want to talk", message);
    }

    @Test
    @DisplayName("Replies with topic info for predefined choice 'about'")
    void repliesWithTopicInfoWhenTopicAboutProvided() {
        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("about");
        when(event.getOption("topic")).thenReturn(topicOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());

        String message = captor.getValue();
        assertEquals("Hi! I'm cloud a discord AI chatbot powered by Gemini", message);
    }

    @Test
    @DisplayName("Replies with topic info for predefined choice 'status'")
    void repliesWithTopicInfoWhenTopicStatusProvided() {
        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("status");
        when(event.getOption("topic")).thenReturn(topicOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());

        String message = captor.getValue();
        assertEquals("Right now I'm still currently under thorough development. Expect some bugs and feel free to provide suggestions on how I can be improved! :)", message);
    }

    @Test
    @DisplayName("Replies with placeholder when given an arbitrary or unknown topic value")
    void repliesWithTopicInfoWhenUnknownTopicProvided() {
        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("architecture");
        when(event.getOption("topic")).thenReturn(topicOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());

        String message = captor.getValue();
        assertEquals("Unknown command", message);
    }

    @Test
    @DisplayName("Strips surrounding whitespace from topic value")
    void stripsWhitespaceFromTopic() {
        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("   help   ");
        when(event.getOption("topic")).thenReturn(topicOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());

        String message = captor.getValue();
        assertEquals("To interact with you can use two commands! \"ask\": If you want a quick answer. \"/conversation\": If you want to talk", message);
    }

    @Test
    @DisplayName("Truncates excessively long topic string safely without exceeding Discord message limit")
    void truncatesExcessivelyLongTopicSafely() {
        String longTopic = "T".repeat(500);
        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn(longTopic);
        when(event.getOption("topic")).thenReturn(topicOption);

        command.handle(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();

        String message = captor.getValue();
        assertTrue(message.length() <= 2000, "Message length must stay within 2000 characters: " + message.length());
        assertEquals("Unknown command", message);
    }
}
