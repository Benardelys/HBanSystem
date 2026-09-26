package com.ardelys.hbansystem.web;

import com.ardelys.hbansystem.web.dto.PunishmentDto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PublicBansPage {

    public static String render(
            @NotNull List<PunishmentDto> bans,
            @NotNull List<PunishmentDto> mutes,
            int currentBanPage,
            int totalBanPages,
            int currentMutePage,
            int totalMutePages,
            @Nullable String search,
            @NotNull String serverName
    ) {
        String safeSearch = search != null ? escapeHtml(search) : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"tr\" data-theme=\"dark\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(escapeHtml(serverName)).append(" - Ceza Listesi (Bans & Mutes)</title>\n");
        sb.append("<style>\n");
        sb.append(":root {\n");
        sb.append("  --bg-primary: #0f172a;\n");
        sb.append("  --bg-card: #1e293b;\n");
        sb.append("  --bg-input: #334155;\n");
        sb.append("  --text-main: #f8fafc;\n");
        sb.append("  --text-muted: #94a3b8;\n");
        sb.append("  --primary: #3b82f6;\n");
        sb.append("  --primary-hover: #2563eb;\n");
        sb.append("  --accent-red: #ef4444;\n");
        sb.append("  --accent-amber: #f59e0b;\n");
        sb.append("  --accent-green: #10b981;\n");
        sb.append("  --border-color: #334155;\n");
        sb.append("}\n");
        sb.append("[data-theme=\"light\"] {\n");
        sb.append("  --bg-primary: #f1f5f9;\n");
        sb.append("  --bg-card: #ffffff;\n");
        sb.append("  --bg-input: #e2e8f0;\n");
        sb.append("  --text-main: #0f172a;\n");
        sb.append("  --text-muted: #64748b;\n");
        sb.append("  --primary: #2563eb;\n");
        sb.append("  --primary-hover: #1d4ed8;\n");
        sb.append("  --border-color: #cbd5e1;\n");
        sb.append("}\n");
        sb.append("* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; transition: background-color 0.2s, color 0.2s; }\n");
        sb.append("body { background-color: var(--bg-primary); color: var(--text-main); min-height: 100vh; padding: 24px 16px; display: flex; flex-direction: column; align-items: center; }\n");
        sb.append(".container { width: 100%; max-width: 1200px; }\n");
        sb.append(".header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 28px; padding-bottom: 16px; border-bottom: 1px solid var(--border-color); }\n");
        sb.append(".logo-title { display: flex; align-items: center; gap: 12px; }\n");
        sb.append(".logo-badge { background: linear-gradient(135deg, #3b82f6, #06b6d4); color: #fff; font-weight: bold; font-size: 1.2rem; padding: 8px 14px; border-radius: 10px; }\n");
        sb.append(".title-desc h1 { font-size: 1.6rem; font-weight: 700; }\n");
        sb.append(".title-desc p { color: var(--text-muted); font-size: 0.9rem; margin-top: 2px; }\n");
        sb.append(".controls { display: flex; gap: 12px; align-items: center; }\n");
        sb.append(".theme-toggle { background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-main); padding: 8px 14px; border-radius: 8px; cursor: pointer; font-weight: 500; font-size: 0.85rem; }\n");
        sb.append(".theme-toggle:hover { border-color: var(--primary); }\n");
        sb.append(".search-form { display: flex; gap: 8px; margin-bottom: 24px; }\n");
        sb.append(".search-input { flex: 1; max-width: 400px; background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-main); padding: 10px 16px; border-radius: 8px; font-size: 0.95rem; outline: none; }\n");
        sb.append(".search-input:focus { border-color: var(--primary); }\n");
        sb.append(".search-btn { background: var(--primary); border: none; color: #fff; padding: 10px 20px; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 0.95rem; }\n");
        sb.append(".search-btn:hover { background: var(--primary-hover); }\n");
        sb.append(".tab-nav { display: flex; gap: 10px; margin-bottom: 20px; }\n");
        sb.append(".tab-btn { background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-muted); padding: 10px 20px; border-radius: 8px; cursor: pointer; font-weight: 600; font-size: 0.95rem; }\n");
        sb.append(".tab-btn.active { background: var(--primary); color: #fff; border-color: var(--primary); }\n");
        sb.append(".table-card { background: var(--bg-card); border-radius: 12px; border: 1px solid var(--border-color); overflow: hidden; margin-bottom: 24px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); }\n");
        sb.append(".card-header { padding: 16px 20px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; }\n");
        sb.append(".card-header h2 { font-size: 1.2rem; font-weight: 600; display: flex; align-items: center; gap: 8px; }\n");
        sb.append("table { width: 100%; border-collapse: collapse; text-align: left; font-size: 0.92rem; }\n");
        sb.append("th { background: rgba(0, 0, 0, 0.1); padding: 14px 18px; color: var(--text-muted); font-weight: 600; border-bottom: 1px solid var(--border-color); text-transform: uppercase; font-size: 0.78rem; letter-spacing: 0.05em; }\n");
        sb.append("td { padding: 14px 18px; border-bottom: 1px solid var(--border-color); vertical-align: middle; }\n");
        sb.append("tr:last-child td { border-bottom: none; }\n");
        sb.append("tr:hover td { background: rgba(255, 255, 255, 0.02); }\n");
        sb.append(".badge { display: inline-block; padding: 4px 10px; border-radius: 6px; font-size: 0.8rem; font-weight: 600; }\n");
        sb.append(".badge-active { background: rgba(16, 185, 129, 0.15); color: var(--accent-green); border: 1px solid rgba(16, 185, 129, 0.3); }\n");
        sb.append(".badge-expired { background: rgba(148, 163, 184, 0.15); color: var(--text-muted); border: 1px solid rgba(148, 163, 184, 0.3); }\n");
        sb.append(".badge-revoked { background: rgba(239, 68, 68, 0.15); color: var(--accent-red); border: 1px solid rgba(239, 68, 68, 0.3); }\n");
        sb.append(".badge-perm { background: rgba(239, 68, 68, 0.15); color: var(--accent-red); font-weight: 700; }\n");
        sb.append(".player-name { font-weight: 600; color: var(--text-main); display: flex; align-items: center; gap: 8px; }\n");
        sb.append(".player-avatar { width: 24px; height: 24px; border-radius: 4px; }\n");
        sb.append(".pagination { display: flex; justify-content: center; gap: 8px; padding: 16px; }\n");
        sb.append(".page-btn { background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-main); padding: 8px 14px; border-radius: 6px; text-decoration: none; font-size: 0.88rem; font-weight: 500; }\n");
        sb.append(".page-btn:hover:not(.disabled) { border-color: var(--primary); color: var(--primary); }\n");
        sb.append(".page-btn.active { background: var(--primary); color: #fff; border-color: var(--primary); }\n");
        sb.append(".page-btn.disabled { opacity: 0.4; cursor: not-allowed; }\n");
        sb.append(".empty-state { text-align: center; padding: 40px 20px; color: var(--text-muted); }\n");
        sb.append(".footer { text-align: center; color: var(--text-muted); font-size: 0.85rem; margin-top: 32px; padding-top: 16px; border-top: 1px solid var(--border-color); }\n");
        sb.append("@media (max-width: 768px) {\n");
        sb.append("  table, thead, tbody, th, td, tr { display: block; }\n");
        sb.append("  thead tr { position: absolute; top: -9999px; left: -9999px; }\n");
        sb.append("  tr { border-bottom: 1px solid var(--border-color); padding: 12px 16px; }\n");
        sb.append("  td { border: none; padding: 6px 0; display: flex; justify-content: space-between; align-items: center; text-align: right; }\n");
        sb.append("  td::before { content: attr(data-label); font-weight: 600; color: var(--text-muted); text-align: left; padding-right: 12px; font-size: 0.8rem; text-transform: uppercase; }\n");
        sb.append("}\n");
        sb.append("</style>\n");
        sb.append("<script>\n");
        sb.append("function toggleTheme() {\n");
        sb.append("  const html = document.documentElement;\n");
        sb.append("  const next = html.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';\n");
        sb.append("  html.setAttribute('data-theme', next);\n");
        sb.append("  localStorage.setItem('hbans_theme', next);\n");
        sb.append("}\n");
        sb.append("function switchTab(tabId) {\n");
        sb.append("  document.getElementById('bans-section').style.display = tabId === 'bans' ? 'block' : 'none';\n");
        sb.append("  document.getElementById('mutes-section').style.display = tabId === 'mutes' ? 'block' : 'none';\n");
        sb.append("  document.getElementById('tab-bans-btn').classList.toggle('active', tabId === 'bans');\n");
        sb.append("  document.getElementById('tab-mutes-btn').classList.toggle('active', tabId === 'mutes');\n");
        sb.append("}\n");
        sb.append("document.addEventListener('DOMContentLoaded', () => {\n");
        sb.append("  const saved = localStorage.getItem('hbans_theme');\n");
        sb.append("  if (saved) document.documentElement.setAttribute('data-theme', saved);\n");
        sb.append("});\n");
        sb.append("</script>\n");
        sb.append("</head>\n<body>\n");

        sb.append("<div class=\"container\">\n");
        sb.append("  <header class=\"header\">\n");
        sb.append("    <div class=\"logo-title\">\n");
        sb.append("      <div class=\"logo-badge\">HB</div>\n");
        sb.append("      <div class=\"title-desc\">\n");
        sb.append("        <h1>").append(escapeHtml(serverName)).append(" Ceza Listesi</h1>\n");
        sb.append("        <p>HBanSystem Moderasyon ve Güvenlik Sistemi</p>\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("    <div class=\"controls\">\n");
        sb.append("      <button class=\"theme-toggle\" onclick=\"toggleTheme()\">🌓 Tema Değiştir</button>\n");
        sb.append("    </div>\n");
        sb.append("  </header>\n");

        sb.append("  <form class=\"search-form\" method=\"GET\" action=\"/bans\">\n");
        sb.append("    <input type=\"text\" name=\"search\" class=\"search-input\" placeholder=\"Oyuncu adına göre ara...\" value=\"").append(safeSearch).append("\">\n");
        sb.append("    <button type=\"submit\" class=\"search-btn\">Ara</button>\n");
        if (!safeSearch.isEmpty()) {
            sb.append("    <a href=\"/bans\" class=\"search-btn\" style=\"background: var(--bg-input); color: var(--text-main); text-decoration: none; display: flex; align-items: center;\">Temizle</a>\n");
        }
        sb.append("  </form>\n");

        sb.append("  <div class=\"tab-nav\">\n");
        sb.append("    <button id=\"tab-bans-btn\" class=\"tab-btn active\" onclick=\"switchTab('bans')\">🔨 Yasaklananlar (Bans)</button>\n");
        sb.append("    <button id=\"tab-mutes-btn\" class=\"tab-btn\" onclick=\"switchTab('mutes')\">🔇 Susturulanlar (Mutes)</button>\n");
        sb.append("  </div>\n");

        sb.append("  <div id=\"bans-section\">\n");
        sb.append("    <div class=\"table-card\">\n");
        sb.append("      <div class=\"card-header\">\n");
        sb.append("        <h2>🔨 Yasaklanan Oyuncular (").append(bans.size()).append(")</h2>\n");
        sb.append("      </div>\n");
        if (bans.isEmpty()) {
            sb.append("      <div class=\"empty-state\">Aktif veya eşleşen yasaklama kaydı bulunamadı.</div>\n");
        } else {
            sb.append("      <table>\n<thead>\n<tr>\n");
            sb.append("        <th>Oyuncu</th><th>Sebep</th><th>Süre</th><th>Tarih</th><th>Bitiş</th><th>Yetkili</th><th>Durum</th>\n");
            sb.append("      </tr>\n</thead>\n<tbody>\n");
            for (PunishmentDto b : bans) {
                renderTableRow(sb, b);
            }
            sb.append("      </tbody>\n</table>\n");
            renderPagination(sb, "ban_page", currentBanPage, totalBanPages, search);
        }
        sb.append("    </div>\n");
        sb.append("  </div>\n");

        sb.append("  <div id=\"mutes-section\" style=\"display: none;\">\n");
        sb.append("    <div class=\"table-card\">\n");
        sb.append("      <div class=\"card-header\">\n");
        sb.append("        <h2>🔇 Susturulan Oyuncular (").append(mutes.size()).append(")</h2>\n");
        sb.append("      </div>\n");
        if (mutes.isEmpty()) {
            sb.append("      <div class=\"empty-state\">Aktif veya eşleşen susturma kaydı bulunamadı.</div>\n");
        } else {
            sb.append("      <table>\n<thead>\n<tr>\n");
            sb.append("        <th>Oyuncu</th><th>Sebep</th><th>Süre</th><th>Tarih</th><th>Bitiş</th><th>Yetkili</th><th>Durum</th>\n");
            sb.append("      </tr>\n</thead>\n<tbody>\n");
            for (PunishmentDto m : mutes) {
                renderTableRow(sb, m);
            }
            sb.append("      </tbody>\n</table>\n");
            renderPagination(sb, "mute_page", currentMutePage, totalMutePages, search);
        }
        sb.append("    </div>\n");
        sb.append("  </div>\n");

        sb.append("  <footer class=\"footer\">\n");
        sb.append("    <p>HBanSystem v1.0.0 &bull; Geliştirici: Ardelys &bull; Güvenli Web Entegrasyonu</p>\n");
        sb.append("  </footer>\n");
        sb.append("</div>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private static void renderTableRow(StringBuilder sb, PunishmentDto p) {
        String badgeClass = switch (p.status()) {
            case "Aktif" -> "badge-active";
            case "Kaldırıldı" -> "badge-revoked";
            default -> "badge-expired";
        };

        sb.append("<tr>\n");
        sb.append("  <td data-label=\"Oyuncu\"><span class=\"player-name\"><img class=\"player-avatar\" src=\"https://minotar.net/avatar/").append(escapeHtml(p.player())).append("/24.png\" alt=\"\">").append(escapeHtml(p.player())).append("</span></td>\n");
        sb.append("  <td data-label=\"Sebep\">").append(escapeHtml(p.reason())).append("</td>\n");
        sb.append("  <td data-label=\"Süre\">").append(escapeHtml(p.duration())).append("</td>\n");
        sb.append("  <td data-label=\"Tarih\">").append(escapeHtml(p.createdAtFormatted())).append("</td>\n");
        sb.append("  <td data-label=\"Bitiş\">").append(escapeHtml(p.expiresAtFormatted())).append("</td>\n");
        sb.append("  <td data-label=\"Yetkili\">").append(escapeHtml(p.staff())).append("</td>\n");
        sb.append("  <td data-label=\"Durum\"><span class=\"badge ").append(badgeClass).append("\">").append(escapeHtml(p.status())).append("</span></td>\n");
        sb.append("</tr>\n");
    }

    private static void renderPagination(StringBuilder sb, String param, int page, int totalPages, @Nullable String search) {
        if (totalPages <= 1) return;

        sb.append("<div class=\"pagination\">\n");
        String searchParam = (search != null && !search.isEmpty()) ? "&search=" + escapeHtml(search) : "";

        if (page > 1) {
            sb.append("  <a href=\"/bans?").append(param).append("=").append(page - 1).append(searchParam).append("\" class=\"page-btn\">&laquo; Önceki</a>\n");
        } else {
            sb.append("  <span class=\"page-btn disabled\">&laquo; Önceki</span>\n");
        }

        int start = Math.max(1, page - 2);
        int end = Math.min(totalPages, page + 2);
        for (int i = start; i <= end; i++) {
            if (i == page) {
                sb.append("  <span class=\"page-btn active\">").append(i).append("</span>\n");
            } else {
                sb.append("  <a href=\"/bans?").append(param).append("=").append(i).append(searchParam).append("\" class=\"page-btn\">").append(i).append("</a>\n");
            }
        }

        if (page < totalPages) {
            sb.append("  <a href=\"/bans?").append(param).append("=").append(page + 1).append(searchParam).append("\" class=\"page-btn\">Sonraki &raquo;</a>\n");
        } else {
            sb.append("  <span class=\"page-btn disabled\">Sonraki &raquo;</span>\n");
        }
        sb.append("</div>\n");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
