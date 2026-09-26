package com.ardelys.hbansystem.command;

import com.ardelys.hbansystem.auth.AuthorizationService;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.model.DurationResult;
import com.ardelys.hbansystem.util.DurationParser;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class BaseCommand implements CommandExecutor, TabCompleter {

    protected final ConfigManager configManager;
    protected AuthorizationService authService;
    private final String permissionKey;
    private final String defaultPermission;

    public BaseCommand(
            @NotNull ConfigManager configManager,
            @NotNull String permissionKey,
            @NotNull String defaultPermission,
            @Nullable AuthorizationService authService
    ) {
        this.configManager = configManager;
        this.permissionKey = permissionKey;
        this.defaultPermission = defaultPermission;
        this.authService = authService;
    }

    public BaseCommand(@NotNull ConfigManager configManager, @NotNull String permissionKey, @NotNull String defaultPermission) {
        this(configManager, permissionKey, defaultPermission, null);
    }

    public void setAuthorizationService(@Nullable AuthorizationService authService) {
        this.authService = authService;
    }

    @Nullable
    public AuthorizationService getAuthorizationService() {
        return authService;
    }

    public String getPermission() {
        return configManager.getSettings().getPermission(permissionKey, defaultPermission);
    }

    public boolean hasPermission(@NotNull CommandSender sender) {
        if (authService != null) {
            return authService.hasPermission(sender, getPermission());
        }
        if (sender instanceof ConsoleCommandSender || sender.equals(Bukkit.getConsoleSender())) {
            return true;
        }
        String perm = getPermission();
        if (perm.isEmpty() || sender.hasPermission("hban.admin") || sender.hasPermission("abans.admin")) {
            return true;
        }
        if (sender.hasPermission(perm)) {
            return true;
        }
        if (perm.startsWith("hban.")) {
            String legacyPerm = "abans." + perm.substring(5);
            return sender.hasPermission(legacyPerm);
        }
        return false;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (authService != null) {
            if (!authService.checkPermission(sender, getPermission())) {
                return true;
            }
        } else {
            if (!hasPermission(sender)) {
                configManager.getMessages().sendPrefixed(
                        sender,
                        "general.no-permission",
                        "<red>Bu işlemi gerçekleştirmek için yetkiniz bulunmuyor."
                );
                return true;
            }
        }

        execute(sender, label, args);
        return true;
    }

    public abstract void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args);

    
    protected ParsedArgs stripSilentFlag(@NotNull String[] args) {
        String silentFlag = configManager.getSettings().silentFlag();
        List<String> list = new ArrayList<>();
        boolean silent = false;

        for (String arg : args) {
            if (arg.equalsIgnoreCase(silentFlag)) {
                silent = true;
            } else {
                list.add(arg);
            }
        }

        return new ParsedArgs(list.toArray(new String[0]), silent);
    }

    protected record ParsedArgs(String[] args, boolean silent) {}

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }
        return tabComplete(sender, alias, args);
    }

    protected List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(p.getName());
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }

    protected List<String> completeDurations(@NotNull String input) {
        List<String> suggestions = List.of("30m", "1h", "12h", "1d", "3d", "7d", "30d", "perm");
        String lower = input.toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(s -> s.startsWith(lower)).toList();
    }
}
