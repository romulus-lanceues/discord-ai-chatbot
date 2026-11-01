package dev.romulus_lanceues.discord.commands;

import dev.romulus_lanceues.discord.SlashCommandHandler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public class CloudCommand implements SlashCommandHandler {

    private static final String NAME = "cloud";
    private static final String DESCRIPTION = "Learn about Cloud bot, features, and system status.";
    public static final String OPTION_TOPIC = "topic";

    public static final String CHOICE_ABOUT = "about";
    public static final String CHOICE_HELP = "help";
    public static final String CHOICE_STATUS = "status";

    static final int MAX_TOPIC_PREVIEW_LENGTH = 100;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SlashCommandData commandData() {
        return Commands.slash(NAME, DESCRIPTION)
                    .addOptions(new OptionData(OptionType.STRING, OPTION_TOPIC, "Specific topic to view (e.g. about, help, status)", false)
                        .addChoice("About Cloud bot and its architecture", CHOICE_ABOUT)
                        .addChoice("How to interact with Cloud", CHOICE_HELP)
                        .addChoice("System status and connectivity", CHOICE_STATUS));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        OptionMapping topicOption = event.getOption(OPTION_TOPIC);
        if (topicOption == null || topicOption.getAsString().isBlank()) {
            event.reply("Hello there! The name is Cloud. If you want to know more about me, kindly pick certain topics on the topic tab.")
                    .queue();
        } else {
            String topic = topicOption.getAsString().strip();
            String displayTopic = selectTopic(topic);
            event.reply(displayTopic).queue();
        }
    }

    public String selectTopic(String chosenTopic){

        return switch (chosenTopic){
            case CHOICE_ABOUT ->  "Hi! I'm cloud a discord AI chatbot powered by Gemini";
            case CHOICE_HELP ->  "To interact with you can use two commands! \"ask\": If you want a quick answer. \"/conversation\": If you want to talk";
            case CHOICE_STATUS -> "Right now I'm still currently under thorough development. Expect some bugs and feel free to provide suggestions on how I can be improved! :)";
            default -> "Unknown command";
        };

    }
}
