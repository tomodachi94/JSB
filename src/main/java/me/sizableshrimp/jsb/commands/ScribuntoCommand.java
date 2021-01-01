package me.sizableshrimp.jsb.commands;

import com.google.gson.JsonObject;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import me.sizableshrimp.jsb.api.AbstractCommand;
import me.sizableshrimp.jsb.api.CommandContext;
import me.sizableshrimp.jsb.api.CommandInfo;
import me.sizableshrimp.jsb.args.Args;
import me.sizableshrimp.jsb.data.Scribunto;
import org.fastily.jwiki.util.GSONP;
import reactor.core.publisher.Mono;

import java.util.Set;

public class ScribuntoCommand extends AbstractCommand {
    @Override
    public CommandInfo getInfo() {
        return new CommandInfo(this, "%cmdname% <module name> <code>",
                """
                        Allows executing custom code from Scribunto modules.
                        The module name should be the name of the module, with or without the `Module:` namespace.
                        The code should not be wrapped in a code block and should be code that could run in a module's console space.
                        Editor only.
                        """);
    }

    @Override
    public String getName() {
        return "scribunto";
    }

    @Override
    public Set<String> getAliases() {
        return Set.of("scrib", "lua");
    }

    @Override
    public Mono<Message> run(CommandContext context, MessageCreateEvent event, Args args) {
        if (event.getMember().isEmpty())
            return Mono.empty();

        return event.getMember().get().getRoles().any(r -> r.getName().equals("Editor"))
                .filter(b -> b)
                .flatMap(b -> event.getMessage().getChannel())
                .flatMap(channel -> {
                    if (args.getLength() != 2) {
                        return incorrectUsage(event);
                    }
                    String module = args.getArg(0);
                    Scribunto scrib = Scribunto.runScribuntoCode(context.getWiki(), module, args.getArg(1));
                    JsonObject json = scrib.getResponseJson();
                    if (json == null) {
                        return sendMessage(module + " does not exist", channel);
                    }

                    String type = GSONP.getStr(json, "type");
                    if ("normal".equals(type)) {
                        String print = GSONP.getStr(json, "print");
                        String ret = GSONP.getStr(json, "return");

                        StringBuilder builder = new StringBuilder();
                        if (print != null && !print.isEmpty()) {
                            // Print values returned from the API should already have \n appended.
                            builder.append("Logs:\n").append(print);
                        }
                        if (ret != null && !ret.isEmpty()) {
                            builder.append("Returned value: ").append(ret);
                        } else {
                            builder.append("No value was returned from the module.");
                        }
                        return sendMessage("```lua\n" + builder.toString() + "```", channel);
                    } else if ("error".equals(type)) {
                        return sendMessage("An error occurred: " + GSONP.gsonPP.toJson(json), channel);
                    } else if (type == null) {
                        return sendMessage("Type was null: " + GSONP.gsonPP.toJson(json), channel);
                    } else {
                        return sendMessage("Unknown type value \"" + type + "\" for JSON: " + GSONP.gsonPP.toJson(json), channel);
                    }
                }).switchIfEmpty(event.getMessage().getChannel().flatMap(channel -> sendMessage("You must be an editor to execute this command!", channel)));
    }
}
