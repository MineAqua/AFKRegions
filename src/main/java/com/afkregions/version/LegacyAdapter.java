
package com.afkregions.version;

import com.afkregions.AFKRegionsPlugin;
import com.afkregions.model.Region;
import com.afkregions.util.Messages;
import org.bukkit.entity.Player;

public class LegacyAdapter implements VersionAdapter {
    private final AFKRegionsPlugin plugin;
    private final boolean titlesEnabled;
    private final int titleEvery;
    private final java.util.Map<java.util.UUID, Integer> lastTitleSecond = new java.util.HashMap<>();


    public LegacyAdapter(com.afkregions.AFKRegionsPlugin plugin) {
        this.plugin = plugin;
        this.titlesEnabled = plugin.getConfig().getBoolean("settings.titles_enabled", true);
        this.titleEvery = Math.max(0, plugin.getConfig().getInt("settings.title_every_seconds", 0));
    }

    @Override
    public void onEnter(Player p, Region r) {
        if (!titlesEnabled) return;

        Messages.TitleSpec ts = plugin.messages().title("enter_title");
        // Construye textos con colores ya resueltos
        String title = Messages.color(ts.title.replace("{region}", r.name));
        String sub = Messages.color(ts.subtitle.replace("{region}", r.name));

        // Envía el título por paquetes NMS (1.8) con tiempos fi/st/fo
        sendTitle18(p, title, sub, ts.fi, ts.st, ts.fo);

        // Al entrar, resetea el rate-limit de títulos periódicos
        lastTitleSecond.remove(p.getUniqueId());
    }

    @Override
    public void onExit(Player p, Region r) {
        if (!titlesEnabled) return;
        lastTitleSecond.remove(p.getUniqueId());
        Messages.TitleSpec ts = plugin.messages().title("exit_title");
        sendTitle18(p,
                Messages.color(ts.title.replace("{region}", r.name)),
                Messages.color(ts.subtitle.replace("{region}", r.name)),
                ts.fi, ts.st, ts.fo);
    }


    @Override
    public void updateProgress(Player p, Region r, int elapsedSeconds, double progress) {
        if (!titlesEnabled || titleEvery <= 0) return;

        Integer last = lastTitleSecond.get(p.getUniqueId());
        if (last != null && (elapsedSeconds - last) < titleEvery) return;
        lastTitleSecond.put(p.getUniqueId(), elapsedSeconds);

        int pct = (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * 100.0);

        String t = plugin.messages().raw("progress_title.title")
                .replace("{region}", r.name)
                .replace("{elapsed}", String.valueOf(elapsedSeconds))
                .replace("{max}", String.valueOf(r.durationSeconds))
                .replace("{progress%}", String.valueOf(pct));

        String s = plugin.messages().raw("progress_title.subtitle")
                .replace("{region}", r.name)
                .replace("{elapsed}", String.valueOf(elapsedSeconds))
                .replace("{max}", String.valueOf(r.durationSeconds))
                .replace("{progress%}", String.valueOf(pct));

        sendTitle18(p, Messages.color(t), Messages.color(s), 5, 20, 5);
    }

    @Override
    public void clearUI(Player p) {
    }

    @Override
    public void shutdown() {
    }

    // ====== Reflexión cacheada para TITLES en 1.8.x (robusto con colores) ======
    private static Class<?> C_PacketPlayOutTitle, C_IChatBaseComponent, C_EnumTitleAction;
    private static Class<?> C_CraftPlayer, C_EntityPlayer, C_PlayerConnection, C_Packet;
    private static Class<?> C_CraftChatMessage;
    private static java.lang.reflect.Method M_getHandle, M_sendPacket, M_CraftChatMessage_fromString;
    private static java.lang.reflect.Field F_playerConnection;
    private static java.lang.reflect.Constructor<?> CTOR_PacketTitle_Times, CTOR_PacketTitle_Title, CTOR_PacketTitle_Subtitle;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initReflect() throws Exception {
        if (C_PacketPlayOutTitle != null) return; // ya listo

        String v = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3]; // v1_8_R3
        // NMS
        C_PacketPlayOutTitle = Class.forName("net.minecraft.server." + v + ".PacketPlayOutTitle");
        C_IChatBaseComponent = Class.forName("net.minecraft.server." + v + ".IChatBaseComponent");
        C_EnumTitleAction = Class.forName("net.minecraft.server." + v + ".PacketPlayOutTitle$EnumTitleAction");

        // CraftBukkit
        C_CraftPlayer = Class.forName("org.bukkit.craftbukkit." + v + ".entity.CraftPlayer");
        C_CraftChatMessage = Class.forName("org.bukkit.craftbukkit." + v + ".util.CraftChatMessage");

        // Entity / connection / packet
        C_EntityPlayer = Class.forName("net.minecraft.server." + v + ".EntityPlayer");
        C_PlayerConnection = Class.forName("net.minecraft.server." + v + ".PlayerConnection");
        C_Packet = Class.forName("net.minecraft.server." + v + ".Packet");

        // Métodos/constructores
        M_getHandle = C_CraftPlayer.getMethod("getHandle");
        F_playerConnection = C_EntityPlayer.getField("playerConnection");
        M_sendPacket = C_PlayerConnection.getMethod("sendPacket", C_Packet);
        M_CraftChatMessage_fromString = C_CraftChatMessage.getMethod("fromString", String.class);

        // PacketPlayOutTitle ctors
        CTOR_PacketTitle_Times = C_PacketPlayOutTitle.getConstructor(C_EnumTitleAction, C_IChatBaseComponent, int.class, int.class, int.class);
        CTOR_PacketTitle_Title = C_PacketPlayOutTitle.getConstructor(C_EnumTitleAction, C_IChatBaseComponent);
        CTOR_PacketTitle_Subtitle = C_PacketPlayOutTitle.getConstructor(C_EnumTitleAction, C_IChatBaseComponent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void sendTitle18(org.bukkit.entity.Player p, String title, String subtitle, int fi, int st, int fo) {
        try {
            initReflect();
            Object handle = M_getHandle.invoke(p);
            Object conn = F_playerConnection.get(handle);

            // Construye IChatBaseComponent[] desde legacy con colores (CraftChatMessage maneja §/& bien)
            Object arrTitle = M_CraftChatMessage_fromString.invoke(null, title);
            Object compTitle = java.lang.reflect.Array.get((Object[]) arrTitle, 0);
            Object arrSub = M_CraftChatMessage_fromString.invoke(null, subtitle);
            Object compSub = java.lang.reflect.Array.get((Object[]) arrSub, 0);

            Object ACTION_TIMES = Enum.valueOf((Class<Enum>) C_EnumTitleAction, "TIMES");
            Object ACTION_TITLE = Enum.valueOf((Class<Enum>) C_EnumTitleAction, "TITLE");
            Object ACTION_SUB = Enum.valueOf((Class<Enum>) C_EnumTitleAction, "SUBTITLE");

            Object pktTimes = CTOR_PacketTitle_Times.newInstance(ACTION_TIMES, compTitle, fi, st, fo);
            Object pktTitle = CTOR_PacketTitle_Title.newInstance(ACTION_TITLE, compTitle);
            Object pktSub = CTOR_PacketTitle_Subtitle.newInstance(ACTION_SUB, compSub);

            M_sendPacket.invoke(conn, pktTimes);
            M_sendPacket.invoke(conn, pktTitle);
            M_sendPacket.invoke(conn, pktSub);
        } catch (Exception e) {
            // Silencioso para no spamear logs en 1.8; si quieres debug, loggea aquí.
        }
    }
}
