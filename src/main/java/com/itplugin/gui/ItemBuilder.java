package com.itplugin.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta  meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(color(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        if (meta != null)
            meta.setLore(Arrays.stream(lines).map(this::color).collect(Collectors.toList()));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null)
            meta.setLore(lines.stream().map(this::color).collect(Collectors.toList()));
        return this;
    }

    /** Sets the CustomModelData value used by the resource pack to swap the item texture. */
    public ItemBuilder customModelData(int data) {
        if (meta != null) meta.setCustomModelData(data);
        return this;
    }

    public ItemBuilder glowing(boolean glow) {
        if (glow && meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
