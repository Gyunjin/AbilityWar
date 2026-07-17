package org.example.abilities;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Necromancerability implements Ability {

    private static final String ITEM_TAG = "[능력] 사령의 지팡이";
    private static final Set<EntityType> UNDEAD_TYPES = EnumSet.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK,
            EntityType.STRAY, EntityType.DROWNED, EntityType.WITHER_SKELETON
    );

    // 지팡이를 들고 있을 때, 소환수가 주인으로부터 이 거리(칸) 이상 떨어져 있으면
    // 주인 쪽으로 걸어오도록 명령합니다. 매 틱(패시브 훅)마다 다시 확인합니다.
    private static final double FOLLOW_TRIGGER_DISTANCE = 4.0;
    private static final double FOLLOW_SPEED = 1.0;

    /**
     * 네크로맨서 소환 좀비 식별용. 리스트에 없어도 이 표식을 보고 남은 좀비를 찾아 제거합니다.
     *
     * 예전에는 FixedMetadataValue(메타데이터)를 썼는데, 메타데이터는 런타임 전용이라
     * 월드에 저장되지 않습니다. 반면 좀비는 setPersistent(true)로 저장되기 때문에,
     * 서버를 재시작하면 좀비는 살아남고 표식만 사라져서 어떤 방법으로도 찾아 지울 수 없는
     * 철갑옷 좀비가 월드에 영구히 박제됐습니다. PDC는 엔티티와 함께 저장되므로
     * 재시작 후에도 정상적으로 식별/제거됩니다.
     */
    private static NamespacedKey summonKey() {
        return new NamespacedKey(JavaPlugin.getProvidingPlugin(Necromancerability.class), "necro_summon");
    }

    private final Cooldown cooldown = new Cooldown(35000);
    private final List<Zombie> summons = new ArrayList<>();
    private Team necroTeam;

    @Override
    public String getName() {
        return "네크로맨서";
    }

    @Override
    public void resetCooldown() {
        cooldown.reset();
    }

    private ItemStack createItem() {
        ItemStack item = AbilityItems.create(Material.BONE, ChatColor.DARK_RED, ITEM_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "우클릭 시 주인을 따르고 적을 공격하는 사령 좀비 3마리를 부립니다.");
            lore.add(ChatColor.GRAY + "지팡이를 들고 있으면 멀리 떨어진 소환수들이 주인 쪽으로 다가옵니다.");
            meta.setLore(lore);
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
            p.sendMessage(ChatColor.GRAY + "(패시브 효과: 모든 언데드 몬스터들이 당신을 선공하지 않으며, 언데드 대미지를 반감합니다.)");
            p.sendMessage(ChatColor.GRAY + "(지팡이를 들고 있으면 멀리 떨어진 소환수들이 주인 쪽으로 다가옵니다.)");
            p.sendMessage(ChatColor.RED + "(액티브 쿨타임: 35초)");
            p.sendMessage(ChatColor.GOLD + "========================================");
            p.sendMessage("");
        } else {
            p.sendMessage(ChatColor.GREEN + "[능력자] 변경권으로 선택하신 능력 [" + ChatColor.AQUA + getName() + ChatColor.GREEN + "]의 장비가 지급되었습니다.");
        }
    }

    @Override
    public void onRevoke(Player p) {
        clearSummons(p);
        if (necroTeam != null) {
            try {
                necroTeam.unregister();
            } catch (IllegalStateException ignored) {
                // 이미 다른 경로로 해제된 경우
            }
            necroTeam = null;
        }
    }

    private void clearSummons(Player owner) {
        for (Zombie z : summons) {
            if (z != null && z.isValid()) z.remove();
        }
        summons.clear();

        if (necroTeam != null) {
            // 팀 엔트리를 지우지 않으면 재소환할 때마다 죽은 좀비의 UUID 엔트리가
            // 계속 쌓이기만 했습니다.
            try {
                for (String entry : new ArrayList<>(necroTeam.getEntries())) {
                    if (owner == null || !entry.equals(owner.getName())) {
                        necroTeam.removeEntry(entry);
                    }
                }
            } catch (IllegalStateException e) {
                necroTeam = null; // 이미 해제된 팀
            }
        }

        // 리스트와 동기화가 어긋난 소환수(게임 시작 전 테스트 소환, 서버 재시작 등)도
        // PDC 표식을 보고 추가로 제거합니다.
        if (owner == null) return;
        String ownerId = owner.getUniqueId().toString();
        NamespacedKey key = summonKey();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Zombie z : world.getEntitiesByClass(Zombie.class)) {
                String tag = z.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (ownerId.equals(tag)) {
                    z.remove();
                }
            }
        }
    }

    @Override
    public void onEntityTarget(Player p, EntityTargetEvent event) {
        if (UNDEAD_TYPES.contains(event.getEntityType())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEntityDamageByEntity(Player p, EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity && UNDEAD_TYPES.contains(event.getDamager().getType())) {
            event.setDamage(event.getDamage() * 0.5);
        }
    }

    /**
     * 사령의 지팡이를 들고 있는 동안, 주인과 너무 멀리 떨어진 소환수를 주인 쪽으로
     * 걸어오게 합니다. 좀비의 기본 AI(전투 등)를 대체하지 않고 "이동 목표"만
     * 지정하는 방식이라, 근처에 공격할 대상이 있으면 그쪽을 우선 처리합니다.
     */
    @Override
    public void onPassiveTick(Player p) {
        if (summons.isEmpty()) return;
        if (!AbilityItems.isHolding(p, Material.BONE, ITEM_TAG)) return;

        Location ownerLoc = p.getLocation();
        double triggerDistSq = FOLLOW_TRIGGER_DISTANCE * FOLLOW_TRIGGER_DISTANCE;

        for (Zombie zombie : summons) {
            if (zombie == null || !zombie.isValid() || zombie.isDead()) continue;
            if (!zombie.getWorld().equals(ownerLoc.getWorld())) continue;

            if (zombie.getLocation().distanceSquared(ownerLoc) > triggerDistSq) {
                zombie.getPathfinder().moveTo(ownerLoc, FOLLOW_SPEED);
            }
        }
    }

    @Override
    public void onInteract(Player p, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // 우클릭 한 번에 이벤트가 주손/보조손 두 번 발생하므로 주손만 처리합니다.
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!AbilityItems.isHolding(p, Material.BONE, ITEM_TAG)) return;

        event.setCancelled(true);

        if (!cooldown.tryUse(p, "지팡이에 사령의 기운이 부족합니다!")) return;

        clearSummons(p);

        Location spawnLoc = p.getLocation();
        p.sendMessage(ChatColor.DARK_RED + "[네크로맨서] 대지에서 어둠의 사령들을 소환합니다!");

        // 좀비 소환은 이 능력의 핵심 기능이므로 항상 먼저 실행합니다.
        Random random = new Random();
        for (int i = 0; i < 3; i++) {
            Zombie zombie = (Zombie) p.getWorld().spawnEntity(
                    spawnLoc.clone().add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1),
                    EntityType.ZOMBIE
            );
            zombie.customName(Component.text(p.getName() + "의 호위 사령", NamedTextColor.RED));
            zombie.setCustomNameVisible(false); // 체력바 리스너가 마커에 이름+체력을 표시
            // PDC는 엔티티와 함께 월드에 저장되므로 서버를 재시작해도 표식이 살아남습니다.
            zombie.getPersistentDataContainer().set(summonKey(), PersistentDataType.STRING, p.getUniqueId().toString());
            zombie.setAdult(); // setBaby(false)는 deprecated. Ageable.setAdult()가 정식 경로입니다.
            zombie.setPersistent(true); // 자연 디스폰/원거리 제거 로직에 영향받지 않도록 명시적으로 고정
            zombie.setRemoveWhenFarAway(false);
            if (zombie.getEquipment() != null) {
                zombie.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            }
            summons.add(zombie);
        }

        // 팀 충돌 방지 설정은 부가 기능이므로, 여기서 예외가 나더라도 위의 소환 자체는
        // 이미 끝난 뒤라 영향을 받지 않도록 별도로 분리해 try-catch로 감쌉니다.
        // (참고: 팀 이름이 "necro_"+닉네임 형태였을 때, 일부 서버 버전의 16자 제한에
        //  걸려 registerNewTeam()이 예외를 던지고 전체 소환이 조용히 실패하는 문제가
        //  있었습니다. 항상 짧게 고정된 이름을 쓰도록 바꿔 이 문제를 원천 차단합니다.)
        try {
            Scoreboard sb = p.getScoreboard();
            String teamName = "nc" + Integer.toHexString(p.getUniqueId().hashCode());

            Team team = sb.getTeam(teamName);
            if (team == null) {
                team = sb.registerNewTeam(teamName);
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                team.setAllowFriendlyFire(false);
            }
            team.addEntry(p.getName());
            for (Zombie zombie : summons) {
                team.addEntry(zombie.getUniqueId().toString());
            }
            this.necroTeam = team;
        } catch (Exception e) {
            p.getServer().getLogger().warning("[네크로맨서] 팀 충돌 방지 설정 중 오류(소환 자체는 정상 처리됨): " + e.getMessage());
        }
    }
}