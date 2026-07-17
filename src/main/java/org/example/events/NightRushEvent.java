package org.example.events;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.Random;

/**
 * 시간을 밤으로 돌리고 각 생존자 주변에 몬스터를 스폰합니다.
 * 밤은 자연히 지나가므로 시간을 되돌리지 않습니다.
 */
public class NightRushEvent implements GameEvent {

    private static final long NIGHT_TIME = 14000L;
    private static final int PER_PLAYER = 2;
    private static final double SPAWN_RADIUS = 6.0;

    private final Random random = new Random();

    @Override
    public String getName() {
        return "사령의 밤";
    }

    @Override
    public void start(GameContext ctx) {
        if (ctx.getWorld() == null) return;
        ctx.getWorld().setTime(NIGHT_TIME);

        World world = ctx.getWorld();
        for (Player p : ctx.getSurvivors()) {
            // 게임 월드 밖의 생존자는 건너뜁니다. sweep()은 ctx.getWorld()만 훑으므로
            // p.getWorld()에 스폰하면 회수되지 않는 좀비가 남습니다. 그렇다고 좌표만
            // 가져와 게임 월드에 스폰하는 것도 안 됩니다. 다른 월드의 x/z는 게임
            // 월드에서 아무 의미 없는 자리라 엉뚱한 곳에 몹이 생깁니다.
            if (!p.getWorld().equals(world)) continue;

            Location base = p.getLocation();

            for (int i = 0; i < PER_PLAYER; i++) {
                double dx = (random.nextDouble() * 2 - 1) * SPAWN_RADIUS;
                double dz = (random.nextDouble() * 2 - 1) * SPAWN_RADIUS;
                int x = base.getBlockX() + (int) Math.floor(dx);
                int z = base.getBlockZ() + (int) Math.floor(dz);

                // 플레이어의 Y를 그대로 재사용하면 울퉁불퉁한 지형에서 좀비가 블록
                // 속에 파묻혀 스폰되어 즉시 질식사합니다. 근처 청크는 항상 로드되어
                // 있어야 하며, 혹시 아니라면(청크 언로드 경합 등) 생성을 트리거하지
                // 않도록 이 좀비 스폰만 건너뜁니다.
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                int y = world.getHighestBlockYAt(x, z);
                Location spawn = new Location(world, x + 0.5, y + 1, z + 0.5);

                Zombie z2 = (Zombie) world.spawnEntity(spawn, EntityType.ZOMBIE);
                z2.setAdult();
                EventSpawns.tag(z2, ctx.getPlugin(), ctx.getEventSessionId());
            }
        }

        GameEventManager.announce("사령의 밤",
                ChatColor.YELLOW + "어둠이 내렸습니다. " + ChatColor.GRAY + "사령들이 당신을 찾아옵니다.");
    }
}
