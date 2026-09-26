package com.ardelys.hbansystem.config;

import com.ardelys.hbansystem.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Messages {

    private final FileConfiguration config;
    private final String prefix;

    public Messages(@NotNull FileConfiguration config) {
        this.config = config;
        this.prefix = config.getString("prefix", "&8[&6HBanSystem&8] ");
    }

    @NotNull
    public String getPrefix() {
        return prefix;
    }

    @NotNull
    public String applyPrefix(@NotNull String template, boolean withPrefix) {
        if (template.contains("%prefix%")) {
            String p = prefix.isEmpty() ? "" : (prefix.endsWith(" ") ? prefix : prefix + " ");
            return template.replace("%prefix%", p);
        }
        if (withPrefix && !prefix.isEmpty()) {
            String p = prefix.endsWith(" ") ? prefix : prefix + " ";
            return p + template;
        }
        return template;
    }

    @NotNull
    public String getRaw(@NotNull String path, @NotNull String def) {
        return config.getString(path, def);
    }

    @NotNull
    public String getPrefixed(@NotNull String path, @NotNull String def) {
        String msg = getRaw(path, def);
        return applyPrefix(msg, true);
    }

    @NotNull
    public String getString(@NotNull String path, @NotNull String def) {
        return getPrefixed(path, def);
    }

    @NotNull
    public Component getComponent(@NotNull String path, @NotNull String def, @NotNull Map<String, String> placeholders, boolean withPrefix) {
        String template = applyPrefix(getRaw(path, def), withPrefix);
        return MessageUtil.parseWithPlaceholders(template, placeholders);
    }

    @NotNull
    public Component getComponent(@NotNull String path, @NotNull String def) {
        return getComponent(path, def, Collections.emptyMap(), false);
    }

    @NotNull
    public Component getPrefixedComponent(@NotNull String path, @NotNull String def) {
        return getComponent(path, def, Collections.emptyMap(), true);
    }

    @NotNull
    public Component parseComponent(@NotNull String input) {
        return MessageUtil.parse(input);
    }

    public void send(@NotNull CommandSender sender, @NotNull String path, @NotNull String def, @NotNull Map<String, String> placeholders, boolean withPrefix) {
        Component comp = getComponent(path, def, placeholders, withPrefix);
        sender.sendMessage(comp);
    }

    public void sendPrefixed(@NotNull CommandSender sender, @NotNull String path, @NotNull String def, @NotNull Map<String, String> placeholders) {
        send(sender, path, def, placeholders, true);
    }

    public void sendPrefixed(@NotNull CommandSender sender, @NotNull String path, @NotNull String def) {
        send(sender, path, def, Collections.emptyMap(), true);
    }

    public void sendRaw(@NotNull CommandSender sender, @NotNull String path, @NotNull String def) {
        send(sender, path, def, Collections.emptyMap(), false);
    }
}
