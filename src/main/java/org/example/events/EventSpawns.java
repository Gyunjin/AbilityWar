package org.example.events;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 이벤트가 스폰한 엔티티/설치한 블록에 표식을 달고, 게임 종료 시 한 번에 회수합니다.
 *
 * PDC를 쓰는 이유: 메타데이터(FixedMetadataValue)는 런타임 전용이라 월드에 저장되지
 * 않습니다. 예전에 네크로맨서 좀비가 메타데이터로 표식을 달고 setPersistent(true)로
 * 저장되는 바람에, 재시작하면 좀비는 남고 표식만 사라져 영영 제거할 수 없었습니다.
 * PDC는 엔티티와 함께 저장되므로 재시작 후에도 회수됩니다.
 *
 * 블록은 PDC를 붙일 곳이 없어(BlockState PDC는 상자 인벤토리와 함께 저장되긴 하지만
 * 위치를 되찾으려면 결국 월드 전체를 훑어야 합니다) 설치 좌표를 런타임 리스트로만
 * 들고 있습니다. 서버를 열 때마다 새 월드를 만들기 때문에(Main.onEnable) 상자가
 * 재시작 너머로 살아남을 수 없어, 엔티티와 달리 영속 저장이 필요 없습니다.
 */
public final class EventSpawns {

    private static final String KEY = "event_spawn";

    /** 이벤트가 설치한 블록의 좌표. 한 판에 몇 개 수준이라 리스트로 충분합니다. */
    private static final List<Location> TAGGED_BLOCKS = new ArrayList<>();

    private EventSpawns() {
    }

    private static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    /** 이벤트가 스폰한 엔티티임을 표시합니다. */
    public static void tag(Entity entity, JavaPlugin plugin) {
        entity.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
    }

    /** 이벤트가 설치한 블록임을 기록합니다. 설치 직후에 호출합니다. */
    public static void tagBlock(Location location) {
        if (location == null) return;
        TAGGED_BLOCKS.add(location.clone());
    }

    /** 표식이 붙은 엔티티에서 표식을 확인합니다. 청크 로드 시 정리에도 씁니다. */
    static boolean isTagged(Entity entity, JavaPlugin plugin) {
        return entity.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    /**
     * 표식이 붙은 엔티티와 블록을 모두 제거합니다. 게임 종료/플러그인 비활성화 시 호출합니다.
     *
     * world.getEntities()는 로드된 청크의 엔티티만 돌려주므로 여기서 잡히지 않는
     * 잔당이 남습니다. 그건 EventSpawnCleanupListener가 해당 청크가 다음에 로드될 때
     * 처리합니다.
     */
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
        removed += sweepBlocks(world);
        return removed;
    }

    /**
     * 기록해둔 좌표의 상자를 회수합니다.
     *
     * 여전히 CHEST일 때만 지웁니다. 플레이어가 이미 캐갔거나 그 자리에 다른 사람이
     * 뭔가 지어놨을 수 있는데, 남의 블록을 지우는 것이 상자가 남는 것보다 나쁩니다.
     * 청크 로드는 감수합니다. 대상이 한 판에 몇 개뿐이고 게임 종료 시 한 번만 도는
     * 경로라 핫패스가 아닙니다.
     */
    private static int sweepBlocks(World world) {
        int removed = 0;
        for (Location loc : TAGGED_BLOCKS) {
            if (loc.getWorld() == null || !loc.getWorld().equals(world)) continue;
            try {
                Block block = loc.getBlock();
                if (block.getType() == Material.CHEST) {
                    block.setType(Material.AIR);
                    removed++;
                }
            } catch (Exception e) {
                // 한 좌표가 실패해도 나머지 회수는 계속합니다.
            }
        }
        TAGGED_BLOCKS.clear();
        return removed;
    }
}
