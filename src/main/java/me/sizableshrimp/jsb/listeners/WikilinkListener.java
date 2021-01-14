/*
 * Copyright (c) 2021 SizableShrimp
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.sizableshrimp.jsb.listeners;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import me.sizableshrimp.jsb.Bot;
import me.sizableshrimp.jsb.api.EventListener;
import me.sizableshrimp.jsb.commands.utility.WikilinkCommand;
import me.sizableshrimp.jsb.util.MessageUtil;
import org.fastily.jwiki.core.Wiki;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikilinkListener extends EventListener<MessageCreateEvent> {
    // # is not legal, but we need to support section links
    private static final String LEGAL_CHARS = "[ #%!\"$&'()*,\\-./0-9:;=?@A-Z\\\\^_`a-z~\\x80-\\xFF+\\w]";
    private static final Pattern WIKILINK = Pattern.compile("\\[\\[(" + LEGAL_CHARS + "+)(?:\\|(.+))?]]", Pattern.UNICODE_CHARACTER_CLASS);
    static final Set<Snowflake> wikilinkMessages = new HashSet<>();

    public WikilinkListener(GatewayDiscordClient client, Wiki wiki) {
        super(MessageCreateEvent.class, client, wiki);
    }

    private Mono<Message> genMessage(MessageCreateEvent event) {
        return Mono.just(event.getMessage().getContent())
                .map(WIKILINK::matcher)
                .filter(Matcher::find)
                .flatMap(m -> event.getMessage().getChannel().flatMap(MessageChannel::type).thenReturn(m))
                .map(matcher -> {
                    StringBuilder builder = new StringBuilder();

                    do {
                        WikilinkCommand.genWikilink(builder, wiki, matcher.group(1));
                    } while (matcher.find());

                    return builder.toString();
                }).zipWith(event.getMessage().getChannel())
                .flatMap(tuple -> MessageUtil.sendMessage(tuple.getT1(), tuple.getT2()))
                .flatMap(message -> message.addReaction(ReactionEmoji.unicode("🗑️")).thenReturn(message))
                .doOnNext(message -> wikilinkMessages.add(message.getId()));
    }

    @Override
    protected Mono<Void> execute(Flux<MessageCreateEvent> onEvent) {
        return onEvent
                .filterWhen(e -> e.getMessage().getChannel().map(c -> c instanceof GuildMessageChannel))
                .filterWhen(e -> MessageUtil.canSendMessages(e.getMessage()))
                .filter(e -> e.getMessage().getAuthor().map(u -> !u.isBot()).orElse(false))
                .filter(e -> !e.getMessage().getContent().isEmpty())
                .flatMap(this::genMessage)
                .onErrorContinue((error, event) -> Bot.LOGGER.error("Wikilink listener had an uncaught exception!", error))
                .then();
    }
}
