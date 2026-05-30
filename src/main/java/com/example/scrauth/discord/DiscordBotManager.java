package com.example.scrauth.discord;

import com.example.scrauth.ScrAuthPlugin;
import com.example.scrauth.auth.AuthManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Discord JDA bot manager.
 * !dogrula komutuna cevap verir, kod uretir ve DM gonderir.
 */
public class DiscordBotManager extends ListenerAdapter {

    private final ScrAuthPlugin plugin;
    private final String token;
    private JDA jda;

    public DiscordBotManager(ScrAuthPlugin plugin, String token) {
        this.plugin = plugin;
        this.token = token;
    }

    /**
     * Discord botunu baslat.
     */
    public void start() {
        // JDA async olarak kendi thread'inde calisir
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                jda = JDABuilder.createDefault(token)
                        .enableIntents(
                                GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.MESSAGE_CONTENT,
                                GatewayIntent.DIRECT_MESSAGES
                        )
                        .addEventListeners(this)
                        .build();

                jda.awaitReady();
                plugin.getLogger().info("Discord botu basarıyla baglandi! Bot: " + jda.getSelfUser().getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Discord botu baslatılamadi! Token dogru mu?", e);
            }
        });
    }

    /**
     * Discord botunu durdur.
     */
    public void stop() {
        if (jda != null) {
            jda.shutdown();
            plugin.getLogger().info("Discord botu kapatildi.");
        }
    }

    /**
     * Discord mesajlarini dinle.
     */
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Botun kendi mesajlarini yoksay
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw().trim();

        // Komutu kontrol et (!dogrula)
        if (!content.equalsIgnoreCase("!dogrula")) return;

        String discordUserId = event.getAuthor().getId();
        User discordUser = event.getAuthor();

        // Sunucu ID filtresi (config'den)
        // Kanalda yazilmissa veya DM ise devam et
        String channelId = plugin.getConfig().getString("discord.auth-channel-id", "");
        if (!channelId.isEmpty() && !event.isFromGuild()) {
            // Kanal belirtilmis ama DM'den geldi, DM'e de izin ver
        } else if (!channelId.isEmpty() && event.isFromGuild()) {
            // Belirtilen kanaldan mi geliyor?
            if (!event.getChannel().getId().equals(channelId)) {
                return; // Farkli kanaldan, yoksay
            }
        }

        // Bu Discord kullanicisina karsilik gelen Minecraft oyuncusunu bul
        UUID playerUuid = plugin.getAuthManager().getPlayerByDiscordId(discordUserId);

        if (playerUuid == null) {
            // Discord ID config'de eslestirilmemis
            discordUser.openPrivateChannel().queue(dm -> {
                dm.sendMessage(
                        "❌ **Hata:** Discord hesabiniz herhangi bir Minecraft hesabiyla eslesmemis.\n" +
                        "Sunucu yoneticisiyle iletisime gecin."
                ).queue();
            });
            return;
        }

        // Oyuncu sunucuda mi ve dogrulama bekliyor mu?
        AuthManager authManager = plugin.getAuthManager();
        Player player = Bukkit.getPlayer(playerUuid);

        if (player == null || !player.isOnline()) {
            discordUser.openPrivateChannel().queue(dm -> {
                dm.sendMessage(
                        "⚠️ **Oyuncu sunucuda bulunamadi!**\n" +
                        "Lutfen once sunucuya giris yapın, sonra tekrar deneyin."
                ).queue();
            });
            return;
        }

        if (!authManager.isPending(playerUuid)) {
            discordUser.openPrivateChannel().queue(dm -> {
                dm.sendMessage(
                        "ℹ️ **Zaten dogrulandiniz** veya dogrulama gerekmiyor.\n" +
                        "Oyunda zaten aktif durumdasiniz!"
                ).queue();
            });
            return;
        }

        // Kod uret ve DM gonder
        String code = authManager.generateAndStoreCode(playerUuid, discordUserId);

        discordUser.openPrivateChannel().queue(dm -> {
            dm.sendMessage(
                    "🔐 **ScrAuth Dogrulama Kodu**\n\n" +
                    "**Kod:** `" + code + "`\n\n" +
                    "Minecraft'ta su komutu girin:\n" +
                    "```/admindogrula " + code + "```\n" +
                    "⏱️ Bu kod **60 saniye** icinde gecersiz olacaktir.\n" +
                    "_Eger bu istegi siz yapmadıysaniz, hesabinizın guvenligi tehlikede olabilir!_"
            ).queue(
                    success -> {
                        // Oyuncuya mesaj gonder (Bukkit main thread'inde)
                        Bukkit.getScheduler().runTask(plugin, () ->
                                player.sendMessage(plugin.getMessage("waiting-discord") +
                                        " §7(Kodu DM olarak gonderdik)")
                        );
                        // Kanal mesajina onay ver
                        if (event.isFromGuild()) {
                            event.getMessage().addReaction(Emoji.fromUnicode("✅")).queue(null, err -> {});
                        }
                    },
                    error -> {
                        // DM gonderilemedi (gizlilik ayarlari?)
                        if (event.isFromGuild()) {
                            event.getChannel().sendMessage(
                                    event.getAuthor().getAsMention() +
                                    " ❌ Size DM gonderemedim! Discord gizlilik ayarlarinizi kontrol edin."
                            ).queue();
                        }
                    }
            );
        });
    }

    public JDA getJda() {
        return jda;
    }
}
