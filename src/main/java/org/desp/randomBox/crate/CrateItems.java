package org.desp.randomBox.crate;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.desp.randomBox.crate.opening.OpeningConfig;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** GUI 장식 아이템을 만드는 도구. */
public final class CrateItems {

    private CrateItems() {
    }

    public static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** 텍스처 해시(EC SkinURL)로 머리 아이템을 만든다. */
    public static ItemStack head(String skin, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (skin != null && !skin.isEmpty()) {
            try {
                String url = skin.startsWith("http") ? skin : "http://textures.minecraft.net/texture/" + skin;
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(skin.getBytes(StandardCharsets.UTF_8)), "crate");
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(new URL(url));
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
            } catch (Exception ignored) {
            }
        }
        if (name != null) {
            meta.displayName(name);
        }
        if (lore != null) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack decor(OpeningConfig.DecorItem decor) {
        if (decor.getMaterial() == Material.AIR || decor.getAmount() <= 0) {
            return null;
        }
        Component name = decor.getDisplayName() == null ? Component.text(" ") : CrateText.mm(decor.getDisplayName());
        List<Component> lore = CrateText.mm(decor.getLore());
        if (decor.getMaterial() == Material.PLAYER_HEAD && decor.getSkin() != null) {
            return head(decor.getSkin(), name, lore);
        }
        ItemStack item = new ItemStack(decor.getMaterial(), Math.max(1, decor.getAmount()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}
