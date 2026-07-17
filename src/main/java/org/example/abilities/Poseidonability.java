package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * 포세이돈: 전용 삼지창을 지급합니다. 내구도 무한, 공격력 없음(순수 이동/유틸용).
 * 급류(Riptide) III가 적용되어 있어 물속/빗속에서 우클릭을 누르고 있다가 놓으면
 * 바닐라 급류 효과로 회전하며 하늘로 솟구칩니다 - 별도 커스텀 코드 없이 인챈트만으로
 * 구현되며, 대미지가 0이라 이 상태에서도 무기로 쓸 수 없습니다.
 * 패시브: 물속에 있으면 힘 1 + 돌고래의 우아함을 얻습니다.
 */
public class Poseidonability implements Ability {

    private static final String ITEM_TAG = "[능력] 포세이돈의 삼지창";

    @Override
    public String getName() {
        return "포세이돈";
    }

    @SuppressWarnings("removal")
    private ItemStack createItem() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + ITEM_TAG);
            meta.setUnbreakable(true);

            // 삼지창 기본 공격력(바닐라 기준 9)을 완전히 상쇄시켜, 급류로 날아오르는
            // 상태에서도 이 삼지창으로는 대미지를 줄 수 없는 순수 이동용 아이템으로 만듭니다.
            AttributeModifier noDamage = new AttributeModifier(
                    UUID.randomUUID(), "poseidon_no_damage", -9.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, noDamage);

            meta.addEnchant(Enchantment.RIPTIDE, 3, true);

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);

            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        p.getInventory().addItem(createItem());

        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(전용 삼지창은 내구도가 무한하고 대미지가 없습니다.)");
            p.sendMessage(ChatColor.GRAY + "(물속/빗속에서 우클릭을 눌렀다 놓으면 급류 효과로 하늘로 솟구칩니다.)");
            p.sendMessage(ChatColor.GRAY + "(패시브 효과: 물속에 있으면 힘 1과 돌고래의 우아함 버프를 얻습니다.)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]이(가) 적용되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        // 상시 유지되는 상태가 없습니다. 아이템 회수는 AbilityManager가 공통 처리합니다.
    }

    @Override
    public void onPassiveTick(Player p) {
        if (p.isInWater()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 0));
        }
    }
}