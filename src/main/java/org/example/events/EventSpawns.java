package org.example.events;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 이벤트가 스폰한 엔티티에 표식을 달고, 게임 종료 시 한 번에 회수합니다.
 *
 * PDC를 쓰는 이유: 메타데이터(FixedMetadataValue)는 런타임 전용이라 월드에 저장되지
 * 않습니다. 예전에 네크로맨서 좀비가 메타데이터로 표식을 달고 setPersistent(true)로
 * 저장되는 바람에, 재시작하면 좀비는 남고 표식만 사라져 영영 제거할 수 없었습니다.
 * PDC는 엔티티와 함께 저장되므로 재시작 후에도 회수됩니다.
 */
public final class EventSpawns {

    private static final String KEY = "event_spawn";

    private EventSpawns() {
    }

    private static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    /** 이벤트가 스폰한 엔티티임을 표시합니다. */
    public static void tag(Entity entity, JavaPlugin plugin) {
        entity.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
    }

    /** 표식이 붙은 엔티티를 모두 제거합니다. 게임 종료/플러그인 비활성화 시 호출합니다. */
    public static int sweep(World world, JavaPlugin plugin) {
        if (world == null) return 0;

        NamespacedKey k = key(plugin);
        int removed = 0;
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(k, PersistentDataType.BYTE)) {
                e.remove();
                removed++;
            }
        }
        return removed;
    }
}
