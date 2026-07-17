package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 능력 아이템의 생성과 판정을 한 곳으로 모읍니다. 기존 6종이 각자 createItem()과
 * isHoldingX()를 복붙하고 있었고 판정 로직도 완전히 동일했습니다.
 *
 * 판정을 표시이름 문자열로 하는 것은 기존 코드의 방식을 그대로 따른 것입니다.
 * PDC 기반으로 바꾸면 모루로 이름만 바꾼 잡템이 귀속 아이템으로 인정되는 문제까지
 * 해결되지만, 그것은 이번 범위가 아닙니다.
 */
public final class AbilityItems {

    /** 모든 능력 아이템의 표시이름에 들어가는 공통 태그. 귀속 판정에 씁니다. */
    public static final String BOUND_TAG = "[능력]";

    private AbilityItems() {
    }

    /**
     * 표시이름이 color+tag인 귀속 아이템을 만듭니다.
     *
     * @param tag "[능력] "으로 시작해야 합니다. 안 그러면 귀속 판정(isBound)에 걸리지 않아
     *            사망 시 드랍되고 버릴 수 있게 됩니다.
     */
    public static ItemStack create(Material type, ChatColor color, String tag) {
        if (!tag.startsWith(BOUND_TAG)) {
            throw new IllegalArgumentException("능력 아이템 태그는 \"" + BOUND_TAG + "\"으로 시작해야 합니다: " + tag);
        }
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + tag);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** p가 주손에 해당 태그의 아이템을 들고 있는지. */
    public static boolean isHolding(Player p, Material type, String tag) {
        ItemStack main = p.getInventory().getItemInMainHand();
        return matches(main, type, tag);
    }

    /** 귀속 아이템("[능력]" 태그)인지. */
    public static boolean isBound(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains(BOUND_TAG);
    }

    private static boolean matches(ItemStack item, Material type, String tag) {
        return item != null && item.getType() == type
                && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains(tag);
    }
}
