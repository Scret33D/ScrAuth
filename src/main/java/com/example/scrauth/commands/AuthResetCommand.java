package com.example.scrauth.commands;

import com.example.scrauth.ScrAuthPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * /authreset <oyuncu> komutu
 * Bir yetkilinin IP kaydini sifirlar (bir sonraki girisinde tekrar dogrulama yapmasini saglar).
 */
public class AuthResetCommand implements CommandExecutor {

    private final ScrAuthPlugin plugin;

    public AuthResetCommand(ScrAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("scrauth.admin")) {
            sender.sendMessage(plugin.translateColors("&cBu komutu kullanma yetkiniz yok!"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.translateColors("&cKullanim: &e/authreset <oyuncu>"));
            return true;
        }

        String targetName = args[0];

        // Oyuncuyu bul (cevrimici veya cevrimdisi)
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(plugin.getMessage("authreset-fail"));
            return true;
        }

        UUID targetUuid = target.getUniqueId();
        boolean reset = plugin.getIpManager().resetIp(targetUuid);

        if (reset) {
            String msg = plugin.getMessage("authreset-success")
                    .replace("{player}", targetName);
            sender.sendMessage(msg);
            plugin.getLogger().info("[ScrAuth] " + sender.getName() +
                    " tarafindan " + targetName + " icin IP sifirlandi.");
        } else {
            sender.sendMessage(plugin.getMessage("authreset-fail"));
        }

        return true;
    }
}
