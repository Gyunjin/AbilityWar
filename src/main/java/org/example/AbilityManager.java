package org.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.example.abilities.Ability;
import org.example.abilities.AbilityItems;
import org.example.abilities.AbilityRegistry;
import org.example.abilities.Reincarnatorability;
import org.example.abilities.Vanish;
import org.example.game.AbilityAssigner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class AbilityManager implements Listener, CommandExecutor {

    private final Main plugin;
    private final Map<UUID, Ability> playerAbilities = new HashMap<>();

    public AbilityManager(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return false;
    }

    public String[] getAbilityList() {
        return AbilityRegistry.getNames();
    }

    public boolean isValidAbility(String name) {
        return AbilityRegistry.isValid(name);
    }

    /** 플레이어의 현재 능력 이름을 반환합니다. 없으면 null. (Main의 능력 공개용) */
    public String getPlayerAbilityName(UUID uuid) {
        Ability a = playerAbilities.get(uuid);
        return a == null ? null : a.getName();
    }

    /** 이번 게임에 배정된 능력 이름 목록(정렬). 파밍 종료 시 등장 능력 공개에 씁니다. */
    public List<String> getAssignedAbilityNames() {
        List<String> names = new ArrayList<>();
        for (Ability a : playerAbilities.values()) {
            names.add(a.getName());
        }
        Collections.sort(names);
        return names;
    }

    /** 게임 종료/초기화 시 호출. 모든 플레이어의 능력을 원상복구하고 목록을 비웁니다. */
    public void clearAbilities() {
        for (Map.Entry<UUID, Ability> entry : playerAbilities.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            entry.getValue().onRevoke(p); // p가 null이어도(오프라인) 소환물 정리 등은 진행됨
        }
        playerAbilities.clear();
    }

    /** 게임 시작 시 호출. 아직 능력이 없는 플레이어에게만 무작위 배정합니다. */
    public void assignAbilitiesIfNone(Collection<? extends Player> players) {
        // 능력이 없는 플레이어를 먼저 모읍니다. 이들에게만 중복 없이 배정합니다.
        List<Player> needAbility = new ArrayList<>();
        for (Player p : players) {
            if (!playerAbilities.containsKey(p.getUniqueId())) {
                needAbility.add(p);
            }
        }

        // 이미 능력을 가진(=변경권으로 미리 배정된) 플레이어의 능력 이름은 풀에서 빼야
        // 새로 배정되는 플레이어와 겹치지 않습니다.
        Set<String> taken = new HashSet<>();
        for (Player p : players) {
            if (needAbility.contains(p)) continue;
            String name = getPlayerAbilityName(p.getUniqueId());
            if (name != null) taken.add(name);
        }

        List<String> pool = AbilityAssigner.availablePool(List.of(AbilityRegistry.getNames()), taken);
        List<String> assigned = AbilityAssigner.assign(pool, needAbility.size(), new Random());

        for (int i = 0; i < needAbility.size(); i++) {
            Player p = needAbility.get(i);
            Ability ability = AbilityRegistry.create(assigned.get(i));
            if (ability == null) continue;
            playerAbilities.put(p.getUniqueId(), ability);
            ability.onGrant(p, false);
        }

        // 이미 능력이 있는 플레이어는 같은 능력으로 새 인스턴스를 만들어 초기화한 뒤
        // 장비만 재지급합니다. 기존 인스턴스를 그대로 쓰면 소환물·쿨타임 상태가 남습니다.
        for (Player p : players) {
            Ability existing = playerAbilities.get(p.getUniqueId());
            if (existing == null || needAbility.contains(p)) continue;

            String abilityName = existing.getName();
            existing.onRevoke(p);
            Ability fresh = AbilityRegistry.create(abilityName);
            if (fresh != null) {
                playerAbilities.put(p.getUniqueId(), fresh);
                fresh.onGrant(p, true);
            }
        }
    }

    /** 관리자 명령어 등으로 특정 플레이어의 능력을 강제 변경합니다. newAbilityName이 비어있으면 능력을 제거만 합니다. */
    public void changePlayerAbilityForce(Player p, String newAbilityName) {
        removeAbilityItems(p);

        Ability old = playerAbilities.remove(p.getUniqueId());
        if (old != null) old.onRevoke(p);

        // 공통 상태 초기화 (체력 클램프는 PlayerStats가 함께 처리합니다)
        PlayerStats.resetMaxHealth(p);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.SPEED);

        if (newAbilityName == null || newAbilityName.isEmpty()) return;

        Ability newAbility = AbilityRegistry.create(newAbilityName);
        if (newAbility == null) return;

        playerAbilities.put(p.getUniqueId(), newAbility);
        newAbility.onGrant(p, false);
    }

    private void removeAbilityItems(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isBoundItem(item)) {
                p.getInventory().remove(item);
            }
        }
    }

    /** 능력 아이템(태그 "[능력]")인지 확인합니다. 판정 로직은 AbilityItems가 갖고 있습니다. */
    private boolean isBoundItem(ItemStack item) {
        return AbilityItems.isBound(item);
    }

    /**
     * 능력이 없는 상태로 접속한 플레이어에게 남아있는 능력 잔재를 정리합니다.
     *
     * clearAbilities()는 오프라인 플레이어에게 onRevoke(null)을 호출하는데, 이때 헐크의
     * 최대 체력 원복(setMaxHealth)은 Player 객체가 없으면 불가능합니다. 그래서 헐크가
     * 게임 종료 전에 접속을 끊으면 최대 체력 40이 플레이어 데이터에 그대로 저장되어
     * 재접속해도 40인 채로 남았습니다. 오프라인 상태에서는 손댈 수 없으니,
     * 실제로 고칠 수 있는 유일한 시점인 "재접속 순간"에 정리합니다.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        // 은신 중인 다른 플레이어를 이 접속자에게도 숨깁니다. 이게 없으면 방금 들어온
        // 사람 눈에만 은신자가 보입니다. 능력 보유 여부와 무관하므로 조기 반환보다 앞에 둡니다.
        Vanish.reapplyFor(plugin, p);

        if (playerAbilities.containsKey(p.getUniqueId())) return; // 능력 보유 중이면 정상 상태

        PlayerStats.resetMaxHealth(p);
        // 티모의 투명화는 스스로 만료되지만, 이전 버전에서 무한 지속시간으로 걸려
        // 저장돼 있던 플레이어를 위해 여기서도 확실히 정리합니다.
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        // 능력이 없는데 은신 상태로 남아 있으면 잔재입니다.
        Vanish.show(plugin, p);
        // 윤회자는 무한 지속 효과와 AttributeModifier를 겁니다. 오프라인 중 능력이
        // 정리됐다면 여기가 유일한 복구 지점입니다(기존 최대체력 정리와 같은 이유).
        Reincarnatorability.cleanupResidue(p);
    }

    /** 능력 아이템(귀속 무기)은 실수로든 의도적으로든 버릴 수 없도록 막습니다. */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (isBoundItem(dropped)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "귀속된 능력 아이템은 버릴 수 없습니다.");
        }
    }

    /** 귀속 아이템은 왼손(오프핸드)으로 옮길 수 없도록 F키 손바꾸기를 막습니다. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isBoundItem(event.getMainHandItem()) || isBoundItem(event.getOffHandItem())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "귀속된 능력 아이템은 왼손에 들 수 없습니다.");
        }
    }

    /** 모든 플레이어의 능력 쿨타임을 즉시 초기화합니다. ('능력 재충전' 이벤트용) */
    public void resetAllCooldowns(Collection<? extends Player> players) {
        for (Player p : players) {
            Ability a = playerAbilities.get(p.getUniqueId());
            if (a != null) a.resetCooldown();
        }
    }

    public void checkPassiveAbilities(Collection<? extends Player> players, boolean isGameStarted) {
        if (!isGameStarted) return;
        for (Player p : players) {
            Ability a = playerAbilities.get(p.getUniqueId());
            if (a != null) a.onPassiveTick(p);
        }
    }

    public void recordHistoryTick(Collection<? extends Player> players, boolean isGameStarted) {
        if (!isGameStarted) return;
        for (Player p : players) {
            Ability a = playerAbilities.get(p.getUniqueId());
            if (a != null) a.recordTick(p);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!plugin.isGameStarted()) return;
        if (!(event.getTarget() instanceof Player)) return;
        Player player = (Player) event.getTarget();
        Ability a = playerAbilities.get(player.getUniqueId());
        if (a != null) a.onEntityTarget(player, event);
    }

    @EventHandler
    public void onAbilityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.isGameStarted()) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Ability a = playerAbilities.get(player.getUniqueId());
        if (a != null) a.onEntityDamageByEntity(player, event);
    }

    /**
     * 공격자 기준 근접 대미지 위임. 피격자 기준인 onAbilityDamageByEntity와는 별개이며,
     * 플레이어가 플레이어를 때리면 두 훅이 각각 자기 능력 인스턴스로 갑니다. 의도된 동작입니다.
     */
    @EventHandler
    public void onAbilityDealDamage(EntityDamageByEntityEvent event) {
        if (!plugin.isGameStarted()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        Ability a = playerAbilities.get(attacker.getUniqueId());
        if (a != null) a.onDealMeleeDamage(attacker, event);
    }

    /** 바람 인도자의 더블 점프용. 게임 진행 중이 아니어도 위임합니다(로비에서도 비행 토글은 발생). */
    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        Ability a = playerAbilities.get(p.getUniqueId());
        if (a != null) a.onToggleFlight(p, event);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isGameStarted()) return;
        Player player = event.getPlayer();
        Ability a = playerAbilities.get(player.getUniqueId());
        if (a != null) a.onBlockPlace(player, event);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isGameStarted()) return;
        Player player = event.getPlayer();
        Ability a = playerAbilities.get(player.getUniqueId());
        if (a != null) a.onBlockBreak(player, event);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!plugin.isGameStarted()) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Ability a = playerAbilities.get(player.getUniqueId());
        if (a != null) {
            a.onEntityDamage(player, event);
            a.onFatalDamage(player, event);
        }
    }

    /**
     * 플레이어 이동 이벤트를 능력별로 위임합니다. 예전에는 헐크/티모 같은 능력이 각자
     * Listener를 직접 등록해서, 같은 능력을 가진 플레이어 수만큼 이벤트가 중복 처리됐습니다.
     * 이제는 여기서 한 번만 구독하고, 해당 플레이어의 능력 인스턴스에만 정확히 전달합니다.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Ability a = playerAbilities.get(player.getUniqueId());
        if (a != null) a.onPlayerMove(player, event);
    }

    /**
     * 능력이 발사한 발사체(독침 등)가 무언가에 맞았을 때, 발사체에 표시된 소유자
     * 메타데이터(Ability.OWNER_META_KEY)를 보고 정확한 플레이어의 능력 인스턴스로만 위임합니다.
     */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!event.getEntity().hasMetadata(Ability.OWNER_META_KEY)) return;

        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(event.getEntity().getMetadata(Ability.OWNER_META_KEY).get(0).asString());
        } catch (Exception e) {
            return;
        }

        Ability a = playerAbilities.get(ownerUuid);
        if (a != null) a.onProjectileHit(event);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // 우클릭 한 번에 이벤트가 주손/보조손 두 번 발생하므로 주손만 처리합니다.
        // (가드가 없으면 변경권 한 번 사용에 2개가 소모될 수 있습니다.)
        if (event.getHand() != EquipmentSlot.HAND) return;

        // 능력 변경권 사용 (능력별 로직이 아닌 공통 아이템이므로 여기서 직접 처리)
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.NETHER_STAR && item.hasItemMeta()
                    && item.getItemMeta().hasDisplayName()
                    && item.getItemMeta().getDisplayName().contains("능력 변경권")) {

                event.setCancelled(true);

                if (plugin.isGameStarted()) {
                    player.sendMessage(ChatColor.RED + "게임이 이미 시작된 이후에는 능력 변경권을 사용할 수 없습니다!");
                    return;
                }

                changePlayerAbilityForce(player, AbilityRegistry.randomName(new Random()));
                item.setAmount(item.getAmount() - 1);
                return;
            }
        }

        Ability a = playerAbilities.get(player.getUniqueId());
        if (a != null) a.onInteract(player, event);
    }
}