package com.example.scrauth;

import com.example.scrauth.auth.AuthManager;
import com.example.scrauth.commands.AuthCommand;
import com.example.scrauth.commands.AuthResetCommand;
import com.example.scrauth.commands.ScrAuthAdminCommand;
import com.example.scrauth.discord.DiscordBotManager;
import com.example.scrauth.ip.IPManager;
import com.example.scrauth.listeners.PlayerEventListener;
import com.example.scrauth.listeners.PlayerJoinListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class ScrAuthPlugin extends JavaPlugin {

    private static ScrAuthPlugin instance;
    private AuthManager authManager;
    private DiscordBotManager discordBotManager;
    private IPManager ipManager;

    @Override
    public void onEnable() {
        instance = this;

        // Config dosyasini olustur/yukle
        saveDefaultConfig();

        // Veri yoneticilerini baslat
        this.ipManager = new IPManager(this);
        this.authManager = new AuthManager(this);

        // Discord botunu baslat (aktifse)
        if (getConfig().getBoolean("auth-methods.discord", true)) {
            String token = getConfig().getString("discord.bot-token", "");
            if (token.isEmpty() || token.equals("BURAYA_BOT_TOKENINIZI_GIRIN")) {
                getLogger().warning("============================================");
                getLogger().warning("Discord bot token'i ayarlanmamis!");
                getLogger().warning("config.yml dosyasindaki 'discord.bot-token'");
                getLogger().warning("alanini doldurun ve eklentiyi yeniden yukleyin.");
                getLogger().warning("============================================");
            } else {
                this.discordBotManager = new DiscordBotManager(this, token);
                this.discordBotManager.start();
            }
        }

        // Event dinleyicilerini kaydet
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);

        // Komutlari kaydet
        getCommand("admindogrula").setExecutor(new AuthCommand(this));
        getCommand("authreset").setExecutor(new AuthResetCommand(this));
        getCommand("scrauthadmin").setExecutor(new ScrAuthAdminCommand(this));

        getLogger().info("ScrAuth v" + getDescription().getVersion() + " basariyla yuklendi!");
    }

    @Override
    public void onDisable() {
        // Karantinadaki tum oyuncularin envanterlerini geri ver
        if (authManager != null) {
            authManager.restoreAllInventories();
        }

        // Discord botunu durdur
        if (discordBotManager != null) {
            discordBotManager.stop();
        }

        // IP verisini kaydet
        if (ipManager != null) {
            ipManager.save();
        }

        getLogger().info("ScrAuth devre disi birakildi.");
    }

    public static ScrAuthPlugin getInstance() {
        return instance;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public DiscordBotManager getDiscordBotManager() {
        return discordBotManager;
    }

    public IPManager getIpManager() {
        return ipManager;
    }

    /**
     * Config'den mesaj al ve prefix ekle.
     */
    public String getMessage(String key) {
        String prefix = translateColors(getConfig().getString("messages.prefix", "&8[&cScrAuth&8] &r"));
        String msg = getConfig().getString("messages." + key, "&cMesaj bulunamadi: " + key);
        return translateColors(prefix + msg);
    }

    /**
     * Renk kodlarini cevir (&a, &c, vb.)
     */
    public String translateColors(String text) {
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', text);
    }
}
