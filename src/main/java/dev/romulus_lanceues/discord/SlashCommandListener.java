package dev.romulus_lanceues.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SlashCommandListener extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(SlashCommandListener.class);

    private final Map<String, SlashCommandHandler> handlers;
    private final List<SlashCommandHandler> handlerList;

    public SlashCommandListener(List<SlashCommandHandler> commandHandlers) {
        this.handlerList = List.copyOf(commandHandlers);
        Map<String, SlashCommandHandler> map = new LinkedHashMap<>();
        for (SlashCommandHandler handler : commandHandlers) {
            map.put(handler.name(), handler);
        }
        this.handlers = Map.copyOf(map);
    }

    public List<SlashCommandData> getCommandData() {
        return handlerList.stream()
                .map(SlashCommandHandler::commandData)
                .toList();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String name = event.getName();
        SlashCommandHandler handler = name != null ? handlers.get(name) : null;
        if (handler == null) {
            event.reply("Unknown or unsupported command: /" + (name != null ? name : "unknown"))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            handler.handle(event);
        } catch (Exception e) {
            LOG.error("Error executing slash command /{}", name, e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while executing this command.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }
}
