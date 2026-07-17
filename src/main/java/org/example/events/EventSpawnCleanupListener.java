package org.example.events;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import org.example.Main;

/**
 * 언로드된 청크에 남은 이벤트 스폰물을, 그 청크가 다음에 로드되는 순간 회수합니다.
 *
 * 게임 종료 시의 EventSpawns.sweep()은 world.getEntities()를 쓰는데 이건 "로드된
 * 청크"의 엔티티만 돌려줍니다. 보급 투하는 최대 200블록 밖까지 나가고, 종료 시점엔
 * 자기장이 10x10까지 줄어 모두가 중앙에 모여 있으므로, 사실상 이벤트 좀비 대부분이
 * 언로드된 청크에 있어 한 번도 정리되지 않았습니다. 예전 네크로맨서 좀비 사건과
 * 같은 실패입니다.
 *
 * EventSpawns가 직접 Listener가 되지 않고 별도 클래스로 둔 이유: EventSpawns는
 * 인스턴스 없는 정적 유틸(private 생성자)이라 registerEvents에 넘길 인스턴스가
 * 없고, 표식 유틸과 이벤트 수신은 생명주기도 다릅니다.
 */
public class EventSpawnCleanupListener implements Listener {

    private final Main plugin;

    public EventSpawnCleanupListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // 게임 중에는 절대 건드리지 않습니다. 이벤트 몹은 게임 중에 살아있는 것이
        // 정상이고, 여기서 지우면 플레이어가 이동해 청크가 로드될 때마다 방금 스폰된
        // 몹이 사라집니다.
        if (plugin.isGameStarted()) return;

        for (Entity e : event.getChunk().getEntities()) {
            if (EventSpawns.isTagged(e, plugin)) {
                e.remove();
            }
        }
    }
}
