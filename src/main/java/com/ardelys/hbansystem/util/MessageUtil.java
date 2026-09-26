package com.ardelys.hbansystem.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .build();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private MessageUtil() {}

    @NotNull
    public static String convertLegacyToMiniMessage(@NotNull String input) {
        if (input.isEmpty()) return input;
        String result = input.replaceAll("(?i)&?#([0-9a-f]{6})", "<#$1>")
                             .replaceAll("(?i)§#([0-9a-f]{6})", "<#$1>");

        return result
                .replace("&0", "<black>").replace("§0", "<black>")
                .replace("&1", "<dark_blue>").replace("§1", "<dark_blue>")
                .replace("&2", "<dark_green>").replace("§2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("§3", "<dark_aqua>")
                .replace("&4", "<dark_red>").replace("§4", "<dark_red>")
                .replace("&5", "<dark_purple>").replace("§5", "<dark_purple>")
                .replace("&6", "<gold>").replace("§6", "<gold>")
                .replace("&7", "<gray>").replace("§7", "<gray>")
                .replace("&8", "<dark_gray>").replace("§8", "<dark_gray>")
                .replace("&9", "<blue>").replace("§9", "<blue>")
                .replace("&a", "<green>").replace("§a", "<green>")
                .replace("&b", "<aqua>").replace("§b", "<aqua>")
                .replace("&c", "<red>").replace("§c", "<red>")
                .replace("&d", "<light_purple>").replace("§d", "<light_purple>")
                .replace("&e", "<yellow>").replace("§e", "<yellow>")
                .replace("&f", "<white>").replace("§f", "<white>")
                .replace("&k", "<obfuscated>").replace("§k", "<obfuscated>")
                .replace("&l", "<bold>").replace("§l", "<bold>")
                .replace("&m", "<strikethrough>").replace("§m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("§n", "<underlined>")
                .replace("&o", "<italic>").replace("§o", "<italic>")
                .replace("&r", "<reset>").replace("§r", "<reset>");
    }

    @NotNull
    public static Component parse(@NotNull String input) {
        if (input.isEmpty()) {
            return Component.empty();
        }
        String converted = convertLegacyToMiniMessage(input);
        try {
            return MINI_MESSAGE.deserialize(converted);
        } catch (Exception e) {
            return Component.text(input);
        }
    }

    @NotNull
    public static Component parseWithPlaceholders(@NotNull String input, @NotNull Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String val = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace("{" + entry.getKey() + "}", val);
            result = result.replace("%" + entry.getKey() + "%", val);
        }
        return parse(result);
    }

    public static void sendMessage(@NotNull CommandSender sender, @NotNull String message) {
        if (!message.isBlank()) {
            sender.sendMessage(parse(message));
        }
    }

    public static void sendMessage(@NotNull CommandSender sender, @NotNull String message, @NotNull Map<String, String> placeholders) {
        if (!message.isBlank()) {
            sender.sendMessage(parseWithPlaceholders(message, placeholders));
        }
    }

    @NotNull
    public static String formatDate(long timestampMillis) {
        if (timestampMillis <= 0) {
            return "Yok";
        }
        return DATE_FORMATTER.format(Instant.ofEpochMilli(timestampMillis));
    }
}
