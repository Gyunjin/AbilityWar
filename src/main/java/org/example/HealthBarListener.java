package org.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;

/**
 * 모든 생명체(플레이어 포함) 머리 위에 이름+체력을 두 줄로 표시합니다.
 *
 * v4: 아머스탠드 이름표 방식은 마인크래프트 엔진 자체가 이름표를 한 줄만 렌더링해서
 * 이름과 체력이 한 줄에 억지로 이어붙어 길어지는 문제가 있었습니다. 그래서 TextDisplay를
 * 탑승물(passenger)로 바꿔 달았습니다 - TextDisplay는 "\n"으로 줄바꿈이 되면서도,
 * 탑승물이라 바닐라 엔진이 대상 엔티티 이동에 맞춰 자동으로 따라다니게 해줘서
 * (순간이동 방식과 달리) 뚝뚝 끊기지 않습니다. 플레이어도 닉네임을 직접 얻어와서
 * 같은 방식으로 표시하므로, 이전처럼 스코어보드 팀 접두사를 조작할 필요가 없어졌습니다
 * (다른 능력의 팀 설정과 충돌할 걱정도 사라집니다).
 *
 * v4: 또한 이벤트 기반 갱신만으로는 두 가지 문제가 있었습니다.
 *   1) 좀비를 코드에서 직접 entity.remove()로 지우면 EntityDeathEvent가 발생하지 않아
 *      마커가 그 자리에 고아로 남아 이름이 계속 떠 있었습니다.
 *   2) 능력 중에 p.setHealth(...)를 직접 호출해 체력을 바꾸는 경우(예: 사망회귀의
 *      시간 회귀, 헐크 능력 회수 시 원복)는 어떤 대미지/회복 이벤트도 발생시키지 않아
 *      실제 체력이 바뀌어도 체력바가 갱신되지 않고 옛 수치에 멈춰 있었습니다.
 * 그래서 일정 주기로 전체를 다시 계산해 반영하고, 주인이 사라진 마커는 그 자리에서
 * 청소하는 스윕(sweep) 작업을 추가했습니다. 이벤트 기반 즉시 갱신은 그대로 유지해
 * 반응성은 유지하면서, 스윕이 놓친 부분을 보완하는 이중 안전망 구조입니다.
 */
public class HealthBarListener implements Listener {

    private final Plugin plugin;
    private BukkitTask sweepTask;

    // 이 메타데이터 키가 붙어있는 TextDisplay만 "우리가 체력 표시용으로 만든 마커"로 인정합니다.
    private static final String HEALTHBAR_STAND_KEY = "hb_marker";

    private static final long SWEEP_PERIOD_TICKS = 10L; // 0.5초마다 전체 재계산 + 고아 마커 청소

    public HealthBarListener(Plugin plugin) {
        this.plugin = plugin;
        startSweepTask();
    }

    /**
     * 0.5초마다:
     *  1) 모든 플레이어 + 살아있는 생명체의 체력바를 다시 계산해 반영 (이벤트를
     *     거치지 않은 체력 변화까지 놓치지 않기 위함)
     *  2) 소유 엔티티가 사라진(탑승 관계가 끊긴) 마커를 청소 (엔티티가
     *     EntityDeathEvent 없이 직접 remove()된 경우 대비)
     */
    private void startSweepTask() {
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                updateHealthBar(p);
            }

            for (World world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (entity instanceof Player || entity instanceof ArmorStand) continue;
                    updateHealthBar(entity);
                }

                // 주인(탑승 대상)이 사라진 고아 마커 청소
                for (Entity e : world.getEntitiesByClass(TextDisplay.class)) {
                    if (!e.hasMetadata(HEALTHBAR_STAND_KEY)) continue;
                    if (e.getVehicle() == null) {
                        e.remove();
                    }
                }
            }
        }, 20L, SWEEP_PERIOD_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTask(plugin, () -> updateHealthBar(entity));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTask(plugin, () -> updateHealthBar(entity));
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        removeMarker(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            Bukkit.getScheduler().runTask(plugin, () -> updateHealthBar(entity));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> updateHealthBar(event.getPlayer()));
    }

    private void updateHealthBar(LivingEntity entity) {
        try {
            if (!entity.isValid() || entity.isDead()) {
                removeMarker(entity);
                return;
            }

            if (entity instanceof ArmorStand) {
                return;
            }

            if (entity instanceof Player && !((Player) entity).isOnline()) {
                return;
            }

            if (entity.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                removeMarker(entity);
                return;
            }

            AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr == null) return;

            double maxHealth = maxHealthAttr.getValue();
            double currentHealth = Math.max(0, entity.getHealth());
            String healthLine = formatHealthBar(currentHealth, maxHealth);

            String nameLine;
            if (entity instanceof Player) {
                nameLine = ChatColor.YELLOW + ((Player) entity).getName();
                ((Player) entity).spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(healthLine));
            } else if (entity.getCustomName() != null && !entity.getCustomName().isEmpty()) {
                nameLine = entity.getCustomName();
                entity.setCustomNameVisible(false);
            } else {
                nameLine = null;
            }

            String displayText = (nameLine != null) ? (nameLine + "\n" + healthLine) : healthLine;
            updateMarker(entity, displayText);
        } catch (Exception e) {
            plugin.getLogger().warning("[체력바] 업데이트 중 오류: " + e.getMessage());
        }
    }

    private void updateMarker(LivingEntity entity, String displayText) {
        TextDisplay marker = findMarker(entity);
        if (marker == null) {
            marker = createMarker(entity);
        }
        marker.setText(displayText);
    }

    private TextDisplay findMarker(LivingEntity owner) {
        for (Entity passenger : owner.getPassengers()) {
            if (passenger instanceof TextDisplay && passenger.hasMetadata(HEALTHBAR_STAND_KEY)) {
                return (TextDisplay) passenger;
            }
        }
        return null;
    }

    private TextDisplay createMarker(LivingEntity owner) {
        Location loc = owner.getLocation().add(0, owner.getHeight() + 0.3, 0);
        TextDisplay marker = owner.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setBillboard(TextDisplay.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setPersistent(false);
            d.setMetadata(HEALTHBAR_STAND_KEY, new FixedMetadataValue(plugin, true));
        });
        owner.addPassenger(marker);
        return marker;
    }

    private static void removeMarker(Entity entity) {
        for (Entity passenger : new ArrayList<>(entity.getPassengers())) {
            if (passenger instanceof TextDisplay && passenger.hasMetadata(HEALTHBAR_STAND_KEY)) {
                passenger.remove();
            }
        }
    }

    /**
     * 체력바 마커를 떼어낸 뒤 텔레포트합니다. 반드시 이 메서드로 플레이어를 이동시키세요.
     *
     * 이유: 마커를 TextDisplay "탑승물"로 붙이는 순간 그 엔티티는 Bukkit 기준 '탈것(vehicle)'이
     * 되는데, CraftEntity.teleport()는 탑승자가 있는 엔티티를 이동시키지 않고 조용히 false를
     * 반환합니다(`if (entity.isVehicle()) return false;`). 그래서 마커가 붙은 뒤로는 플러그인의
     * 모든 p.teleport()가 실패했고, /맵변경이 "완료되었습니다" 메시지만 띄우고 실제로는
     * 아무도 이동하지 않는 문제, 게임 시작 시 스폰 이동이 안 되는 문제, 블링커 대쉬가
     * 발동하지 않는 문제가 전부 여기서 나왔습니다.
     *
     * 마커를 떼면 스윕 작업이 0.5초 안에 다시 붙여주므로 별도 복구 처리는 필요 없습니다.
     */
    public static boolean safeTeleport(Entity entity, Location destination) {
        if (entity == null || destination == null) return false;
        removeMarker(entity);
        return entity.teleport(destination);
    }

    /** 플러그인 비활성화 시 남아있는 마커를 전부 정리합니다. Main.onDisable()에서 호출하세요. */
    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClass(TextDisplay.class)) {
                if (e.hasMetadata(HEALTHBAR_STAND_KEY)) {
                    e.remove();
                }
            }
        }
    }

    private String formatHealthBar(double current, double max) {
        int totalHearts = 10;
        double ratio = max > 0 ? current / max : 0;
        int fillCount = (int) Math.round(ratio * totalHearts);

        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GRAY).append("[");

        sb.append(ChatColor.RED);
        for (int i = 0; i < fillCount; i++) {
            sb.append("❤");
        }

        sb.append(ChatColor.DARK_GRAY);
        for (int i = fillCount; i < totalHearts; i++) {
            sb.append("❤");
        }

        sb.append(ChatColor.GRAY).append("] ");

        sb.append(ChatColor.GREEN).append(String.format("%.1f", current))
                .append(ChatColor.GRAY).append("/")
                .append(ChatColor.DARK_GREEN).append(String.format("%.1f", max));

        return sb.toString();
    }
}