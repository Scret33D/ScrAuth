package com.example.scrauth.auth;

import com.example.scrauth.ScrAuthPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tüm dogrulama durumlarini, pending kodlari ve karantina yonetimini merkezlesen sinif.
 */
public class AuthManager {

    private final ScrAuthPlugin plugin;

    // dogrulanmamis oyuncular seti
    private final Set<UUID> pendingAuth = ConcurrentHashMap.newKeySet();

    // Oyuncu UUID -> Discord kodu
    private final Map<UUID, String> pendingCodes = new ConcurrentHashMap<>();

    // Oyuncu UUID -> Discord UserId (kod isteginde)
    private final Map<UUID, String> discordIdMap = new ConcurrentHashMap<>();

    // Oyuncu UUID -> Gercek envanter
    private final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();

    // Oyuncu UUID -> Zaman asimi gorevi
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();

    public AuthManager(ScrAuthPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    //  KARANTINA BASLATMA / SONLANDIRMA
    // =========================================================

    /**
     * Oyuncuyu karantinaya al: kör, yavas, envanter bariyer, OP kaldir.
     */
    public void enterQuarantine(Player player) {
        UUID uuid = player.getUniqueId();
        pendingAuth.add(uuid);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            applyEffects(player);
            if (plugin.getConfig().getBoolean("effects.barrier-inventory", true)) {
                lockInventory(player);
            }
            showAuthTitle(player);
        });

        // Zaman asimi gorevi
        int timeoutSeconds = plugin.getConfig().getInt("timeout-seconds", 60);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isPending(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    String kickMsg = plugin.getConfig().getString("messages.timeout-kick",
                            "ScrAuth: Dogrulama suresi doldu.");
                    p.kickPlayer(plugin.translateColors(kickMsg));
                }
            }
        }, timeoutSeconds * 20L);

        timeoutTasks.put(uuid, task);
    }

    /**
     * Dogrulama basarili - karantinadan cikart.
     */
    public void exitQuarantine(Player player) {
        UUID uuid = player.getUniqueId();
        pendingAuth.remove(uuid);
        pendingCodes.remove(uuid);
        discordIdMap.remove(uuid);

        BukkitTask task = timeoutTasks.remove(uuid);
        if (task != null) task.cancel();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            removeEffects(player);
            restoreInventory(player);
            showSuccessTitle(player);
            player.sendMessage(plugin.getMessage("success"));

            // IP kaydet (IP kontrolu aktifse)
            if (plugin.getConfig().getBoolean("auth-methods.ip-check", true)) {
                String ip = getPlayerIp(player);
                if (ip != null) {
                    plugin.getIpManager().saveIp(uuid, ip);
                }
            }
        });
    }

    /**
     * Oyuncu ayrılınca temizle (envanter geri ver, efekt kaldir).
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask task = timeoutTasks.remove(uuid);
        if (task != null) task.cancel();

        if (isPending(uuid)) {
            pendingAuth.remove(uuid);
            pendingCodes.remove(uuid);
            discordIdMap.remove(uuid);
            restoreInventory(player);
            removeEffects(player);
        }
    }

    // =========================================================
    //  EFEKTLER
    // =========================================================

    private void applyEffects(Player player) {
        // Sonsuz (10000 saniye) Blindness efekti
        if (plugin.getConfig().getBoolean("effects.blindness", true)) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        }
        // Slowness 255 - hareket tamamen kilitlenir
        if (plugin.getConfig().getBoolean("effects.slowness", true)) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW, Integer.MAX_VALUE, 254, false, false));
        }
    }

    private void removeEffects(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOW);
    }

    // =========================================================
    //  ENVANTER KİLİTLEME
    // =========================================================

    private void lockInventory(Player player) {
        UUID uuid = player.getUniqueId();
        // Gercek envanteri kaydet
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = (contents[i] != null) ? contents[i].clone() : null;
        }
        savedInventories.put(uuid, copy);

        // Envanteri Barrier ile doldur
        player.getInventory().clear();
        ItemStack barrier = new ItemStack(Material.BARRIER);
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            player.getInventory().setItem(i, barrier);
        }
    }

    private void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] saved = savedInventories.remove(uuid);
        if (saved != null) {
            player.getInventory().clear();
            player.getInventory().setContents(saved);
        }
    }

    /**
     * Sunucu kapanirken tum bekleyen oyuncularin envanterlerini geri ver.
     */
    public void restoreAllInventories() {
        for (UUID uuid : pendingAuth) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                restoreInventory(p);
                removeEffects(p);
            }
        }
        pendingAuth.clear();
        savedInventories.clear();
    }

    // =========================================================
    //  BAŞLIKLAR (TITLE)
    // =========================================================

    private void showAuthTitle(Player player) {
        boolean isDiscord = plugin.getConfig().getBoolean("auth-methods.discord", true);
        boolean isPassword = plugin.getConfig().getBoolean("auth-methods.password", false);

        String subtitle;
        if (isDiscord) {
            subtitle = "§eBota §f!dogrula §eyazin ve kodu buraya girin!";
        } else if (isPassword) {
            subtitle = "§e/admindogrula <sifre> §fyazarak giris yapin!";
        } else {
            subtitle = "§eSistem dogrulama bekliyor...";
        }

        player.sendTitle(
                "§c⚠ DOGRULAMA GEREKLİ",
                subtitle,
                10, 200, 20
        );
    }

    private void showSuccessTitle(Player player) {
        player.sendTitle(
                "§a✔ DOGRULAMA BASARILI",
                "§fHosgeldiniz, §e" + player.getName() + "§f!",
                10, 80, 20
        );
    }

    public void showNewIpTitle(Player player) {
        player.sendTitle(
                "§c⚠ YENİ IP ALGILANDI",
                "§fGuvenliginiz icin tekrar dogrulama gerekiyor.",
                10, 100, 20
        );
    }

    // =========================================================
    //  KOD YÖNETİMİ
    // =========================================================

    /**
     * Oyuncu icin 6 haneli rastgele kod uret ve kaydet.
     */
    public String generateAndStoreCode(UUID playerUuid, String discordUserId) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        pendingCodes.put(playerUuid, code);
        discordIdMap.put(playerUuid, discordUserId);
        return code;
    }

    /**
     * Verilen kod dogru mu?
     */
    public boolean checkCode(UUID playerUuid, String inputCode) {
        String stored = pendingCodes.get(playerUuid);
        return stored != null && stored.equalsIgnoreCase(inputCode.trim());
    }

    /**
     * Sifre dogrulamasi (password modu).
     */
    public boolean checkPassword(String inputPassword) {
        String configPassword = plugin.getConfig().getString("admin-password", "");
        return configPassword.equals(inputPassword);
    }

    // =========================================================
    //  YARDIMCI METODLAR
    // =========================================================

    public boolean isPending(UUID uuid) {
        return pendingAuth.contains(uuid);
    }

    public String getPlayerIp(Player player) {
        if (player.getAddress() == null) return null;
        return player.getAddress().getAddress().getHostAddress();
    }

    /**
     * Config'den oyuncunun Discord ID'sini al.
     */
    public String getDiscordId(UUID playerUuid) {
        return plugin.getConfig().getString(
                "discord.player-discord-map." + playerUuid.toString(), null);
    }

    /**
     * Hangi Discord kullanicisinin bu kodu istedigini bul (Discord mesajlasmasi icin).
     */
    public UUID getPlayerByDiscordId(String discordUserId) {
        // Config map'inden ara
        var section = plugin.getConfig().getConfigurationSection("discord.player-discord-map");
        if (section == null) return null;

        for (String uuidStr : section.getKeys(false)) {
            String dId = section.getString(uuidStr);
            if (discordUserId.equals(dId)) {
                try {
                    return UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "Gecersiz UUID config'de: " + uuidStr);
                }
            }
        }
        return null;
    }
}
