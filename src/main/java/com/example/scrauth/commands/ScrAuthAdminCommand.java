package com.example.scrauth.commands;

import com.example.scrauth.ScrAuthPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /scrauthadmin <reload|status> komutu
 * Eklenti yonetim komutu.
 */
public class ScrAuthAdminCommand implements CommandExecutor {

    private final ScrAuthPlugin plugin;

    public ScrAuthAdminCommand(ScrAuthPlugin plugin) {
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

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                // Config'i yeniden yukle
                plugin.reloadConfig();
                sender.sendMessage(plugin.translateColors(
                        "&8[&cScrAuth&8] &aConfig basariyla yeniden yuklendi!"));
            }
            case "status" -> {
                // Mevcut durumu goster
                long pendingCount = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> plugin.getAuthManager().isPending(p.getUniqueId()))
                        .count();

                sender.sendMessage(plugin.translateColors("&8--- &cScrAuth Durum &8---"));
                sender.sendMessage(plugin.translateColors(
                        "&7Discord Bot: &e" + (plugin.getDiscordBotManager() != null &&
                                plugin.getDiscordBotManager().getJda() != null ? "&aAktif" : "&cDevre Disi")));
                sender.sendMessage(plugin.translateColors(
                        "&7Discord Auth: &e" + plugin.getConfig().getBoolean("auth-methods.discord")));
                sender.sendMessage(plugin.translateColors(
                        "&7Sifre Auth: &e" + plugin.getConfig().getBoolean("auth-methods.password")));
                sender.sendMessage(plugin.translateColors(
                        "&7IP Kontrol: &e" + plugin.getConfig().getBoolean("auth-methods.ip-check")));
                sender.sendMessage(plugin.translateColors(
                        "&7Dogrulama Bekleyen: &c" + pendingCount + " &7oyuncu"));
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.translateColors("&8--- &cScrAuth Komutlari &8---"));
        sender.sendMessage(plugin.translateColors("&e/scrauthadmin reload &7- Config'i yeniden yukle"));
        sender.sendMessage(plugin.translateColors("&e/scrauthadmin status &7- Sistem durumunu goster"));
        sender.sendMessage(plugin.translateColors("&e/authreset <oyuncu> &7- IP kaydini sifirla"));
        sender.sendMessage(plugin.translateColors("&e/admindogrula <kod> &7- Dogrulama komutu"));
    }
}
