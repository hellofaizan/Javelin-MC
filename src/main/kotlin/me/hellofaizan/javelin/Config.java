package me.hellofaizan.javelin;

import org.bukkit.configuration.file.FileConfiguration;

public class Config {
    public static FileConfiguration config;

    public static void init() {
        config = Javelin.getPlugin(Javelin.class).getConfig();

        config.addDefault("Unit", "Blocks");
    }

    public static String getUnitFromConfig() {
        return config.getString("Unit");
    }
}
