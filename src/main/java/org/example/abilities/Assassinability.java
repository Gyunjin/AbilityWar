package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.example.game.BackstabMath;

/**
 * 암살자: 어둠 속에 숨어 단 한 번의 강력한 일격을 노립니다.
 *
 * 은신은 Vanish(hidePlayer)를 씁니다. 포션 투명화는 갑옷과 든 검이 그대로 보여서
 * 암살자에게는 쓸 수 없습니다(Vanish 주석 참고).
 */
public class Assassinability implements Ability {

    private static final String ITEM_TAG = "[능력] 그림자 은신";
    private static final long COOLDOWN_MS = 20000;
    private static final int HIDE_TICKS = 100;          // 5초
    private static final double BACKSTAB_MULTIPLIER = 2.5;
    private static final double FRONT_MULTIPLIER = 2.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_MS);
    private BukkitTask expiryTask;
    private boolean hiding = false;

    @Override
    public String getName() {
        return "암살자";
    }

    @Override
    public void resetCooldown() {
        cooldown.reset();
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        p.getInventory().addItem(AbilityItems.create(Material.INK_SAC, ChatColor.DARK_GRAY, ITEM_TAG));

        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(액티브: 먹물 주머니 우클릭 시 5초간 완전히 사라지고 신속 2를 얻습니다.)");
            p.sendMessage(ChatColor.GRAY + "(은신 중 공격하면 대미지 2배. 등 뒤에서 찌르면 2.5배.)");
            p.sendMessage(ChatColor.RED + "(공격하는 순간 은신이 풀립니다. 쿨타임: 20초)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]의 장비가 지급되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        cancelExpiry();
        hiding = false;
        if (p == null) return;
        // show()가 누락되면 영구 투명 버그가 됩니다. 중복 호출은 무해합니다.
        Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        p.removePotionEffect(PotionEffectType.SPEED);
    }

    /** 안전망: 은신 상태가 아닌데 숨겨진 채로 남아 있으면 되돌립니다. */
    @Override
    public void onPassiveTick(Player p) {
        if (!hiding && Vanish.isHidden(p)) {
            Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!AbilityItems.isHolding(p, Material.INK_SAC, ITEM_TAG)) return;

        event.setCancelled(true);
        if (hiding) return;
        if (!cooldown.tryUse(p, "아직 그림자에 숨을 수 없습니다!")) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        hiding = true;
        Vanish.hide(plugin, p);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, HIDE_TICKS, 1, false, false, false));
        p.sendMessage(ChatColor.DARK_GRAY + "그림자에 스며듭니다... " + ChatColor.GRAY + "(5초)");

        cancelExpiry();
        expiryTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            unhide(p);
            if (p.isOnline()) p.sendMessage(ChatColor.GRAY + "은신이 풀렸습니다.");
        }, HIDE_TICKS);
    }

    @Override
    public void onDealMeleeDamage(Player attacker, EntityDamageByEntityEvent event) {
        if (!hiding) return;
        if (!attacker.equals(event.getDamager())) return; // 발사체 제외

        double multiplier = FRONT_MULTIPLIER;
        Entity victim = event.getEntity();
        // LivingEntity가 아니면(방어구 거치대 등) 배율만 적용하고 등 뒤 판정은 건너뜁니다.
        if (victim instanceof LivingEntity living) {
            Vector look = living.getLocation().getDirection();
            Vector toVictim = living.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (BackstabMath.isBackstab(look.getX(), look.getZ(), toVictim.getX(), toVictim.getZ())) {
                multiplier = BACKSTAB_MULTIPLIER;
            }
        }

        event.setDamage(event.getDamage() * multiplier);
        attacker.sendMessage(ChatColor.DARK_GRAY + (multiplier == BACKSTAB_MULTIPLIER
                ? "등 뒤에서 급소를 찔렀습니다! (x2.5)" : "기습! (x2)"));

        // 공격하는 순간 은신 해제
        unhide(attacker);
    }

    private void unhide(Player p) {
        cancelExpiry();
        hiding = false;
        if (p == null) return;
        Vanish.show(JavaPlugin.getProvidingPlugin(getClass()), p);
        p.removePotionEffect(PotionEffectType.SPEED);
    }

    private void cancelExpiry() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }
}
