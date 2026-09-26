package com.ardelys.hbansystem.command.impl;

import com.ardelys.hbansystem.HBanSystem;
import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.command.BaseCommand;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PlayerDataRepository;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.database.repository.SecurityRepository;
import com.ardelys.hbansystem.gui.PunishmentHistoryGui;
import com.ardelys.hbansystem.hook.LuckPermsHook;
import com.ardelys.hbansystem.hook.discord.DiscordWebhookService;
import com.ardelys.hbansystem.manager.security.SecurityManager;
import com.ardelys.hbansystem.model.SecurityEvidence;
import com.ardelys.hbansystem.util.ApiKeyGenerator;
import com.ardelys.hbansystem.util.MessageUtil;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class HBanCommand extends BaseCommand {

    private final JavaPlugin plugin;
    private final PunishmentRepository punishmentRepository;
    private final PlayerDataRepository playerDataRepository;
    private final SecurityRepository securityRepository;
    private final PunishmentCache cache;
    private final SecurityManager securityManager;
    private final LuckPermsHook luckPermsHook;
    private final DiscordWebhookService discordWebhookService;
    private final Map<String, BaseCommand> subcommands = new HashMap<>();

    public HBanCommand(
            @NotNull JavaPlugin plugin,
            @NotNull ConfigManager configManager,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull PlayerDataRepository playerDataRepository,
            @NotNull SecurityRepository securityRepository,
            @NotNull PunishmentCache cache,
            @NotNull SecurityManager securityManager,
            @NotNull LuckPermsHook luckPermsHook,
            @NotNull DiscordWebhookService discordWebhookService
    ) {
        super(configManager, "help", "hban.help");
        this.plugin = plugin;
        this.punishmentRepository = punishmentRepository;
        this.playerDataRepository = playerDataRepository;
        this.securityRepository = securityRepository;
        this.cache = cache;
        this.securityManager = securityManager;
        this.luckPermsHook = luckPermsHook;
        this.discordWebhookService = discordWebhookService;
    }

    @Override
    public boolean hasPermission(@NotNull CommandSender sender) {
        if (authService != null && (authService.isConsole(sender) || authService.hasPermission(sender, "hban.admin") || authService.hasPermission(sender, "hban.help"))) {
            return true;
        }
        for (BaseCommand sub : subcommands.values()) {
            if (sub.hasPermission(sender)) {
                return true;
            }
        }
        return super.hasPermission(sender);
    }

    public void registerSubcommand(@NotNull String name, @NotNull BaseCommand command) {
        this.subcommands.put(name.toLowerCase(Locale.ROOT), command);
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        BaseCommand subCmd = subcommands.get(sub);
        if (subCmd != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            subCmd.onCommand(sender, null, label + " " + sub, subArgs);
            return;
        }

        switch (sub) {
            case "reload" -> {
                if (authService != null && !authService.hasPermission(sender, "hban.reload") && !authService.hasPermission(sender, "hban.admin")) {
                    configManager.getMessages().sendPrefixed(sender, "general.no-permission", "<red>Bu işlemi gerçekleştirmek için yetkiniz bulunmuyor.");
                    return;
                }
                configManager.reload();
                discordWebhookService.reload(configManager.getDiscordConfig());
                if (plugin instanceof HBanSystem main) {
                    if (main.getWebServer() != null) {
                        main.getWebServer().reload();
                    }
                    if (main.getWebSyncService() != null) {
                        main.getWebSyncService().initialize(configManager.getWebConfig());
                    }
                }
                configManager.getMessages().sendPrefixed(sender, "general.reload-success", "<green>HBanSystem yapılandırma dosyaları başarıyla yenilendi.");
            }
            case "version" -> {
                sender.sendMessage(configManager.getMessages().parseComponent(
                        "<gradient:#4facfe:#00f2fe>HBanSystem</gradient> <gray>sürüm: </gray><yellow>" + plugin.getPluginMeta().getVersion() + "</yellow> <gray>(Geliştirici: <gold>Ardelys</gold>)</gray>"
                ));
            }
            case "info" -> {
                if (authService != null && !authService.hasPermission(sender, "hban.info") && !authService.hasPermission(sender, "hban.admin")) {
                    configManager.getMessages().sendPrefixed(sender, "general.no-permission", "<red>Bu işlemi gerçekleştirmek için yetkiniz bulunmuyor.");
                    return;
                }
                sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== HBanSystem Sistem Bilgisi ===</gradient>"));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>Sürüm: <yellow>" + plugin.getPluginMeta().getVersion() + "</yellow>"));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>Geliştirici: <gold>Ardelys</gold>"));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>Veritabanı: <green>" + configManager.getDatabaseConfig().type() + "</green>"));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>Güvenlik Modülü: " + (configManager.getSecuritySettings().enabled() ? "<green>Aktif</green>" : "<red>Devre Dışı</red>")));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>LuckPerms: " + (luckPermsHook.isAvailable() ? "<green>Bağlandı</green>" : "<yellow>Yok</yellow>")));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>PlaceholderAPI: " + (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? "<green>Bağlandı</green>" : "<yellow>Yok</yellow>")));
                boolean webRunning = (plugin instanceof HBanSystem main && main.getWebServer() != null && main.getWebServer().isRunning());
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>Web Entegrasyonu: " + (webRunning ? "<green>Aktif (Port: " + configManager.getWebConfig().port() + ")</green>" : "<red>Devre Dışı</red>")));
            }
            case "debug" -> {
                if (authService != null) {
                    var authRes = authService.canAccessSecurityDebug(sender);
                    if (!authRes.allowed()) {
                        if (authRes.denialMessage() != null) sender.sendMessage(configManager.getMessages().parseComponent(authRes.denialMessage()));
                        return;
                    }
                } else if (!sender.hasPermission("hban.security.debug") && !hasPermission(sender)) {
                    configManager.getMessages().sendPrefixed(sender, "general.no-permission", "<red>Yetkiniz yok.");
                    return;
                }

                if (args.length > 1) {
                    handlePlayerDebug(sender, args[1]);
                } else {
                    handleGeneralDebug(sender);
                }
            }
            case "security" -> {
                if (authService != null) {
                    var authRes = authService.canAccessSecurity(sender);
                    if (!authRes.allowed()) {
                        if (authRes.denialMessage() != null) sender.sendMessage(configManager.getMessages().parseComponent(authRes.denialMessage()));
                        return;
                    }
                } else if (!sender.hasPermission("hban.security") && !sender.hasPermission("hban.security.view") && !hasPermission(sender)) {
                    configManager.getMessages().sendPrefixed(sender, "general.no-permission", "<red>Yetkiniz yok.");
                    return;
                }

                if (args.length < 2) {
                    sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/hban security <oyuncu></gray>"));
                    return;
                }
                handleSecurityAudit(sender, args[1]);
            }
            case "database" -> {
                if (authService != null) {
                    var authRes = authService.canAccessDatabase(sender);
                    if (!authRes.allowed()) {
                        if (authRes.denialMessage() != null) sender.sendMessage(configManager.getMessages().parseComponent(authRes.denialMessage()));
                        return;
                    }
                } else if (!sender.hasPermission("hban.database") && !hasPermission(sender)) {
                    configManager.getMessages().sendPrefixed(sender, "general.no-permission", "<red>Yetkiniz yok.");
                    return;
                }

                if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
                    if (authService != null && !authService.hasPermission(sender, "hban.database.status") && !authService.hasPermission(sender, "hban.database")) {
                        configManager.getMessages().sendPrefixed(sender, "general.no-permission", "<red>Bu işlemi gerçekleştirmek için yetkiniz bulunmuyor.");
                        return;
                    }
                    sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== HBanSystem Veritabanı Durumu ===</gradient>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<gray>Tür: <green>" + configManager.getDatabaseConfig().type() + "</green>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<gray>Kapsam: <yellow>" + configManager.getSettings().serverScope() + "</yellow>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<gray>Havuz Durumu: <aqua>Sağlıklı / Bağlı</aqua>"));
                } else if (args.length > 1 && args[1].equalsIgnoreCase("migrate")) {
                    if (authService != null && !authService.hasPermission(sender, "hban.database.migrate") && !authService.hasPermission(sender, "hban.database")) {
                        configManager.getMessages().sendPrefixed(sender, "general.no-permission", "<red>Bu işlemi gerçekleştirmek için yetkiniz bulunmuyor.");
                        return;
                    }
                    sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Veritabanı tabloları ve şemaları doğrulanıyor...</yellow>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<green>Tüm tablolar (hbans_punishments, hbans_player_data, hbans_security_evidence) başarıyla doğrulandı.</green>"));
                } else {
                    sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/hban database <status|migrate></gray>"));
                }
            }
            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(configManager.getMessages().parseComponent("<red>Bu komut sadece oyuncular tarafından kullanılabilir.</red>"));
                    return;
                }
                if (authService != null && !authService.hasPermission(player, "hban.history") && !authService.hasPermission(player, "hban.punishments") && !authService.hasPermission(player, "hban.admin")) {
                    configManager.getMessages().sendPrefixed(player, "general.no-permission", "<red>Bu işlemi gerçekleştirmek için yetkiniz bulunmuyor.");
                    return;
                }
                String target = (args.length > 1) ? args[1] : player.getName();
                handleGuiOpen(player, target);
            }
            case "apikey", "key" -> {
                if (authService != null && !authService.hasPermission(sender, "hban.admin")) {
                    configManager.getMessages().sendPrefixed(sender, "general.no-permission", "<red>Bu işlemi gerçekleştirmek için yetkiniz bulunmuyor.");
                    return;
                }
                handleApiKey(sender, args);
            }
            default -> sendHelp(sender);
        }
    }

    private void handleApiKey(@NotNull CommandSender sender, @NotNull String[] args) {
        String action = (args.length > 1) ? args[1].toLowerCase(Locale.ROOT) : "view";

        switch (action) {
            case "generate", "gen", "create" -> {
                String newKey = ApiKeyGenerator.generateKey();
                boolean saved = configManager.updateApiKey(newKey);
                if (saved) {
                    if (plugin instanceof HBanSystem main && main.getWebServer() != null) {
                        main.getWebServer().reload();
                    }
                    sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== HBanSystem API Anahtarı Oluşturuldu ===</gradient>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<gray>Yeni API Anahtarı: </gray><aqua><click:copy_to_clipboard:'" + newKey + "'><hover:show_text:'<gray>Panoya kopyalamak için tıklayın'>[" + newKey + "]</hover></click></aqua>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<green>✔ Anahtar başarıyla config.yml dosyasına kaydedildi ve Web API yenilendi.</green>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<gray>Bu anahtarı web sitenizdeki <yellow>bans.php</yellow> dosyasına yapıştırın.</gray>"));
                } else {
                    sender.sendMessage(configManager.getMessages().parseComponent("<red>API anahtarı config.yml dosyasına kaydedilirken bir hata oluştu!</red>"));
                }
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Kullanım: </yellow><gray>/hban apikey set <yeni_anahtar></gray>"));
                    return;
                }
                String customKey = args[2].trim();
                if (customKey.length() < 8) {
                    sender.sendMessage(configManager.getMessages().parseComponent("<red>API anahtarı en az 8 karakter olmalıdır!</red>"));
                    return;
                }
                boolean saved = configManager.updateApiKey(customKey);
                if (saved) {
                    if (plugin instanceof HBanSystem main && main.getWebServer() != null) {
                        main.getWebServer().reload();
                    }
                    sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== HBanSystem API Anahtarı Güncellendi ===</gradient>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<gray>Yeni API Anahtarı: </gray><aqua><click:copy_to_clipboard:'" + customKey + "'><hover:show_text:'<gray>Panoya kopyalamak için tıklayın'>[" + customKey + "]</hover></click></aqua>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<green>✔ Anahtar başarıyla güncellendi.</green>"));
                } else {
                    sender.sendMessage(configManager.getMessages().parseComponent("<red>API anahtarı kaydedilirken bir hata oluştu!</red>"));
                }
            }
            case "view", "show", "info" -> {
                String currentKey = configManager.getWebConfig().apiKey();
                sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== HBanSystem API Anahtarı ===</gradient>"));
                if (currentKey == null || currentKey.isBlank() || "CHANGE_ME".equals(currentKey)) {
                    sender.sendMessage(configManager.getMessages().parseComponent("<yellow>Uyarı: Geçerli bir API anahtarı ayarlanmamış (Varsayılan: CHANGE_ME).</yellow>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<gray>Hemen yeni bir anahtar oluşturmak için: <aqua>/hban apikey generate</aqua></gray>"));
                } else {
                    sender.sendMessage(configManager.getMessages().parseComponent("<gray>Mevcut API Anahtarı: </gray><aqua><click:copy_to_clipboard:'" + currentKey + "'><hover:show_text:'<gray>Panoya kopyalamak için tıklayın'>[" + currentKey + "]</hover></click></aqua>"));
                    sender.sendMessage(configManager.getMessages().parseComponent("<gray>Web Port: <yellow>" + configManager.getWebConfig().port() + "</yellow> | Durum: " + (configManager.getWebConfig().enabled() ? "<green>Aktif</green>" : "<red>Devre Dışı</red>")));
                }
            }
            default -> {
                sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== HBanSystem API Key Yönetimi ===</gradient>"));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban apikey generate </gray><dark_gray>-</dark_gray> <yellow>Rastgele güçlü bir API anahtarı üretir ve kaydeder.</yellow>"));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban apikey view </gray><dark_gray>-</dark_gray> <yellow>Mevcut API anahtarını görüntüler.</yellow>"));
                sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban apikey set <anahtar> </gray><dark_gray>-</dark_gray> <yellow>Özel bir API anahtarı belirler.</yellow>"));
            }
        }
    }

    private void handleSecurityAudit(CommandSender sender, String targetName) {
        UUID uuid = ValidationUtil.parseUuidSafely(targetName);
        if (uuid == null) {
            uuid = cache.getUuidByName(targetName);
        }

        if (uuid == null) {
            configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu bulunamadı.");
            return;
        }

        securityRepository.findByPlayerUuid(uuid).thenAccept(list -> {
            if (list.isEmpty()) {
                sender.sendMessage(configManager.getMessages().parseComponent("<green>" + targetName + " için kaydedilmiş herhangi bir güvenlik tespiti bulunmuyor.</green>"));
                return;
            }

            sender.sendMessage(configManager.getMessages().parseComponent(
                    "<gradient:#4facfe:#00f2fe>=== [HBanSystem Güvenlik] " + targetName + " Denetim Kayıtları (" + list.size() + ") ===</gradient>"
            ));

            for (SecurityEvidence ev : list) {
                sender.sendMessage(configManager.getMessages().parseComponent(
                        "<red>• Tespit:</red> <yellow>" + ev.detectionType() + "</yellow> | <gray>Seviye:</gray> <gold>" + ev.level().getDisplayName() + " (%" + ev.confidenceScore() + ")</gold>"
                ));
                sender.sendMessage(configManager.getMessages().parseComponent(
                        "  <dark_gray>Detay: " + ev.explanation() + " | Eylem: " + ev.actionTaken().getDisplayName() + " | Tarih: " + MessageUtil.formatDate(ev.timestamp()) + "</dark_gray>"
                ));
            }
        });
    }

    private void handleGuiOpen(Player staff, String targetName) {
        UUID uuid = ValidationUtil.parseUuidSafely(targetName);
        if (uuid == null) {
            uuid = cache.getUuidByName(targetName);
        }

        if (uuid == null) {
            Player online = Bukkit.getPlayer(targetName);
            if (online != null) {
                uuid = online.getUniqueId();
                targetName = online.getName();
            }
        }

        if (uuid == null) {
            configManager.getMessages().sendPrefixed(staff, "general.player-not-found", "<red>Oyuncu bulunamadı.");
            return;
        }

        PunishmentHistoryGui gui = new PunishmentHistoryGui(
                configManager, punishmentRepository, playerDataRepository, securityRepository, cache, uuid, targetName
        );
        gui.buildAsync().thenAccept(inv -> Bukkit.getScheduler().runTask(plugin, () -> staff.openInventory(inv)));
    }

    private void handleGeneralDebug(CommandSender sender) {
        sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== HBanSystem Sistem ve Güvenlik Debug ===</gradient>"));
        sender.sendMessage(configManager.getMessages().parseComponent("<gray>Aktif Süreli Ceza Sayısı: <yellow>" + cache.getActiveTemporaryPunishments().size() + "</yellow>"));
        long freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        sender.sendMessage(configManager.getMessages().parseComponent("<gray>JVM Bellek: <aqua>" + (totalMem - freeMem) + "MB / " + totalMem + "MB</aqua>"));

        var engine = securityManager.getEngine();
        int activeContexts = engine.getDetectionManager().getAllActiveContexts().size();
        sender.sendMessage(configManager.getMessages().parseComponent("<gray>Aktif Güvenlik Bağlamı (Oyuncu): <yellow>" + activeContexts + "</yellow>"));
        sender.sendMessage(configManager.getMessages().parseComponent("<gray>Güvenlik Dedektör Sağlık Durumları:</gray>"));

        for (var detector : engine.getAllDetectors()) {
            String healthTag = detector.isHealthy() ? "<green>Sağlıklı</green>" : "<red>Devre Dışı (Hata: " + detector.getErrorCount() + ")</red>";
            long execMicro = detector.getLastExecutionDurationNanos() / 1000;
            sender.sendMessage(configManager.getMessages().parseComponent(
                    "  <gold>• " + detector.getName() + ":</gold> " + healthTag +
                    " <dark_gray>| İşlenen: <aqua>" + detector.getTotalEventsProcessed() + "</aqua> | Tespit: <red>" + detector.getTotalDetections() + "</red> | Son Süre: <gray>" + execMicro + "µs</gray></dark_gray>"
            ));
        }
    }

    private void handlePlayerDebug(CommandSender sender, String targetName) {
        UUID uuid = ValidationUtil.parseUuidSafely(targetName);
        String resolvedName = targetName;
        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            uuid = online.getUniqueId();
            resolvedName = online.getName();
        } else if (uuid == null) {
            uuid = cache.getUuidByName(targetName);
        }

        if (uuid == null) {
            configManager.getMessages().sendPrefixed(sender, "general.player-not-found", "<red>Oyuncu bulunamadı.");
            return;
        }

        var engine = securityManager.getEngine();
        var context = engine.getDetectionManager().getContext(uuid);

        if (context == null) {
            sender.sendMessage(configManager.getMessages().parseComponent(
                    "<yellow>" + resolvedName + "</yellow> <gray>için aktif bir bellek güvenlik oturumu bulunamadı (Oyuncu çevrimdışı olabilir).</gray>"
            ));
            sender.sendMessage(configManager.getMessages().parseComponent(
                    "<dark_gray>Geçmiş veritabanı kayıtları için: </dark_gray><yellow>/hban security " + resolvedName + "</yellow>"
            ));
            return;
        }

        int currentConfidence = engine.getConfidenceManager().calculateConfidence(context);
        var level = engine.getConfidenceManager().getDetectionLevel(currentConfidence);
        long lastActivity = (System.currentTimeMillis() - context.getLastActivityTimestamp()) / 1000;

        sender.sendMessage(configManager.getMessages().parseComponent(
                "<gradient:#4facfe:#00f2fe>=== [HBanSystem Güvenlik Debug] " + context.getPlayerName() + " ===</gradient>"
        ));
        sender.sendMessage(configManager.getMessages().parseComponent(
                "<gray>İstemci Markası (Brand): <aqua>" + context.getClientBrand() + "</aqua>"
        ));
        sender.sendMessage(configManager.getMessages().parseComponent(
                "<gray>Uyumluluk Bayrakları: <yellow>" + (context.getCompatibilityFlags().isEmpty() ? "Yok (Standart)" : String.join(", ", context.getCompatibilityFlags())) + "</yellow>"
        ));
        sender.sendMessage(configManager.getMessages().parseComponent(
                "<gray>Anlık Güven Skoru: <gold>%" + currentConfidence + "</gold> (" + level.getDisplayName() + ")"
        ));
        sender.sendMessage(configManager.getMessages().parseComponent(
                "<gray>Son Aktivite: <aqua>" + lastActivity + "s</aqua> önce <dark_gray>(Aktivitesizlikte güven SAFE seviyesine düşer)</dark_gray>"
        ));

        var evidences = context.getAllEvidences();
        sender.sendMessage(configManager.getMessages().parseComponent(
                "<gray>Aktif Kanıt Arabelleği: <yellow>" + evidences.size() + " / 50</yellow>"
        ));

        if (!evidences.isEmpty()) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>Son Tespit Sinyalleri:</gray>"));
            int count = 0;
            List<com.ardelys.hbansystem.manager.security.DetectionEvidence> list = new ArrayList<>(evidences);
            for (int i = list.size() - 1; i >= 0 && count < 5; i--, count++) {
                var ev = list.get(i);
                long sec = (System.currentTimeMillis() - ev.timestamp()) / 1000;
                sender.sendMessage(configManager.getMessages().parseComponent(
                        "  <red>• [" + ev.detector() + "]</red> <yellow>" + ev.category() + "</yellow> <dark_gray>(Ağırlık: " + ev.weight() + ", Güvenilirlik: " + (int)(ev.reliability() * 100) + "%, " + sec + "s önce)</dark_gray>"
                ));
                sender.sendMessage(configManager.getMessages().parseComponent(
                        "    <dark_gray>Kanıt: " + ev.evidence() + "</dark_gray>"
                ));
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(configManager.getMessages().parseComponent("<gradient:#4facfe:#00f2fe>=== HBanSystem Yönetim ve Moderasyon Komutları ===</gradient>"));

        if (canExecuteSub(sender, "reload", "hban.reload")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban reload </gray><dark_gray>-</dark_gray> <yellow>Ayar dosyalarını yeniler.</yellow>"));
        }
        if (canExecuteSub(sender, "info", "hban.info")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban info </gray><dark_gray>-</dark_gray> <yellow>Sistem ve bağlantı durumunu gösterir.</yellow>"));
        }
        if (canExecuteSub(sender, "debug", "hban.security.debug")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban debug [oyuncu] </gray><dark_gray>-</dark_gray> <yellow>Önbellek, dedektör sağlığı ve oyuncu güvenlik analizini gösterir.</yellow>"));
        }
        if (canExecuteSub(sender, "version", "hban.info")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban version </gray><dark_gray>-</dark_gray> <yellow>Eklenti sürümünü gösterir.</yellow>"));
        }
        if (canExecuteSub(sender, "security", "hban.security.view")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban security <oyuncu> </gray><dark_gray>-</dark_gray> <yellow>Güvenlik ve enjeksiyon kanıtlarını listeler.</yellow>"));
        }
        if (canExecuteSub(sender, "database", "hban.database")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban database <status|migrate> </gray><dark_gray>-</dark_gray> <yellow>Veritabanı bağlantı durumunu veya şemalarını yönetir.</yellow>"));
        }
        if (canExecuteSub(sender, "apikey", "hban.admin")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban apikey <generate|view|set> </gray><dark_gray>-</dark_gray> <yellow>Web API anahtarını yönetir.</yellow>"));
        }
        if (canExecuteSub(sender, "gui", "hban.history")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban gui [oyuncu] </gray><dark_gray>-</dark_gray> <yellow>Yetkili yönetim arayüzünü açar.</yellow>"));
        }
        if (canExecuteSub(sender, "ban", "hban.ban")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban ban <oyuncu> [süre] <sebep> </gray><dark_gray>-</dark_gray> <yellow>Oyuncuyu sunucudan yasaklar.</yellow>"));
        }
        if (canExecuteSub(sender, "tempban", "hban.tempban")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban tempban <oyuncu> <süre> <sebep> </gray><dark_gray>-</dark_gray> <yellow>Oyuncuyu süreli olarak yasaklar.</yellow>"));
        }
        if (canExecuteSub(sender, "unban", "hban.unban")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban unban <oyuncu> [sebep] </gray><dark_gray>-</dark_gray> <yellow>Oyuncunun yasağını kaldırır.</yellow>"));
        }
        if (canExecuteSub(sender, "mute", "hban.mute")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban mute <oyuncu> [süre] <sebep> </gray><dark_gray>-</dark_gray> <yellow>Oyuncuyu susturur.</yellow>"));
        }
        if (canExecuteSub(sender, "tempmute", "hban.tempmute")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban tempmute <oyuncu> <süre> <sebep> </gray><dark_gray>-</dark_gray> <yellow>Oyuncuyu süreli olarak susturur.</yellow>"));
        }
        if (canExecuteSub(sender, "unmute", "hban.unmute")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban unmute <oyuncu> [sebep] </gray><dark_gray>-</dark_gray> <yellow>Oyuncunun susturmasını kaldırır.</yellow>"));
        }
        if (canExecuteSub(sender, "kick", "hban.kick")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban kick <oyuncu> <sebep> </gray><dark_gray>-</dark_gray> <yellow>Oyuncuyu sunucudan atar.</yellow>"));
        }
        if (canExecuteSub(sender, "warn", "hban.warn")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban warn <oyuncu> <sebep> </gray><dark_gray>-</dark_gray> <yellow>Oyuncuya uyarı verir.</yellow>"));
        }
        if (canExecuteSub(sender, "history", "hban.history")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban history <oyuncu> </gray><dark_gray>-</dark_gray> <yellow>Ceza geçmişini görüntüler.</yellow>"));
        }
        if (canExecuteSub(sender, "check", "hban.check")) {
            sender.sendMessage(configManager.getMessages().parseComponent("<gray>/hban check <oyuncu> </gray><dark_gray>-</dark_gray> <yellow>Ceza durumunu kontrol eder.</yellow>"));
        }
    }

    private boolean canExecuteSub(CommandSender sender, String subName, String defaultPerm) {
        if (authService != null) {
            if (authService.isConsole(sender) || authService.hasPermission(sender, "hban.admin")) {
                return true;
            }
            BaseCommand subCmd = subcommands.get(subName);
            if (subCmd != null) {
                return subCmd.hasPermission(sender);
            }
            return authService.hasPermission(sender, defaultPerm);
        }
        return sender.hasPermission(defaultPerm) || sender.hasPermission("hban.admin");
    }

    @Override
    protected List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            Set<String> candidates = new TreeSet<>();
            if (canExecuteSub(sender, "reload", "hban.reload")) candidates.add("reload");
            if (canExecuteSub(sender, "info", "hban.info")) candidates.add("info");
            if (canExecuteSub(sender, "version", "hban.info")) candidates.add("version");
            if (canExecuteSub(sender, "debug", "hban.security.debug")) candidates.add("debug");
            if (canExecuteSub(sender, "security", "hban.security.view")) candidates.add("security");
            if (canExecuteSub(sender, "database", "hban.database")) candidates.add("database");
            if (canExecuteSub(sender, "gui", "hban.history")) candidates.add("gui");
            if (canExecuteSub(sender, "apikey", "hban.admin")) {
                candidates.add("apikey");
                candidates.add("key");
            }

            for (Map.Entry<String, BaseCommand> entry : subcommands.entrySet()) {
                if (entry.getValue().hasPermission(sender)) {
                    candidates.add(entry.getKey());
                }
            }

            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> res = new ArrayList<>();
            for (String s : candidates) {
                if (s.startsWith(p)) res.add(s);
            }
            return res;
        } else if (args.length >= 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            BaseCommand subCmd = subcommands.get(sub);
            if (subCmd != null && subCmd.hasPermission(sender)) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCmd.onTabComplete(sender, null, alias + " " + sub, subArgs);
            }
            if ((sub.equals("security") || sub.equals("gui") || sub.equals("debug")) && canExecuteSub(sender, sub, "hban." + sub)) {
                return super.tabComplete(sender, alias, args);
            }
            if ((sub.equals("apikey") || sub.equals("key")) && canExecuteSub(sender, "apikey", "hban.admin")) {
                if (args.length == 2) {
                    List<String> keySubs = List.of("generate", "view", "set");
                    String p = args[1].toLowerCase(Locale.ROOT);
                    return keySubs.stream().filter(s -> s.startsWith(p)).toList();
                }
            }
            if (sub.equals("database") && canExecuteSub(sender, "database", "hban.database")) {
                if (args.length == 2) {
                    List<String> dbSubs = new ArrayList<>();
                    if (canExecuteSub(sender, "database.status", "hban.database.status")) dbSubs.add("status");
                    if (canExecuteSub(sender, "database.migrate", "hban.database.migrate")) dbSubs.add("migrate");
                    String p = args[1].toLowerCase(Locale.ROOT);
                    return dbSubs.stream().filter(s -> s.startsWith(p)).toList();
                }
            }
        }
        return List.of();
    }
}
