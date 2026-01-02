package me.luke.wilberhelper;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

public final class WilberHelper extends JavaPlugin {

    private Economy economy;

    // =========================================================
    // STEREO REGION (spawn)
    // =========================================================
    private static final String STEREO_WORLD = "spawn";

    private static final int MIN_X = -53;
    private static final int MAX_X = -39;
    private static final int MIN_Y = -14;
    private static final int MAX_Y = -9;
    private static final int MIN_Z = -128;
    private static final int MAX_Z = -108;

    private static final String[] POWER_ON_COMMANDS = {
            "setblock -52 -11 -125 minecraft:redstone_block",
            "setblock -40 -11 -125 minecraft:redstone_block"
    };

    private static final String[] POWER_OFF_COMMANDS = {
            "setblock -52 -11 -125 minecraft:air",
            "setblock -40 -11 -125 minecraft:air"
    };

    private static final String[][] GLASS_LOCATIONS = {
            {"-47", "-15", "-124"},
            {"-46", "-15", "-123"},
            {"-45", "-15", "-124"},
            {"-46", "-15", "-121"},
            {"-47", "-15", "-120"},
            {"-45", "-15", "-120"},
            {"-46", "-15", "-119"}
    };

    private static final String[] GLASS_COLORS = {
            "red_stained_glass",
            "yellow_stained_glass",
            "lime_stained_glass",
            "orange_stained_glass",
            "light_blue_stained_glass"
    };

    private static final int GLASS_ANIMATION_DURATION = 1500;

    private boolean isInStereoRegion(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(STEREO_WORLD)) return false;

        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();

        return x >= MIN_X && x <= MAX_X
                && y >= MIN_Y && y <= MAX_Y
                && z >= MIN_Z && z <= MAX_Z;
    }

    // =========================================================
    // GLASS ANIMATION
    // =========================================================
    private void startGlassAnimation() {

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {

                if (elapsed >= GLASS_ANIMATION_DURATION) {
                    for (String[] g : GLASS_LOCATIONS) {
                        Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                "setblock " + g[0] + " " + g[1] + " " + g[2] + " minecraft:orange_stained_glass"
                        );
                    }
                    cancel();
                    return;
                }

                for (String[] g : GLASS_LOCATIONS) {
                    String color = GLASS_COLORS[ThreadLocalRandom.current().nextInt(GLASS_COLORS.length)];
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(),
                            "setblock " + g[0] + " " + g[1] + " " + g[2] + " minecraft:" + color
                    );
                }

                elapsed += 10;
            }

        }.runTaskTimer(this, 0L, 15L);
    }

    // =========================================================
    // BELL RINGS
    // =========================================================
    private void scheduleBellRings(long startDelay) {

        new BukkitRunnable() {
            int rings = 0;

            @Override
            public void run() {

                if (rings >= 3) {
                    cancel();
                    return;
                }

                for (String cmd : POWER_ON_COMMANDS) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }

                Bukkit.getScheduler().runTaskLater(
                        this.getPlugin(),
                        () -> {
                            for (String cmd : POWER_OFF_COMMANDS) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                            }
                        },
                        1L
                );

                rings++;
            }

            private JavaPlugin getPlugin() {
                return JavaPlugin.getProvidingPlugin(getClass());
            }

        }.runTaskTimer(this, startDelay, 8L);
    }

    // =========================================================
    // GOLEM DATA
    // =========================================================
    private static final String[] POSES = {
            "standing", "running", "star", "sitting"
    };

    private static final String[][] STATUES = {
            {"-43", "-14", "-125", "west"},
            {"-49", "-14", "-125", "east"},
            {"-49", "-14", "-119", "south"},
            {"-43", "-14", "-119", "south"}
    };

    private static final String[] MUSIC_POSITIONS = {
            "-48 -12 -128",
            "-44 -12 -128",
            "-49 -12 -119",
            "-43 -12 -119"
    };

    private static final String MUSIC_COMMAND_FORMAT =
            "playsound minecraft:music_disc.lava_chicken master @a %s 1.5 %s";

    private static final long ADDITIONAL_BELL_START_TICKS = (49 * 20L) - 4; // 4 ticks before 49 seconds

    // =========================================================
    // ENABLE
    // =========================================================
    @Override
    public void onEnable() {
        getLogger().info("WilberHelper enabled");

        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling WilberHelper.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;

        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) return false;

        economy = rsp.getProvider();
        return economy != null;
    }

    // =========================================================
    // COMMAND
    // =========================================================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!command.getName().equalsIgnoreCase("wilber")) return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command must be run by a player.");
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("stereo")) {
            player.sendMessage("§cUsage: /wilber stereo");
            return true;
        }

        // 🔒 REGION LOCK
        if (!isInStereoRegion(player)) {
            player.sendMessage("§cYou must be standing at Wilber’s stereo to do that.");
            return true;
        }

        // ---- ECONOMY ----
        double balance = economy.getBalance(player);
        double cost = balance * 0.005;

        if (cost <= 0 || balance < cost) {
            player.sendMessage("§cYou cannot afford the offering.");
            return true;
        }

        economy.withdrawPlayer(player, cost);

        // ---- RARITY ----
        double pitch = 1.0;
        int roll = ThreadLocalRandom.current().nextInt(100);

        if (roll < 5) pitch = 0.8;
        else if (roll < 10) pitch = 1.1;

        long bellDelay = Math.round(184 / pitch);

        // ---- MUSIC ----
        for (String position : MUSIC_POSITIONS) {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    MUSIC_COMMAND_FORMAT.formatted(position, pitch)
            );
        }

        // ---- EFFECTS ----
        scheduleBellRings(bellDelay);
        scheduleBellRings(ADDITIONAL_BELL_START_TICKS);
        startGolemDance();
        startGlassAnimation();

        return true;
    }

    // =========================================================
    // GOLEM DANCE
    // =========================================================
    private void startGolemDance() {

        new BukkitRunnable() {

            int ticks = 0;
            int beat = 0;

            @Override
            public void run() {

                if (ticks >= 1500) {
                    cancel();
                    return;
                }

                boolean advanceBeat =
                        (beat < 3) ? (ticks % 8 == 0) : (ticks % 4 == 0);

                if (advanceBeat) {
                    String pose = POSES[beat % POSES.length];

                    for (String[] s : STATUES) {
                        Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                String.format(
                                        "setblock %s %s %s minecraft:waxed_copper_golem_statue[copper_golem_pose=%s,facing=%s]",
                                        s[0], s[1], s[2], pose, s[3]
                                )
                        );
                    }

                    beat++;
                }

                ticks += 4;
            }

        }.runTaskTimer(this, 0L, 4L);
    }
}
