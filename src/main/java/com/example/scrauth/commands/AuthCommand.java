package com.example.scrauth.commands;

import com.example.scrauth.ScrAuthPlugin;
import com.example.scrauth.auth.AuthManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /admindogrula <kod> komutu
 * Hem Discord kodu ile hem de sifre ile dogrulama destekler.
 */
public class AuthCommand implements CommandExecutor {

    private final ScrAuthPlugin plugin;

    public AuthCommand(ScrAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Bu komut sadece oyuncular tarafindan kullanilabilir.");
            return true;
        }

        AuthManager authManager = plugin.getAuthManager();

        // Dogrulama bekliyor mu?
        if (!authManager.isPending(player.getUniqueId())) {
            player.sendMessage(plugin.translateColors("&7Zaten dogrulandiniz."));
            return true;
        }

        if (args.length < 1) {
            boolean discordEnabled = plugin.getConfig().getBoolean("auth-methods.discord", true);
            if (discordEnabled) {
                player.sendMessage(plugin.getMessage("waiting-discord"));
            } else {
                player.sendMessage(plugin.translateColors("&cKullanim: &e/admindogrula <kod>"));
            }
            return true;
        }

        String input = args[0].trim();
        boolean discordEnabled = plugin.getConfig().getBoolean("auth-methods.discord", true);
        boolean passwordEnabled = plugin.getConfig().getBoolean("auth-methods.password", false);

        boolean success = false;

        // Discord kodu ile dogrulama
        if (discordEnabled) {
            success = authManager.checkCode(player.getUniqueId(), input);
            if (!success && !passwordEnabled) {
                player.sendMessage(plugin.getMessage("fail-wrong-code"));
                return true;
            }
        }

        // Sifre ile dogrulama (discord basarisizsa veya discord kapali)
        if (!success && passwordEnabled) {
            success = authManager.checkPassword(input);
        }

        if (success) {
            authManager.exitQuarantine(player);
        } else {
            player.sendMessage(plugin.getMessage("fail-wrong-code"));
        }

        return true;
    }
}
