package com.setlobbyspawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 全服出生点 + 入服欢迎插件。
 *
 * 兼容 Paper/Spigot 1.8 ~ 最新版：
 *  - 编译为 Java 8 字节码，所有 JVM 均可加载；
 *  - 只直接调用 1.8 就存在的 API；
 *  - 版本较新的 API（标题 1.11+、死亡重生点 1.12+）用反射调用，
 *    老版本找不到方法时自动降级，不会报错。
 */
public final class SetLobbySpawn extends JavaPlugin implements Listener {

    private static final int MAX_LINES = 5;

    // ---- 反射缓存：新版本才有 API，老版本为 null，自动降级 ----
    private static Method SEND_TITLE_TIMED;   // Player#sendTitle(String,String,int,int,int)  1.12+
    private static Method SEND_TITLE_DOUBLE;  // Player#sendTitle(String,String)             1.11+
    private static Method SEND_TITLE_SINGLE;  // Player#sendTitle(String)                     1.11+
    private static Method SET_RESPAWN;        // PlayerRespawnEvent#setRespawnLocation(Location) 1.12+
    private static boolean methodsScanned = false;
    private static boolean respawnFallbackLogged = false;

    private boolean spawnEnabled = true;
    private String spawnWorld = "world";
    private double spawnX = 0;
    private double spawnY = 64;
    private double spawnZ = 0;
    private float spawnYaw = 0;
    private float spawnPitch = 0;
    private List<String> welcomeLines = new ArrayList<>();
    private List<String> titleLines = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        scanMethods();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SetLobbySpawn 已启用，版本 " + getDescription().getVersion()
                + "，兼容 Paper/Spigot 1.8+");
    }

    @Override
    public void onDisable() {
        getLogger().info("SetLobbySpawn 已停用");
    }

    /** 扫描当前服务器可用的新版本 API（找不到则置 null，走降级逻辑） */
    private static synchronized void scanMethods() {
        if (methodsScanned) {
            return;
        }
        methodsScanned = true;
        try {
            SEND_TITLE_TIMED = Player.class.getMethod(
                    "sendTitle", String.class, String.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            SEND_TITLE_DOUBLE = Player.class.getMethod("sendTitle", String.class, String.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            SEND_TITLE_SINGLE = Player.class.getMethod("sendTitle", String.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            SET_RESPAWN = PlayerRespawnEvent.class.getMethod("setRespawnLocation", Location.class);
        } catch (NoSuchMethodException ignored) {
        }
    }

    /** 从配置文件读取全部设置 */
    private void loadConfigValues() {
        FileConfiguration cfg = getConfig();
        spawnEnabled = cfg.getBoolean("spawn.enabled", true);
        spawnWorld = cfg.getString("spawn.world", "world");
        spawnX = cfg.getDouble("spawn.x", 0);
        spawnY = cfg.getDouble("spawn.y", 64);
        spawnZ = cfg.getDouble("spawn.z", 0);
        spawnYaw = (float) cfg.getDouble("spawn.yaw", 0);
        spawnPitch = (float) cfg.getDouble("spawn.pitch", 0);
        welcomeLines = normalize(cfg.getStringList("welcome.lines"));
        titleLines = normalize(cfg.getStringList("welcome-title.lines"));
    }

    /** 只保留前 MAX_LINES 行，不足则补空字符串，方便按行号定位 */
    private List<String> normalize(List<String> raw) {
        List<String> list = new ArrayList<>(raw);
        while (list.size() < MAX_LINES) {
            list.add("");
        }
        if (list.size() > MAX_LINES) {
            list = new ArrayList<>(list.subList(0, MAX_LINES));
        }
        return list;
    }

    /** 过滤掉空行，只保留实际要显示的内容 */
    private List<String> nonEmpty(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String s : lines) {
            if (s != null && !s.trim().isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** 根据配置计算出生点位置，世界不存在时返回 null */
    private Location getSpawnLocation() {
        World world = Bukkit.getWorld(spawnWorld);
        if (world == null) {
            return null;
        }
        return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    /** 转换 & 颜色符（支持 &0-9 &a-f &k-o &r 及 &x 十六进制颜色） */
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /** 发送入服欢迎标题：1.11+ 用原生标题；1.8-1.10 无标题 API，降级为聊天消息 */
    private void sendWelcomeTitle(Player player, String title, String subtitle) {
        scanMethods();
        try {
            if (SEND_TITLE_TIMED != null) {
                SEND_TITLE_TIMED.invoke(player, title, subtitle, 10, 70, 20);
                return;
            }
            if (SEND_TITLE_DOUBLE != null) {
                SEND_TITLE_DOUBLE.invoke(player, title, subtitle);
                return;
            }
            if (SEND_TITLE_SINGLE != null) {
                SEND_TITLE_SINGLE.invoke(player, title);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // 反射失败不影响，继续走降级
        }
        if (title != null && !title.isEmpty()) {
            player.sendMessage(color("&6[欢迎标题] " + title));
        }
        if (subtitle != null && !subtitle.isEmpty()) {
            player.sendMessage(color("&7" + subtitle.replace('\n', ' ')));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&e用法: /sls help"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help":
                return showHelp(sender);
            case "spawn":
                return spawnCommand(sender, args);
            case "saywelcome":
            case "saywecome": // 兼容少打一个 l 的写法
                return setLineCommand(sender, args, "welcome.lines", "saywelcome");
            case "welcometitle":
                return setLineCommand(sender, args, "welcome-title.lines", "welcometitle");
            case "reload":
                return reloadCommand(sender);
            default:
                sender.sendMessage(color("&c未知子指令，请输入 /sls help 查看帮助"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help", "spawn", "saywelcome", "welcometitle", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return Arrays.asList("true", "false");
        }
        return null;
    }

    private boolean showHelp(CommandSender sender) {
        sender.sendMessage(color("&6========== SetLobbySpawn 帮助 =========="));
        sender.sendMessage(color("&e/sls spawn <true|false> &7- 站在出生点执行，设置全服出生点"));
        sender.sendMessage(color("&7  true : 每次入服、死亡后都回到此处"));
        sender.sendMessage(color("&7  false: 仅第一次入服出生在此"));
        sender.sendMessage(color("&e/sls spawn &7- 查看当前出生点设置"));
        sender.sendMessage(color("&e/sls saywelcome <行号1-5> <内容> &7- 设置入服欢迎语(最多5行，支持&颜色符)"));
        sender.sendMessage(color("&e/sls welcometitle <行号1-5> <内容> &7- 设置入服欢迎标题(第1行最大，其余依次减小)"));
        sender.sendMessage(color("&e/sls reload &7- 修改配置文件后重载，立即生效"));
        sender.sendMessage(color("&e/sls help &7- 查看本帮助"));
        sender.sendMessage(color("&7配置文件: plugins/SetLobbySpawn/config.yml"));
        return true;
    }

    private boolean spawnCommand(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(color("&c请以玩家身份站在出生点执行此指令"));
            return true;
        }
        Player player = (Player) sender;

        // 不带参数: 查看当前出生点设置
        if (args.length == 1) {
            Location loc = getSpawnLocation();
            sender.sendMessage(color("&6当前全服出生点:"));
            sender.sendMessage(color("&e模式: " + (spawnEnabled
                    ? "&atrue(每次入服/死亡后回到此处)"
                    : "&7false(仅第一次入服出生在此)")));
            if (loc == null) {
                sender.sendMessage(color("&c出生点所在世界不存在: " + spawnWorld));
            } else {
                sender.sendMessage(color("&e世界/坐标: &7" + loc.getWorld().getName()
                        + " " + String.format(Locale.US, "%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ())));
            }
            return true;
        }

        String mode = args[1].toLowerCase();
        if (!mode.equals("true") && !mode.equals("false")) {
            sender.sendMessage(color("&c用法: /sls spawn <true|false>"));
            return true;
        }
        boolean enabled = Boolean.parseBoolean(mode);
        Location loc = player.getLocation();
        FileConfiguration cfg = getConfig();
        cfg.set("spawn.enabled", enabled);
        cfg.set("spawn.world", loc.getWorld().getName());
        cfg.set("spawn.x", loc.getX());
        cfg.set("spawn.y", loc.getY());
        cfg.set("spawn.z", loc.getZ());
        cfg.set("spawn.yaw", (double) loc.getYaw());
        cfg.set("spawn.pitch", (double) loc.getPitch());
        saveConfig();
        loadConfigValues();
        sender.sendMessage(color("&a出生点已设置为当前位置: &7" + loc.getWorld().getName()
                + " " + String.format(Locale.US, "%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ())));
        sender.sendMessage(color("&a模式: " + (enabled
                ? "&etrue(每次入服、死亡后回到此处)"
                : "&efalse(仅第一次入服出生在此)")));
        return true;
    }

    /** 设置欢迎语/欢迎标题的指定行 */
    private boolean setLineCommand(CommandSender sender, String[] args, String configPath, String subName) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(color("&c用法: /sls " + subName + " <行号1-" + MAX_LINES + "> <内容>"));
            return true;
        }
        int line;
        try {
            line = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(color("&c行号必须是整数(1-" + MAX_LINES + ")"));
            return true;
        }
        if (line < 1 || line > MAX_LINES) {
            sender.sendMessage(color("&c行号范围: 1-" + MAX_LINES));
            return true;
        }
        // 内容可能含空格，把剩余参数重新拼起来
        String text = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
        if (text.equals("\"\"")) {
            text = ""; // 用 "" 清空该行
        }
        List<String> lines = new ArrayList<>(getConfig().getStringList(configPath));
        while (lines.size() < MAX_LINES) {
            lines.add("");
        }
        lines.set(line - 1, text);
        getConfig().set(configPath, lines);
        saveConfig();
        loadConfigValues();
        if (text.isEmpty()) {
            sender.sendMessage(color("&a已清空第 " + line + " 行"));
        } else {
            sender.sendMessage(color("&a已设置第 " + line + " 行: " + text));
        }
        return true;
    }

    private boolean reloadCommand(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return true;
        }
        reloadConfig();
        loadConfigValues();
        sender.sendMessage(color("&a配置文件已重载，所有设置已生效"));
        return true;
    }

    private boolean hasAdmin(CommandSender sender) {
        if (sender.hasPermission("setlobbyspawn.admin")) {
            return true;
        }
        sender.sendMessage(color("&c你没有权限执行此指令"));
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();

        // 出生点传送: 第一次入服必定传送; 模式为 true 时每次入服都传送
        if (firstJoin || spawnEnabled) {
            Location target = getSpawnLocation();
            if (target != null) {
                Location dest = target.clone();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (player.isOnline()) {
                        player.teleport(dest);
                    }
                }, 5L);
            }
        }

        // 入服欢迎语
        List<String> messages = nonEmpty(welcomeLines);
        if (!messages.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) {
                    return;
                }
                for (String msg : messages) {
                    player.sendMessage(color(msg));
                }
            }, 10L);
        }

        // 入服欢迎标题: 第1行为主标题(最大)，其余行合并进副标题(依次减小)
        List<String> titles = nonEmpty(titleLines);
        if (!titles.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) {
                    return;
                }
                String mainTitle = color(titles.get(0));
                StringBuilder subtitle = new StringBuilder();
                for (int i = 1; i < titles.size(); i++) {
                    if (subtitle.length() > 0) {
                        subtitle.append('\n');
                    }
                    subtitle.append(color(titles.get(i)));
                }
                sendWelcomeTitle(player, mainTitle, subtitle.toString());
            }, 30L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // 模式为 true 时，死亡后回到出生点
        if (!spawnEnabled) {
            return;
        }
        Location target = getSpawnLocation();
        if (target == null) {
            return;
        }
        scanMethods();
        if (SET_RESPAWN != null) {
            try {
                SET_RESPAWN.invoke(event, target);
            } catch (ReflectiveOperationException ignored) {
            }
        } else if (!respawnFallbackLogged) {
            respawnFallbackLogged = true;
            getLogger().warning("当前服务器版本过旧(需 1.12+)，无法设置死亡重生点，"
                    + "'死亡回出生点'功能不可用");
        }
    }
}
