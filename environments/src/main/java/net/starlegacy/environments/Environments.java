package net.starlegacy.environments;

import net.starlegacy.cache.space.PlanetCache;
import net.starlegacy.database.schema.space.Planet;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class Environments extends JavaPlugin implements Listener {

    public static Environments getInstance() {
        return (Environments) Bukkit.getPluginManager().getPlugin("Environments");
    }

    public static boolean isWearingEnvironmentSuit(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) {
            return false;
        }

        List<String> lore = helmet.getLore();

        if (lore != null && (lore.contains("Module: environment") || lore.contains("Module: ENVIRONMENT"))) {
            return true;
        }

        return Arrays.stream(player.getInventory().getArmorContents())
                .allMatch(i -> i != null && i.getType().name().contains("CHAIN"));
    }

    public static boolean isBreathable(World world) {
        return getInstance().getConfig().getBoolean(getPlanetKey(world, "breathable"));
    }

    public static boolean isRadioactive(World world) {
        return getInstance().getConfig().getBoolean(getPlanetKey(world, "radioactive"), false);
    }

    public static int getTemperature(World world) {
        return getInstance().getConfig().getInt(getPlanetKey(world, "temperature"), 0);
    }

    public static double getGravity(World world) {
        return getInstance().getConfig().getDouble(getPlanetKey(world, "gravity"), 1.0);
    }

    public static boolean isCold(World world) {
        return getTemperature(world) < 0;
    }

    public static boolean isHot(World world) {
        return getTemperature(world) > 0;
    }

    private static String getPlanetKey(World world, String key) {
        return "planets." + world.getName().toLowerCase() + "." + key;
    }

    @Override
    public void onEnable() {
        loadConfig();
        DebuffManager.init();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.DROWNING) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        if (!isWearingEnvironmentSuit((Player) event.getEntity())) {
            return;
        }

        event.setCancelled(true);
    }

    private void loadConfig() {
        saveConfig();
        for (Planet planet : PlanetCache.INSTANCE.getAll()) {
            if (Bukkit.getWorld(planet.getPlanetWorld()) == null) {
                continue;
            }

            String prefix = "planets." + planet.get_id() + ".";
            getConfig().set(prefix + "breathable", getConfig().getBoolean(prefix + "breathable", true));
            getConfig().set(prefix + "radioactive", getConfig().getBoolean(prefix + "radioactive", false));
            getConfig().set(prefix + "temperature", getConfig().getInt(prefix + "temperature", 0));
            getConfig().set(prefix + "gravity", getConfig().getDouble(prefix + "gravity", 1));
        }
        saveConfig();
    }
}
