package com.ardelys.hbansystem.command;

import com.ardelys.hbansystem.auth.AuthorizationService;
import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.command.impl.*;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PlayerDataRepository;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.database.repository.SecurityRepository;
import com.ardelys.hbansystem.hook.LuckPermsHook;
import com.ardelys.hbansystem.hook.discord.DiscordWebhookService;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.manager.security.SecurityManager;
import com.ardelys.hbansystem.model.PunishmentType;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CommandManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PunishmentManager punishmentManager;
    private final PunishmentRepository punishmentRepository;
    private final PlayerDataRepository playerDataRepository;
    private final SecurityRepository securityRepository;
    private final PunishmentCache cache;
    private final SecurityManager securityManager;
    private final LuckPermsHook luckPermsHook;
    private final DiscordWebhookService discordWebhookService;
    private final AuthorizationService authorizationService;

    public CommandManager(
            @NotNull JavaPlugin plugin,
            @NotNull ConfigManager configManager,
            @NotNull PunishmentManager punishmentManager,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull PlayerDataRepository playerDataRepository,
            @NotNull SecurityRepository securityRepository,
            @NotNull PunishmentCache cache,
            @NotNull SecurityManager securityManager,
            @NotNull LuckPermsHook luckPermsHook,
            @NotNull DiscordWebhookService discordWebhookService,
            @Nullable AuthorizationService authorizationService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.punishmentManager = punishmentManager;
        this.punishmentRepository = punishmentRepository;
        this.playerDataRepository = playerDataRepository;
        this.securityRepository = securityRepository;
        this.cache = cache;
        this.securityManager = securityManager;
        this.luckPermsHook = luckPermsHook;
        this.discordWebhookService = discordWebhookService;
        this.authorizationService = authorizationService;
    }

    public CommandManager(
            @NotNull JavaPlugin plugin,
            @NotNull ConfigManager configManager,
            @NotNull PunishmentManager punishmentManager,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull PlayerDataRepository playerDataRepository,
            @NotNull SecurityRepository securityRepository,
            @NotNull PunishmentCache cache,
            @NotNull SecurityManager securityManager,
            @NotNull LuckPermsHook luckPermsHook,
            @NotNull DiscordWebhookService discordWebhookService
    ) {
        this(plugin, configManager, punishmentManager, punishmentRepository, playerDataRepository, securityRepository, cache, securityManager, luckPermsHook, discordWebhookService, null);
    }

    public void registerAll() {
        BanCommand banCmd = new BanCommand(configManager, punishmentManager, cache);
        TempBanCommand tempBanCmd = new TempBanCommand(configManager, punishmentManager, cache);
        UnbanCommand unbanCmd = new UnbanCommand(configManager, punishmentManager, cache);

        MuteCommand muteCmd = new MuteCommand(configManager, punishmentManager, cache);
        TempMuteCommand tempMuteCmd = new TempMuteCommand(configManager, punishmentManager, cache);
        UnmuteCommand unmuteCmd = new UnmuteCommand(configManager, punishmentManager, cache);

        KickCommand kickCmd = new KickCommand(configManager, punishmentManager);
        WarnCommand warnCmd = new WarnCommand(configManager, punishmentManager, cache);
        UnwarnCommand unwarnCmd = new UnwarnCommand(configManager, punishmentManager);

        IpBanCommand ipBanCmd = new IpBanCommand(configManager, punishmentManager, cache);
        TempIpBanCommand tempIpBanCmd = new TempIpBanCommand(configManager, punishmentManager, cache);
        UnIpBanCommand unIpBanCmd = new UnIpBanCommand(configManager, punishmentManager, cache);

        CheckBanCommand checkBanCmd = new CheckBanCommand(configManager, cache);
        CheckMuteCommand checkMuteCmd = new CheckMuteCommand(configManager, cache);
        HistoryCommand historyCmd = new HistoryCommand(configManager, punishmentRepository, cache);

        ListCommands banListCmd = new ListCommands(configManager, punishmentRepository, PunishmentType.BAN, "banlist", "Yasaklama");
        ListCommands muteListCmd = new ListCommands(configManager, punishmentRepository, PunishmentType.MUTE, "mutelist", "Susturma");
        ListCommands warnListCmd = new ListCommands(configManager, punishmentRepository, PunishmentType.WARN, "warnlist", "Uyarı");

        BanInfoCommand banInfoCmd = new BanInfoCommand(configManager, punishmentRepository);
        AltsCommand altsCmd = new AltsCommand(configManager, playerDataRepository, cache);

        HBanCommand hbanCommand = new HBanCommand(
                plugin, configManager, punishmentRepository, playerDataRepository,
                securityRepository, cache, securityManager, luckPermsHook, discordWebhookService
        );
        if (authorizationService != null) {
            hbanCommand.setAuthorizationService(authorizationService);
        }

        hbanCommand.registerSubcommand("ban", banCmd);
        hbanCommand.registerSubcommand("tempban", tempBanCmd);
        hbanCommand.registerSubcommand("unban", unbanCmd);
        hbanCommand.registerSubcommand("mute", muteCmd);
        hbanCommand.registerSubcommand("tempmute", tempMuteCmd);
        hbanCommand.registerSubcommand("unmute", unmuteCmd);
        hbanCommand.registerSubcommand("kick", kickCmd);
        hbanCommand.registerSubcommand("warn", warnCmd);
        hbanCommand.registerSubcommand("unwarn", unwarnCmd);
        hbanCommand.registerSubcommand("ipban", ipBanCmd);
        hbanCommand.registerSubcommand("tempipban", tempIpBanCmd);
        hbanCommand.registerSubcommand("unipban", unIpBanCmd);
        hbanCommand.registerSubcommand("check", checkBanCmd);
        hbanCommand.registerSubcommand("checkban", checkBanCmd);
        hbanCommand.registerSubcommand("checkmute", checkMuteCmd);
        hbanCommand.registerSubcommand("history", historyCmd);
        hbanCommand.registerSubcommand("punishments", historyCmd);
        hbanCommand.registerSubcommand("alts", altsCmd);
        hbanCommand.registerSubcommand("baninfo", banInfoCmd);
        hbanCommand.registerSubcommand("banlist", banListCmd);
        hbanCommand.registerSubcommand("mutelist", muteListCmd);
        hbanCommand.registerSubcommand("warnlist", warnListCmd);

        register("hban", hbanCommand);

        register("ban", banCmd);
        register("tempban", tempBanCmd);
        register("unban", unbanCmd);

        register("mute", muteCmd);
        register("tempmute", tempMuteCmd);
        register("unmute", unmuteCmd);

        register("kick", kickCmd);
        register("warn", warnCmd);
        register("unwarn", unwarnCmd);

        register("ipban", ipBanCmd);
        register("tempipban", tempIpBanCmd);
        register("unipban", unIpBanCmd);

        register("checkban", checkBanCmd);
        register("checkmute", checkMuteCmd);
        register("history", historyCmd);

        register("banlist", banListCmd);
        register("mutelist", muteListCmd);
        register("warnlist", warnListCmd);

        register("baninfo", banInfoCmd);
        register("alts", altsCmd);
    }

    private void register(@NotNull String name, @NotNull BaseCommand command) {
        if (authorizationService != null) {
            command.setAuthorizationService(authorizationService);
        }
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        } else {
            plugin.getLogger().warning("[HBanSystem] Komut tanımlanamadı: " + name);
        }
    }
}
