
package net.mineaqua.afkregions.version;

import net.mineaqua.afkregions.AFKRegionsPlugin;
import net.mineaqua.afkregions.model.Region;
import net.mineaqua.afkregions.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LegacyAdapter implements VersionAdapter {
    private final AFKRegionsPlugin plugin;

    private final boolean titlesEnabled;
    private final int titleEvery;

    private final Map<UUID, Integer> lastTitleSecond = new HashMap<>();

    private static final Constructor<?> PACKET_TITLE_TIMES, PACKET_TITLE_TITLE, PACKET_TITLE_SUBTITLE;
    private static final Method GET_HANDLE, SEND_PACKET, CRAFT_CHAT_MESSAGE_FROM_STRING;
    private static final Class<?> ENUM_TITLE_ACTION_CLASS;
    private static final Field PLAYER_CONNECTION;

    static {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

            Class<?> iChatBaseComponentClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
            Class<?> packetPlayOutTitleClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle");
            ENUM_TITLE_ACTION_CLASS = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle$EnumTitleAction");

            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Class<?> craftChatMessageClass = Class.forName("org.bukkit.craftbukkit." + version + ".util.CraftChatMessage");

            Class<?> entityPlayerClass = Class.forName("net.minecraft.server." + version + ".EntityPlayer");
            Class<?> playerConnectionClass = Class.forName("net.minecraft.server." + version + ".PlayerConnection");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".Packet");

            GET_HANDLE = craftPlayerClass.getMethod("getHandle");
            PLAYER_CONNECTION = entityPlayerClass.getField("playerConnection");
            SEND_PACKET = playerConnectionClass.getMethod("sendPacket", packetClass);
            CRAFT_CHAT_MESSAGE_FROM_STRING = craftChatMessageClass.getMethod("fromString", String.class);

            PACKET_TITLE_TIMES = packetPlayOutTitleClass.getConstructor(
                    ENUM_TITLE_ACTION_CLASS, iChatBaseComponentClass, int.class, int.class, int.class
            );
            PACKET_TITLE_TITLE = packetPlayOutTitleClass.getConstructor(ENUM_TITLE_ACTION_CLASS, iChatBaseComponentClass);
            PACKET_TITLE_SUBTITLE = packetPlayOutTitleClass.getConstructor(ENUM_TITLE_ACTION_CLASS, iChatBaseComponentClass);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public LegacyAdapter(AFKRegionsPlugin plugin) {
        this.plugin = plugin;

        FileConfiguration config = plugin.getConfig();
        this.titlesEnabled = config.getBoolean("settings.titles_enabled", true);
        this.titleEvery = Math.max(0, config.getInt("settings.title_every_seconds", 0));
    }

    @Override
    public void onEnter(Player player, Region region) {
        handlePlayerTitles(player, region, "enter_title");
    }

    @Override
    public void onExit(Player player, Region region) {
        handlePlayerTitles(player, region, "exit_title");
    }

    private void handlePlayerTitles(Player player, Region region, String messageKey) {
        if (!titlesEnabled) return;

        Messages.TitleData titleData = plugin.messages().title(messageKey);
        String title = Messages.color(titleData.title().replace("{region}", region.name()));
        String subtitle = Messages.color(titleData.subtitle().replace("{region}", region.name()));

        sendLegacyTitle(player, title, subtitle, titleData.fadeIn(), titleData.stay(), titleData.fadeOut());

        lastTitleSecond.remove(player.getUniqueId());
    }

    @Override
    public void updateProgress(Player player, Region region, int elapsedSeconds, double progress) {
        if (!titlesEnabled || titleEvery <= 0) return;

        Integer last = lastTitleSecond.get(player.getUniqueId());
        if (last != null && (elapsedSeconds - last) < titleEvery) {
            return;
        }

        lastTitleSecond.put(player.getUniqueId(), elapsedSeconds);

        int percentage = (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * 100.0);

        String title = plugin.messages().raw("progress_title.title")
                .replace("{region}", region.name())
                .replace("{elapsed}", String.valueOf(elapsedSeconds))
                .replace("{max}", String.valueOf(region.durationSeconds()))
                .replace("{progress%}", String.valueOf(percentage));

        String subtitle = plugin.messages().raw("progress_title.subtitle")
                .replace("{region}", region.name())
                .replace("{elapsed}", String.valueOf(elapsedSeconds))
                .replace("{max}", String.valueOf(region.durationSeconds()))
                .replace("{progress%}", String.valueOf(percentage));

        sendLegacyTitle(player, Messages.color(title), Messages.color(subtitle), 5, 20, 5);
    }

    @Override
    public void clearUI(Player player) {
    }

    @Override
    public void shutdown() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void sendLegacyTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Object handle = GET_HANDLE.invoke(player);
            Object connection = PLAYER_CONNECTION.get(handle);

            Object titleArray = CRAFT_CHAT_MESSAGE_FROM_STRING.invoke(null, title);
            Object titleComponent = Array.get(titleArray, 0);

            Object subtitleArray = CRAFT_CHAT_MESSAGE_FROM_STRING.invoke(null, subtitle);
            Object subtitleComponent = Array.get(subtitleArray, 0);

            Object timesAction = Enum.valueOf((Class<Enum>) ENUM_TITLE_ACTION_CLASS, "TIMES");
            Object titleAction = Enum.valueOf((Class<Enum>) ENUM_TITLE_ACTION_CLASS, "TITLE");
            Object subtitleAction = Enum.valueOf((Class<Enum>) ENUM_TITLE_ACTION_CLASS, "SUBTITLE");

            Object timesPacket = PACKET_TITLE_TIMES.newInstance(timesAction, titleComponent, fadeIn, stay, fadeOut);
            Object titlePacket = PACKET_TITLE_TITLE.newInstance(titleAction, titleComponent);
            Object subtitlePacket = PACKET_TITLE_SUBTITLE.newInstance(subtitleAction, subtitleComponent);

            SEND_PACKET.invoke(connection, timesPacket);
            SEND_PACKET.invoke(connection, titlePacket);
            SEND_PACKET.invoke(connection, subtitlePacket);
        } catch (Exception ignored) {
        }
    }
}
