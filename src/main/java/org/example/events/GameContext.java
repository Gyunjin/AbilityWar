package org.example.events;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.AbilityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 이벤트가 필요로 하는 것만 담은 묶음입니다.
 *
 * 이벤트가 Main을 직접 참조하지 않게 하려고 둡니다. 그래야 이벤트를 독립적으로
 * 이해하고 교체할 수 있습니다.
 */
public final class GameContext {

    private final JavaPlugin plugin;
    private final World world;
    /** 이벤트가 실제 게임 상태를 몰래 변형(예: remove)하지 못하도록 불변 복사본을 보관합니다. */
    private final List<Player> survivors;
    private final boolean farming;
    /** 이벤트가 실제 게임 상태를 몰래 변형(예: clear)하지 못하도록 불변 복사본을 보관합니다. */
    private final Map<UUID, Integer> kills;
    private final AbilityManager abilityManager;

    public GameContext(JavaPlugin plugin, World world, List<Player> survivors,
                       boolean farming, Map<UUID, Integer> kills, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.world = world;
        this.survivors = List.copyOf(survivors);
        this.farming = farming;
        this.kills = Map.copyOf(kills);
        this.abilityManager = abilityManager;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    /** '능력 재충전' 이벤트가 쿨타임 초기화를 위임하는 데 씁니다. */
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public World getWorld() {
        return world;
    }

    public List<Player> getSurvivors() {
        return survivors;
    }

    /** 파밍 구간이면 true. 전투 전용 이벤트가 canRun에서 거르는 데 씁니다. */
    public boolean isFarming() {
        return farming;
    }

    public Map<UUID, Integer> getKills() {
        return kills;
    }

    public Set<UUID> getAliveUuids() {
        Set<UUID> result = new HashSet<>();
        for (Player p : survivors) {
            result.add(p.getUniqueId());
        }
        return result;
    }
}
