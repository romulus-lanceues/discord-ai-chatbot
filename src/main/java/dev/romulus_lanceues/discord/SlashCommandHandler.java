package dev.romulus_lanceues.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface SlashCommandHandler {

    String name();

    SlashCommandData commandData();

    void handle(SlashCommandInteractionEvent event);
}
