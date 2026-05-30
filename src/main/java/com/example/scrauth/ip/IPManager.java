package com.example.scrauth.ip;

import com.example.scrauth.ScrAuthPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Yetkili oyuncularin IP adreslerini ip_data.yml'e kaydeden ve okuyabilen sinif.
 */
public class IPManager {

    private final ScrAuthPlugin plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    // Bellekteki IP cache
    private final Map<UUID, String> ipCache = new HashMap<>();

    public IPManager(ScrAuthPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "ip_data.yml");
        load();
    }

    /**
     * Dosyadan IP verilerini bellegee yukle.
     */
    private void load() {
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "ip_data.yml olusturulamadi!", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Mevcut verileri cache'e yukle
        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String ip = dataConfig.getString(key);
                if (ip != null) {
                    ipCache.put(uuid, ip);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("ip_data.yml icinde gecersiz UUID: " + key);
            }
        }

        plugin.getLogger().info("IP verileri yuklendi. " + ipCache.size() + " kayit bulundu.");
    }

    /**
     * Dosyaya kaydet.
     */
    public void save() {
        for (Map.Entry<UUID, String> entry : ipCache.entrySet()) {
            dataConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "ip_data.yml kaydedilemedi!", e);
        }
    }

    /**
     * Oyuncunun kaydedilmis IP'sini al. null: kayit yok.
     */
    public String getSavedIp(UUID playerUuid) {
        return ipCache.get(playerUuid);
    }

    /**
     * Oyuncunun IP'sini kaydet/guncelle.
     */
    public void saveIp(UUID playerUuid, String ip) {
        ipCache.put(playerUuid, ip);
        dataConfig.set(playerUuid.toString(), ip);
        // Anlik kaydedelim
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "IP anlik kaydedilemedi: " + e.getMessage());
        }
    }

    /**
     * Oyuncunun IP kaydini sil (authreset icin).
     */
    public boolean resetIp(UUID playerUuid) {
        if (ipCache.containsKey(playerUuid)) {
            ipCache.remove(playerUuid);
            dataConfig.set(playerUuid.toString(), null);
            try {
                dataConfig.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "IP sifirlanamadi: " + e.getMessage());
            }
            return true;
        }
        return false;
    }

    /**
     * Oyuncunun IP'si kayitli IP ile ayni mi?
     */
    public boolean isKnownIp(UUID playerUuid, String currentIp) {
        String savedIp = getSavedIp(playerUuid);
        if (savedIp == null) return false;
        return savedIp.equals(currentIp);
    }
}
