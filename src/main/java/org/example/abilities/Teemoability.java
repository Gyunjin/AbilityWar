package org.example.abilities;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.example.GameRules;

import java.util.UUID;

/**
 * 티모: 전용 독침 발사기로 우클릭 시 일직선으로 독침을 발사합니다.
 * 대미지는 낮지만(1하트) 짧은 중독(4초)을 겁니다. 견제형 능력이라 쿨타임은 짧게(2.5초) 잡았습니다.
 * 패시브: 5초 이상 제자리에 가만히 있으면 투명화됩니다(파티클도 표시 안 함). 움직이는 순간 즉시 풀립니다.
 *
 * 참고: 발사체 충돌 감지는 이 클래스가 직접 Listener를 등록하지 않고, AbilityManager가
 * 전역에서 한 번만 구독한 뒤 onProjectileHit으로 위임해줍니다(같은 능력을 가진 플레이어가
 * 여러 명이어도 이벤트가 중복 처리되지 않도록). 독침에는 공용 메타데이터 키
 * (Ability.OWNER_META_KEY)를 붙여 AbilityManager가 소유자를 식별합니다.
 */
public class Teemoability implements Ability {

    private static final String ITEM_TAG = "[능력] 독침 발사기";

    private static final double DART_SPEED = 2.2;
    private static final double DART_DAMAGE = 2.0; // 1하트 - 견제용으로 낮게
    private static final int POISON_DURATION_TICKS = 80; // 4초
    private static final int POISON_AMPLIFIER = 0; // 중독 1

    private static final long STILL_THRESHOLD_MS = 5000; // 5초 이상 정지 시 은신
    private static final double MOVE_THRESHOLD = 0.05; // 이보다 더 움직이면 "이동"으로 판정

    // 투명화 지속시간. 0.5초(10틱)마다 갱신되므로 넉넉히 2초를 줍니다.
    // 무한 지속시간을 쓰면 오프라인 상태에서 해제할 방법이 없어 영구 투명 버그가 됩니다.
    private static final int INVISIBILITY_REFRESH_TICKS = 40;

    private final Cooldown cooldown = new Cooldown(2500);
    private UUID ownerUuid;
    private Plugin plugin;
    private BukkitTask stillnessTask;

    private Location lastCheckedLocation;
    private long stillSince = 0;
    private boolean invisibleActive = false;

    @Override
    public String getName() {
        return "티모";
    }

    @Override
    public void resetCooldown() {
        cooldown.reset();
    }

    private ItemStack createItem() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + ITEM_TAG);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onGrant(Player p, boolean isReGrant) {
        this.ownerUuid = p.getUniqueId();
        p.getInventory().addItem(createItem());

        if (plugin == null) {
            plugin = JavaPlugin.getProvidingPlugin(getClass());
        }

        if (stillnessTask == null) {
            stillnessTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkStillness, 20L, 10L);
        }
        lastCheckedLocation = p.getLocation();
        stillSince = System.currentTimeMillis();

        if (!isReGrant) {
            p.sendMessage("");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage(ChatColor.YELLOW + "당신의 무작위 능력은 [" + ChatColor.AQUA + getName() + ChatColor.YELLOW + "] 입니다!");
            p.sendMessage(ChatColor.GRAY + "(전용 독침 발사기 우클릭 시 일직선으로 독침을 발사합니다. 대미지는 낮지만 짧게 중독시킵니다.)");
            p.sendMessage(ChatColor.RED + "(쿨타임: 2.5초)");
            p.sendMessage(ChatColor.GRAY + "(패시브: 5초 이상 가만히 있으면 투명화됩니다. 움직이면 즉시 풀립니다.)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]의 장비가 지급되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        if (stillnessTask != null) {
            stillnessTask.cancel();
            stillnessTask = null;
        }
        invisibleActive = false;
        if (p != null && p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    private boolean isHoldingBlowgun(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        return main.getType() == Material.BLAZE_ROD && main.hasItemMeta()
                && main.getItemMeta().hasDisplayName()
                && main.getItemMeta().getDisplayName().contains(ITEM_TAG);
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 우클릭 한 번에 이벤트가 주손/보조손 두 번 발생하므로 주손만 처리합니다.
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!isHoldingBlowgun(p)) return;

        event.setCancelled(true);

        if (!cooldown.tryUse(p, "독침을 재장전 중입니다!")) return;

        Snowball dart = p.launchProjectile(Snowball.class);
        dart.setVelocity(p.getEyeLocation().getDirection().multiply(DART_SPEED));
        dart.setGravity(false); // 포물선 없이 일직선으로 날아감
        dart.setMetadata(OWNER_META_KEY, new FixedMetadataValue(plugin, p.getUniqueId().toString()));

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.8f);
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        // AbilityManager가 메타데이터의 소유자를 보고 이미 정확한 플레이어의 인스턴스로만
        // 전달해주므로, 여기서 별도로 소유자를 다시 확인할 필요가 없습니다.
        if (!(event.getHitEntity() instanceof LivingEntity)) {
            event.getEntity().remove();
            return;
        }

        LivingEntity target = (LivingEntity) event.getHitEntity();
        Player shooter = Bukkit.getPlayer(ownerUuid);

        // PVP가 꺼진 동안(파밍 시간)에는 플레이어에게 아무 효과도 주지 않습니다.
        //
        // 직격 대미지는 PVP 플래그가 알아서 막아줍니다(공격 주체가 플레이어인 대미지는
        // 전부 차단됨). 하지만 아래의 중독은 "대미지"가 아니라 포션 효과라서 그 차단을
        // 그대로 통과합니다. 그래서 가드가 없으면 파밍 중 티모만 일방적으로 상대를
        // 갉을 수 있어 "파밍 중엔 서로 못 때린다"는 규칙에 구멍이 생깁니다.
        // (몬스터는 대상이 아니므로 파밍 중에도 정상적으로 독이 걸립니다.)
        if (target instanceof Player && !GameRules.isPvp(target.getWorld())) {
            event.getEntity().remove();
            return;
        }

        if (shooter != null) {
            target.damage(DART_DAMAGE, shooter);
        } else {
            target.damage(DART_DAMAGE);
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, POISON_DURATION_TICKS, POISON_AMPLIFIER));

        event.getEntity().remove();
    }

    /** 0.5초마다 위치를 확인해 5초 이상 정지 상태면 투명화하고, 움직이면 즉시 해제합니다. */
    private void checkStillness() {
        if (ownerUuid == null) return;
        Player p = Bukkit.getPlayer(ownerUuid);
        if (p == null || !p.isOnline()) return;

        Location current = p.getLocation();
        if (lastCheckedLocation == null || hasMoved(current, lastCheckedLocation)) {
            stillSince = System.currentTimeMillis();
            lastCheckedLocation = current;
            if (invisibleActive) {
                invisibleActive = false;
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
            return;
        }

        lastCheckedLocation = current;
        long stillDuration = System.currentTimeMillis() - stillSince;
        if (stillDuration >= STILL_THRESHOLD_MS) {
            invisibleActive = true;
            // ambient/particles/icon 전부 false로 줘서 투명화 이펙트 자체가 안 보이게 함.
            //
            // 예전에는 Integer.MAX_VALUE(사실상 무한) 지속시간을 한 번만 걸었습니다. 그런데
            // 포션 효과는 오프라인 플레이어에게서 줄어들지 않고, onRevoke()는 플레이어가
            // 오프라인이면 효과를 해제하지 못합니다(p == null). 그래서 투명한 상태로 접속을
            // 끊으면 재접속 후에도 영구히 투명한 채로 남았습니다.
            // 이제는 짧은 지속시간을 이 0.5초 주기 작업이 계속 갱신하는 방식이라,
            // 어떤 이유로든 갱신이 멈추면 스스로 사라집니다.
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    INVISIBILITY_REFRESH_TICKS, 0, false, false, false), true);
        }
    }

    private boolean hasMoved(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) return true;
        return a.distanceSquared(b) > MOVE_THRESHOLD * MOVE_THRESHOLD;
    }
}