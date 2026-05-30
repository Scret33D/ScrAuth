package com.example.scrauth.listeners;

import com.example.scrauth.ScrAuthPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * Karantinadaki oyuncularin hareket, sohbet, komut ve esya almasini engeller.
 */
public class PlayerEventListener implements Listener {

    private final ScrAuthPlugin plugin;

    public PlayerEventListener(ScrAuthPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Sohbeti engelle.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAuthManager().isPending(player.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("effects.block-chat", true)) return;

        event.setCancelled(true);
        player.sendMessage(plugin.getMessage("waiting-discord"));
    }

    /**
     * /admindogrula haricindeki tum komutlari engelle.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAuthManager().isPending(player.getUniqueId())) return;

        String cmd = event.getMessage().toLowerCase().trim();

        // Sadece /admindogrula komutuna izin ver
        if (cmd.startsWith("/admindogrula")) return;

        event.setCancelled(true);
        player.sendMessage(plugin.getMessage("waiting-discord"));
    }

    /**
     * Karantinadayken esya dusurmeyi engelle (envanter koruması).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (plugin.getAuthManager().isPending(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Karantinadayken esya almayı engelle.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (plugin.getAuthManager().isPending(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Karantinadayken blok interaksiyonunu engelle.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getAuthManager().isPending(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Hareket engeli: Slowness 255 zaten hareketi kilitiyor, ama
     * tam garanti icin pozisyonu sifirla.
     * NOT: Bu cok agresif olabilir, gerekirse kapatilabilir.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAuthManager().isPending(player.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("effects.block-move", true)) return;

        // Sadece XZ hareket etmesini engelle (yukarı bakmaya izin ver)
        if (event.getFrom().getX() != event.getTo().getX() ||
            event.getFrom().getZ() != event.getTo().getZ() ||
            event.getFrom().getY() != event.getTo().getY()) {

            // Bakin aci degisimlerine izin ver, pozisyonu kilitle
            event.getTo().setX(event.getFrom().getX());
            event.getTo().setY(event.getFrom().getY());
            event.getTo().setZ(event.getFrom().getZ());
        }
    }
}
