package dev.romulus_lanceues.discord;

import dev.romulus_lanceues.ai.ChatService;
import dev.romulus_lanceues.discord.commands.AskCommand;
import dev.romulus_lanceues.discord.commands.CloudCommand;
import dev.romulus_lanceues.discord.commands.ConversationCommand;
import dev.romulus_lanceues.util.RateLimiter;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CommandRegistrationIntegrationTest {

    // Discord slash command name format: 1-32 lowercase characters, numbers, underscores, hyphens
    private static final Pattern DISCORD_NAME_PATTERN = Pattern.compile("^[a-z0-9_-]{1,32}$");

    private ChatService chatService;
    private RateLimiter rateLimiter;
    private List<SlashCommandHandler> standardHandlers;
    private SlashCommandListener listener;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        rateLimiter = mock(RateLimiter.class);
        standardHandlers = List.of(
                new CloudCommand(),
                new AskCommand(chatService, rateLimiter),
                new ConversationCommand()
        );
        listener = new SlashCommandListener(standardHandlers);

        event = mock(SlashCommandInteractionEvent.class);
        replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    }

    @Test
    @DisplayName("All registered commands conform strictly to Discord naming and description limits")
    void allRegisteredCommandsConformToDiscordConstraints() {
        List<SlashCommandData> commandDataList = listener.getCommandData();
        assertEquals(3, commandDataList.size());

        Set<String> commandNames = new HashSet<>();

        for (SlashCommandData data : commandDataList) {
            String name = data.getName();
            // Name uniqueness
            assertTrue(commandNames.add(name), "Duplicate command name found: " + name);

            // Name pattern: 1-32 lowercase characters
            assertTrue(DISCORD_NAME_PATTERN.matcher(name).matches(),
                    "Command name does not match Discord pattern: " + name);

            // Description: 1-100 characters
            String description = data.getDescription();
            assertNotNull(description);
            assertFalse(description.isBlank());
            assertTrue(description.length() <= 100, "Description exceeds 100 characters: " + description);

            // Options validation
            for (OptionData option : data.getOptions()) {
                String optName = option.getName();
                assertTrue(DISCORD_NAME_PATTERN.matcher(optName).matches(),
                        "Option name does not match Discord pattern: " + optName);
                String optDesc = option.getDescription();
                assertNotNull(optDesc);
                assertFalse(optDesc.isBlank());
                assertTrue(optDesc.length() <= 100, "Option description exceeds 100 characters: " + optDesc);

                for (Command.Choice choice : option.getChoices()) {
                    String choiceName = choice.getName();
                    String choiceVal = choice.getAsString();
                    assertTrue(choiceName.length() >= 1 && choiceName.length() <= 100,
                            "Choice name must be 1-100 chars: " + choiceName);
                    assertTrue(choiceVal.length() >= 1 && choiceVal.length() <= 100,
                            "Choice value must be 1-100 chars: " + choiceVal);
                }
            }

            // Subcommands validation
            for (SubcommandData subcommand : data.getSubcommands()) {
                String subName = subcommand.getName();
                assertTrue(DISCORD_NAME_PATTERN.matcher(subName).matches(),
                        "Subcommand name does not match Discord pattern: " + subName);
                String subDesc = subcommand.getDescription();
                assertNotNull(subDesc);
                assertFalse(subDesc.isBlank());
                assertTrue(subDesc.length() <= 100, "Subcommand description exceeds 100 characters: " + subDesc);
            }
        }
    }

    @Test
    @DisplayName("Routes /cloud command interaction through listener successfully")
    void routesCloudCommandThroughListener() {
        when(event.getName()).thenReturn("cloud");
        when(event.getOption("topic")).thenReturn(null);

        listener.onSlashCommandInteraction(event);

        verify(event).reply("Hello there! The name is Cloud. If you want to know more about me, kindly pick certain topics on the topic tab.");
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Routes /ask command interaction through listener successfully")
    void routesAskCommandThroughListener() {
        when(event.getName()).thenReturn("ask");
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("What is Cloud?");
        when(event.getOption("prompt")).thenReturn(promptOption);

        User user = mock(User.class);
        when(event.getUser()).thenReturn(user);
        when(user.getIdLong()).thenReturn(12345L);
        when(rateLimiter.checkCooldown(12345L)).thenReturn(Optional.empty());

        ReplyCallbackAction deferAction = mock(ReplyCallbackAction.class);
        when(event.deferReply()).thenReturn(deferAction);

        InteractionHook hook = mock(InteractionHook.class);
        when(event.getHook()).thenReturn(hook);
        WebhookMessageEditAction editAction = mock(WebhookMessageEditAction.class);
        when(hook.editOriginal(anyString())).thenReturn(editAction);
        when(editAction.setAllowedMentions(any())).thenReturn(editAction);

        when(chatService.ask("What is Cloud?"))
                .thenReturn(CompletableFuture.completedFuture("Cloud is an AI assistant."));

        listener.onSlashCommandInteraction(event);

        verify(event.deferReply()).queue();
        verify(chatService).ask("What is Cloud?");
        verify(hook).editOriginal("Cloud is an AI assistant.");
    }

    @Test
    @DisplayName("Routes /conversation command interaction through listener successfully")
    void routesConversationCommandThroughListener() {
        when(event.getName()).thenReturn("conversation");
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("Start conversation");
        when(event.getOption("prompt")).thenReturn(promptOption);

        listener.onSlashCommandInteraction(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(captor.capture());
        verify(replyAction).queue();
        assertTrue(captor.getValue().contains("Start conversation"));
    }

    @Test
    @DisplayName("Routes unregistered slash command to ephemeral warning message")
    void routesUnregisteredCommandToEphemeralWarning() {
        when(event.getName()).thenReturn("nonexistent");

        listener.onSlashCommandInteraction(event);

        verify(event).reply("Unknown or unsupported command: /nonexistent");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Routes /cloud command with topic option through listener successfully")
    void routesCloudCommandWithTopicOptionThroughListener() {
        when(event.getName()).thenReturn("cloud");
        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("about");
        when(event.getOption("topic")).thenReturn(topicOption);

        listener.onSlashCommandInteraction(event);

        verify(event).reply("Hi! I'm cloud a discord AI chatbot powered by Gemini");
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Routes /ask command when user is on cooldown through listener successfully")
    void routesAskCommandInCooldownThroughListener() {
        when(event.getName()).thenReturn("ask");
        OptionMapping promptOption = mock(OptionMapping.class);
        when(promptOption.getAsString()).thenReturn("Can I ask now?");
        when(event.getOption("prompt")).thenReturn(promptOption);

        User user = mock(User.class);
        when(event.getUser()).thenReturn(user);
        when(user.getIdLong()).thenReturn(54321L);
        when(rateLimiter.checkCooldown(54321L)).thenReturn(Optional.of(java.time.Duration.ofSeconds(3)));

        listener.onSlashCommandInteraction(event);

        verify(event).reply("⏳ Please wait 3 seconds before asking another question.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(event, never()).deferReply();
        verifyNoInteractions(chatService);
    }
}
