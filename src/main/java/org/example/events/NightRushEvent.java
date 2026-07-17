package org.example.events;

import org.bukkit.ChatColor;
import org.bukkit.Location;
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

        for (Player p : ctx.getSurvivors()) {
            for (int i = 0; i < PER_PLAYER; i++) {
                double dx = (random.nextDouble() * 2 - 1) * SPAWN_RADIUS;
                double dz = (random.nextDouble() * 2 - 1) * SPAWN_RADIUS;
                Location spawn = p.getLocation().clone().add(dx, 0, dz);

                Zombie z = (Zombie) p.getWorld().spawnEntity(spawn, EntityType.ZOMBIE);
                z.setAdult();
                EventSpawns.tag(z, ctx.getPlugin());
            }
        }

        GameEventManager.announce("사령의 밤",
                ChatColor.YELLOW + "어둠이 내렸습니다. " + ChatColor.GRAY + "사령들이 당신을 찾아옵니다.");
    }
}
