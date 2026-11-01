package dev.romulus_lanceues.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SlashCommandListenerTest {

    private SlashCommandHandler handlerA;
    private SlashCommandHandler handlerB;
    private SlashCommandListener listener;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        handlerA = mock(SlashCommandHandler.class);
        when(handlerA.name()).thenReturn("test-a");
        when(handlerA.commandData()).thenReturn(Commands.slash("test-a", "Description A"));

        handlerB = mock(SlashCommandHandler.class);
        when(handlerB.name()).thenReturn("test-b");
        when(handlerB.commandData()).thenReturn(Commands.slash("test-b", "Description B"));

        listener = new SlashCommandListener(List.of(handlerA, handlerB));

        event = mock(SlashCommandInteractionEvent.class);
        replyAction = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    }

    @Test
    @DisplayName("Exposes CommandData for all registered handlers in order")
    void exposesCommandDataList() {
        List<SlashCommandData> commandDataList = listener.getCommandData();
        assertEquals(2, commandDataList.size());
        assertEquals("test-a", commandDataList.get(0).getName());
        assertEquals("test-b", commandDataList.get(1).getName());
    }

    @Test
    @DisplayName("Handles empty handler list cleanly")
    void handlesEmptyHandlerList() {
        SlashCommandListener emptyListener = new SlashCommandListener(List.of());
        assertTrue(emptyListener.getCommandData().isEmpty());

        when(event.getName()).thenReturn("test-a");
        emptyListener.onSlashCommandInteraction(event);

        verify(event).reply("Unknown or unsupported command: /test-a");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Creates defensive copy so external mutations do not affect listener")
    void defensiveCopyPreventsExternalModification() {
        List<SlashCommandHandler> mutableList = new ArrayList<>();
        mutableList.add(handlerA);

        SlashCommandListener copyListener = new SlashCommandListener(mutableList);
        assertEquals(1, copyListener.getCommandData().size());

        mutableList.add(handlerB);
        assertEquals(1, copyListener.getCommandData().size());
    }

    @Test
    @DisplayName("Routes known command to its respective handler without replying in listener")
    void routesKnownCommandToHandler() {
        when(event.getName()).thenReturn("test-a");

        listener.onSlashCommandInteraction(event);

        verify(handlerA).handle(event);
        verify(handlerB, never()).handle(any());
        verify(event, never()).reply(anyString());
    }

    @Test
    @DisplayName("Replies ephemerally when command is not recognized")
    void repliesEphemerallyOnUnknownCommand() {
        when(event.getName()).thenReturn("unknown-cmd");

        listener.onSlashCommandInteraction(event);

        verify(event).reply("Unknown or unsupported command: /unknown-cmd");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(handlerA, never()).handle(any());
        verify(handlerB, never()).handle(any());
    }

    @Test
    @DisplayName("Replies ephemerally and safely when event command name is null")
    void repliesEphemerallyWhenCommandNameIsNull() {
        when(event.getName()).thenReturn(null);

        listener.onSlashCommandInteraction(event);

        verify(event).reply("Unknown or unsupported command: /unknown");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(handlerA, never()).handle(any());
        verify(handlerB, never()).handle(any());
    }

    @Test
    @DisplayName("Catches handler exception and replies friendly error message when unacknowledged")
    void catchesHandlerExceptionAndRepliesFriendlyMessageWhenUnacknowledged() {
        when(event.getName()).thenReturn("test-a");
        when(event.isAcknowledged()).thenReturn(false);
        doThrow(new RuntimeException("Simulated failure")).when(handlerA).handle(event);

        listener.onSlashCommandInteraction(event);

        verify(event).reply("An error occurred while executing this command.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Catches handler exception and does not attempt double reply when already acknowledged")
    void catchesHandlerExceptionAndDoesNotReplyIfAlreadyAcknowledged() {
        when(event.getName()).thenReturn("test-a");
        when(event.isAcknowledged()).thenReturn(true);
        doThrow(new IllegalStateException("Already acknowledged or deferred")).when(handlerA).handle(event);

        listener.onSlashCommandInteraction(event);

        verify(event, never()).reply(anyString());
        verify(replyAction, never()).queue();
    }
}
