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
        // 게임 상태 가드(isGameStarted)를 쓰지 않는 이유:
        //
        // 예전에는 "게임 중이면 return"으로 이번 판 몹을 보호했습니다. 그런데 멀리 있는
        // 청크는 플레이어가 흩어지는 게임 중에만 로드됩니다. 즉 이 리스너가 정작 일해야
        // 할 바로 그 순간에 항상 조기 return 했습니다. 판과 판 사이에는 모두가 스폰에
        // 모여 있어 그 청크들이 아예 로드되지 않으므로, 1판의 이벤트 좀비가 무장한 채
        // 2판, 3판까지 살아남았습니다.
        //
        // 이제 표식에 세션 id가 들어 있어 게임 상태를 볼 필요가 없습니다. 이번 판 id와
        // 다른 것만 지우므로 게임 중에 청크가 로드돼도 방금 스폰된 몹은 안전합니다.
        // 가드를 되살리지 마세요.
        int sessionId = plugin.getEventSessionId();
        for (Entity e : event.getChunk().getEntities()) {
            if (EventSpawns.isStale(e, plugin, sessionId)) {
                e.remove();
            }
        }
    }
}
