package com.example.scrauth.listeners;

import com.example.scrauth.ScrAuthPlugin;
import com.example.scrauth.auth.AuthManager;
import com.example.scrauth.ip.IPManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Oyuncu giris/cikis eventleri.
 */
public class PlayerJoinListener implements Listener {

    private final ScrAuthPlugin plugin;

    public PlayerJoinListener(ScrAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Korunan oyuncu mu? (OP veya scrauth.protected yetkisi olanlar)
        if (!isProtected(player)) return;

        UUID uuid = player.getUniqueId();
        AuthManager authManager = plugin.getAuthManager();
        IPManager ipManager = plugin.getIpManager();

        boolean ipCheckEnabled = plugin.getConfig().getBoolean("auth-methods.ip-check", true);

        // IP kontrolu aktif mi ve bilinen IP'den mi geliyor?
        if (ipCheckEnabled) {
            String currentIp = authManager.getPlayerIp(player);
            if (currentIp != null && ipManager.isKnownIp(uuid, currentIp)) {
                // Bilinen IP: dogrulama gerekmiyor
                plugin.getLogger().info("[ScrAuth] " + player.getName() +
                        " bilinen IP'den baglandi, dogrulama atlandi. IP: " + currentIp);
                return;
            }

            // Yeni IP - bildirim goster
            String savedIp = ipManager.getSavedIp(uuid);
            if (savedIp != null) {
                // Daha once kayitli IP vardi ama degismis
                plugin.getLogger().warning("[ScrAuth] " + player.getName() +
                        " YENI IP ile baglandi! Onceki: " + savedIp + " | Yeni: " + currentIp);
                // Title biraz gecikmeli goster (join animasyonu bitsin)
                plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        authManager.showNewIpTitle(player), 20L);
            }
        }

        // Dogrulama gerekiyor - karantinaya al
        authManager.enterQuarantine(player);

        // Dogrulama tipine gore bildirim gonder
        boolean discordEnabled = plugin.getConfig().getBoolean("auth-methods.discord", true);
        boolean passwordEnabled = plugin.getConfig().getBoolean("auth-methods.password", false);

        if (discordEnabled) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    player.sendMessage(plugin.getMessage("waiting-discord")), 40L);
        } else if (passwordEnabled) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    player.sendMessage(plugin.getMessage("waiting-password")), 40L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getAuthManager().onPlayerQuit(event.getPlayer());
    }

    /**
     * Bu oyuncu ScrAuth korumasina tabi mi?
     */
    private boolean isProtected(Player player) {
        return player.isOp() || player.hasPermission("scrauth.protected");
    }
}
