# 🛡️ HBanSystem

<div align="center">

![Java](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen?style=for-the-badge&logo=mojang&logoColor=white)
![Paper](https://img.shields.io/badge/Paper%20API-26.3-blue?style=for-the-badge&logo=papermc&logoColor=white)
![Build](https://img.shields.io/badge/Build-Passing-success?style=for-the-badge&logo=githubactions&logoColor=white)
![Author](https://img.shields.io/badge/Author-Ardelys-blueviolet?style=for-the-badge)

**Minecraft Paper 1.21.11 ve Java 21 LTS için geliştirilmiş; sıfır TPS etkisi, asenkron veritabanı boru hattı, HMAC-SHA256 şifreli REST API ve modern bağımsız web paneli içeren yeni nesil ceza, denetim ve güvenlik sistemi.**

[Özellikler](#-özellikler) • [Kurulum](#-kurulum) • [Komutlar ve Yetkiler](#-komutlar-ve-yetkiler) • [Web Paneli](#-web-paneli-bansphp) • [API Anahtarı Sistemi](#-api-anahtarı-sistemi) • [Geliştirici API](#-geliştirici-apisı)

</div>

---

## 🌟 Özellikler

### 1. ⚡ Sıfır TPS Etkisi & Asenkron Mimari
- **Tam Asenkron Boru Hattı**: Veritabanı okuma ve yazma işlemleri ana sunucu iş parçacığını (Main Thread) asla bloklamaz.
- **HikariCP Bağlantı Havuzu**: SQLite, MySQL ve MariaDB için en yüksek hızda bağlantı yönetimi.
- **Çok Katmanlı Bellek Önbelleği (In-Memory Cache)**: Aktif yasak ve susturmalar RAM üzerinde tutulur. Oyuncu sunucuya bağlandığında veya mesaj yazdığında sıfır SQL gecikmesiyle (0ms) denetlenir.

### 2. 🔨 Kapsamlı Ceza ve Denetim Sistemi
- **Ban & Tempban**: Kalıcı ve süreli oyuncu yasaklamaları.
- **IP-Ban & Temp-IP-Ban**: Oyuncunun son IP adresini veya doğrudan bir IP bloğunu yasaklama.
- **Mute & Tempmute**: Kalıcı ve süreli sohbet susturmaları.
- **Kick & Warn**: Sunucudan atma ve ID bazlı uyarı sistemi (`/unwarn`).
- **Gelişmiş Süre Motoru**: `30s`, `10m`, `2h`, `7d`, `2w`, `3mo`, `1y`, `1d12h`, `permanent`.
- **Yan Hesap (Alts) Tespiti**: Aynı IP üzerinden giriş yapmış diğer hesapları ve bu hesapların ceza durumlarını anında listeleme.
- **Ceza Geçmişi & İnceleme**: Oyuncu bazlı detaylı geçmiş (`/history`) ve ceza ID'si ile sorgulama (`/baninfo`).

### 3. 🌐 Modern Web Paneli (`bans.php`) & REST API
- **Hazır Web Arayüzü**: PHP tabanlı, modern koyu tema (Dark UI), anlık arama (Debounced instant search), ceza türü filtreleri ve sayfalama.
- **HMAC-SHA256 İmzalı Güvenlik**: Web sunucusu ile Minecraft sunucusu arasındaki API iletişimi gizli anahtarla imzalanır.
- **Replay Attack Koruması**: Benzersiz Nonce ve zaman damgası (Timestamp - 60s TTL) doğrulaması ile tekrarlama saldırıları engellenir.
- **Önbellek & Oran Sınırlama (Rate Limiting)**: Web istekleri sunucuya yük bindirmemesi için IP başına oran sınırlamalı ve yerel önbelleklidir.

### 4. 🔑 Dinamik API Anahtarı Sistemi
- Oyun içerisinden veya konsoldan dinamik API anahtarı üretimi:
  - `/hban apikey create <isim> [izinler] [bitiş]`
  - `/hban apikey list`
  - `/hban apikey revoke <key|isim>`
  - `/hban apikey check <key>`
- Yetki bazlı API kısıtlaması (`READ`, `WRITE`, `ADMIN`).
- Otomatik SHA-256 hash'leme ile veritabanında güvenli saklama.

### 5. 🛡️ İstemci Enjeksiyon ve Protokol Güvenlik Modülü (Security)
- İstemci markası (`brand`) manipülasyonlarına karşı çoklu sinyal korelasyonu ve güven skoru analizi (%0 - %100).
- Hile ve yetkisiz enjeksiyon tespitlerinde otomatik aksiyon (LOG, ALERT, KICK, TEMPBAN).
- Popüler meşru istemciler için beyaz liste desteği (Lunar, Badlion, Fabric, Iris, Sodium vb.).

### 6. 🔗 Genişletilmiş Entegrasyonlar
- **Adventure / MiniMessage**: Zengin renkler, gradyanlar ve modern yazı biçimleri.
- **LuckPerms**: Rütbe ağırlık kontrolü; düşük yetkili personellerin üst kademeleri cezalandırması engellenir.
- **Discord Webhook**: Zengin gömülü (Embed) bildirimlerle cezaların ve güvenlik uyarılarının anlık Discord'a iletilmesi.
- **PlaceholderAPI**: Skor tabloları ve sohbet formatları için zengin yer tutucu desteği.

---

## 📋 Gereksinimler

| Bileşen | Gereksinim |
|---|---|
| **Minecraft Sunucusu** | Paper / Purpur 1.21.11 (Paper API 26.3+) |
| **Java Sürümü** | Java 21 LTS (64-bit) |
| **Veritabanı** | SQLite (Dahili gelir), MySQL 8.0+ veya MariaDB 10.5+ |
| **Web Paneli (İsteğe Bağlı)** | PHP 8.1+ (cURL ve OpenSSL modülleri aktif) |
| **Uyumlu Eklentiler** | LuckPerms, PlaceholderAPI |

---

## 🚀 Kurulum

### 1. Minecraft Eklenti Kurulumu
1. En son derlenmiş `HBanSystem-1.0.0.jar` dosyasını sunucunuzun `plugins/` klasörüne kopyalayın.
2. Sunucunuzu başlatın veya yeniden yükleyin. Eklenti dosyaları `plugins/HBanSystem/` dizininde otomatik oluşturulacaktır.
3. `database.yml` dosyasından dilediğiniz veritabanı türünü seçin (`SQLITE`, `MYSQL`, `MARIADB`).
4. (İsteğe bağlı) `discord.yml` dosyasına Discord webhook URL'inizi ekleyin.
5. `/hban reload` komutuyla ayarları yenileyin.

### 2. Web Sunucusu Kurulumu (`bans.php`)
1. Proje içindeki `web/bans.php` dosyasını web sunucunuzun kök dizinine (örneğin `/var/www/html/bans.php` veya `public_html/bans/index.php`) yükleyin.
2. `plugins/HBanSystem/config.yml` dosyasında web sunucusunu etkinleştirin:
   ```yaml
   web:
     enabled: true
     port: 8080
     host: "0.0.0.0"
     secret: "COK_GUCLU_GIZLI_BIR_ANAHTAR_BURAYA"
   ```
3. `bans.php` dosyasının en üstündeki yapılandırma bloğunu sunucunuzla eşleştirin:
   ```php
   $HBAN_CONFIG = [
       'api_url'     => 'http://minecraft-sunucu-ip:8080',
       'hmac_secret' => 'COK_GUCLU_GIZLI_BIR_ANAHTAR_BURAYA',
       'server_name' => 'Minecraft Sunucum',
       ...
   ];
   ```
4. Web tarayıcınızdan `https://siteniz.com/bans.php` adresine giderek cezaları canlı olarak görüntüleyin!

---

## ⌨️ Komutlar ve Yetkiler

| Komut | Yetki | Açıklama |
|---|---|---|
| `/ban <oyuncu> [süre] <sebep> [-s]` | `hban.ban` | Kalıcı veya süreli oyuncu yasaklar. |
| `/tempban <oyuncu> <süre> <sebep> [-s]` | `hban.tempban` | Belirtilen süreyle oyuncu yasaklar. |
| `/unban <oyuncu> [sebep] [-s]` | `hban.unban` | Oyuncunun yasağını kaldırır. |
| `/ipban <oyuncu\|ip> [süre] <sebep> [-s]` | `hban.ipban` | IP adresini veya oyuncunun son IP'sini yasaklar. |
| `/tempipban <oyuncu\|ip> <süre> <sebep> [-s]` | `hban.tempipban` | Belirli süreyle IP yasaklar. |
| `/unipban <oyuncu\|ip> [sebep] [-s]` | `hban.unipban` | IP yasağını kaldırır. |
| `/mute <oyuncu> [süre] <sebep> [-s]` | `hban.mute` | Oyuncuyu kalıcı veya süreli susturur. |
| `/tempmute <oyuncu> <süre> <sebep> [-s]` | `hban.tempmute` | Oyuncuyu süreli susturur. |
| `/unmute <oyuncu> [sebep] [-s]` | `hban.unmute` | Oyuncunun susturmasını kaldırır. |
| `/kick <oyuncu> <sebep> [-s]` | `hban.kick` | Oyuncuyu sunucudan atar. |
| `/warn <oyuncu> <sebep> [-s]` | `hban.warn` | Oyuncuya resmi uyarı verir. |
| `/unwarn <oyuncu> <id>` | `hban.unwarn` | ID numarası verilen uyarıyı geçersiz kılar. |
| `/history <oyuncu> [sayfa]` | `hban.history` | Oyuncunun geçmiş ve aktif tüm cezalarını gösterir. |
| `/baninfo <id>` | `hban.baninfo` | Ceza ID'si ile detaylı inceleme yapar. |
| `/alts <oyuncu>` | `hban.alts` | Aynı IP üzerinden giren diğer hesapları listeler. |
| `/banlist [sayfa]` | `hban.banlist` | Aktif yasaklamaları listeler. |
| `/mutelist [sayfa]` | `hban.mutelist` | Aktif susturmaları listeler. |
| `/warnlist [sayfa]` | `hban.warnlist` | Aktif uyarıları listeler. |
| `/checkban <oyuncu>` | `hban.check` | Oyuncunun yasak durumunu kontrol eder. |
| `/checkmute <oyuncu>` | `hban.check` | Oyuncunun susturma durumunu kontrol eder. |
| `/hban reload` | `hban.admin` | Eklenti ayarlarını bellek sızıntısız yeniler. |
| `/hban apikey ...` | `hban.admin` | REST API anahtarlarını yönetir. |

> **İpucu**: Komut sonuna `-s` eklenirse ceza sessiz (silent) uygulanır; duyuru yalnızca yetkililere iletilir.

---

## 🔑 API Anahtarı Sistemi

Harici web siteleri, Discord botları veya yönetim panelleri için güvenli API anahtarları oluşturabilirsiniz:

```bash
# Okuma ve Yazma yetkisine sahip 30 günlük API anahtarı:
/hban apikey create WebSitesi READ,WRITE 30d

# Tüm aktif anahtarları görüntüleme:
/hban apikey list

# Bir anahtarı iptal etme:
/hban apikey revoke WebSitesi

# Anahtarın geçerliliğini ve kalan süresini kontrol etme:
/hban apikey check hban_live_xxxxxxxx
```

### 📡 REST API Uç Noktaları

| Metot | Uç Nokta | Açıklama |
|---|---|---|
| `GET` | `/api/v1/bans` | Aktif ve geçmiş yasakları listeler. |
| `GET` | `/api/v1/mutes` | Aktif susturma cezalarını listeler. |
| `GET` | `/api/v1/history?player=<isim>` | Belirtilen oyuncunun ceza geçmişini döndürür. |
| `GET` | `/api/v1/stats` | Sunucu ceza istatistiklerini döndürür. |
| `POST` | `/api/v1/punish` | Harici olarak ceza uygular (`WRITE` izni gerektirir). |

---

## 🧩 PlaceholderAPI Değişkenleri

| Placeholder | Açıklama |
|---|---|
| `%hbansystem_banned%` | Oyuncu yasaklı mı? (`Evet` / `Hayır`) |
| `%hbansystem_muted%` | Oyuncu susturulmuş mu? (`Evet` / `Hayır`) |
| `%hbansystem_ban_reason%` | Aktif yasağın gerekçesi. |
| `%hbansystem_ban_staff%` | Yasağı uygulayan yetkili. |
| `%hbansystem_ban_duration%` | Kalan yasak süresi. |
| `%hbansystem_mute_reason%` | Aktif susturma gerekçesi. |
| `%hbansystem_mute_duration%` | Kalan susturma süresi. |
| `%hbansystem_warn_count%` | Oyuncunun toplam aktif uyarı sayısı. |

---

## 🛠️ Geliştirici API'sı (Developer API)

Kendi eklentilerinizden HBanSystem API'sine doğrudan erişebilirsiniz:

```java
import com.ardelys.hbansystem.api.HBanSystemApi;
import com.ardelys.hbansystem.api.HBanSystemApiProvider;

HBanSystemApi api = HBanSystemApiProvider.get();

// RAM önbelleğinden O(1) hızında yasak denetimi:
boolean isBanned = api.isBanned(playerUuid);

// Asenkron yasaklama işlemi:
api.ban(targetUuid, "PlayerName", "1.2.3.4", "Hile Kullanımı", staffUuid, "StaffName", 7L * 24 * 3600 * 1000)
   .thenAccept(punishment -> {
       getLogger().info("Ceza başarıyla uygulandı! ID: #" + punishment.getId());
   });
```

### 🔔 Dinlenebilir Olaylar (Custom Events)
- `BanEvent` (Cancellable)
- `UnbanEvent` (Cancellable)
- `MuteEvent` (Cancellable)
- `UnmuteEvent` (Cancellable)
- `KickEvent` (Cancellable)
- `WarningEvent` (Cancellable)
- `SecurityDetectionEvent` (Cancellable)

---

## 🏗️ Projeyi Derleme

Kaynak kodlarını kendiniz derlemek için:

```bash
# Projeyi klonlayın
git clone https://github.com/ardelys/HBanSystem.git
cd HBanSystem

# Maven Wrapper ile temiz derleme ve paketleme
./mvnw clean package
```

Derlenen gölgelenmiş (all-in-one / shaded) eklenti JAR dosyası:  
📂 `target/HBanSystem-1.0.0.jar`

---

## 📄 Lisans & Yazar

- **Geliştirici**: [Ardelys](https://github.com/ardelys)
- **Telif Hakkı**: © 2026 Ardelys. Tüm hakları saklıdır.
