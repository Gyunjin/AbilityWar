package org.example.events;

import org.bukkit.Chunk;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * 자기장 안 무작위 좌표에 보급 상자를 떨어뜨리고 좌표를 공지합니다.
 *
 * 상자 주변에 몬스터를 함께 배치하는 것이 핵심입니다. 파밍 중에는 PVP가 잠겨 있어
 * 상자만 놓으면 가장 가까운 사람이 걸어가서 먹고 끝이라 경쟁이 성립하지 않습니다.
 * 몬스터가 있어야 "뚫을 수 있나"라는 판단이 생깁니다.
 */
public class SupplyDropEvent implements GameEvent {

    private static final int GUARD_COUNT = 3;
    private final Random random = new Random();

    @Override
    public String getName() {
        return "보급 투하";
    }

    @Override
    public void start(GameContext ctx) {
        World world = ctx.getWorld();
        if (world == null) return;

        Location loc = randomLocationInBorder(world);
        if (loc == null) return;

        Block block = loc.getBlock();
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            chest.getBlockInventory().addItem(
                    new ItemStack(Material.DIAMOND_SWORD),
                    new ItemStack(Material.DIAMOND_CHESTPLATE),
                    new ItemStack(Material.GOLDEN_APPLE, 2),
                    new ItemStack(Material.COOKED_BEEF, 16));
        }

        for (int i = 0; i < GUARD_COUNT; i++) {
            Location spawn = loc.clone().add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
            Zombie guard = (Zombie) world.spawnEntity(spawn, EntityType.ZOMBIE);
            guard.setAdult();
            if (guard.getEquipment() != null) {
                guard.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                guard.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            }
            EventSpawns.tag(guard, ctx.getPlugin());
        }

        GameEventManager.announce("보급 투하",
                ChatColor.YELLOW + "좌표 " + loc.getBlockX() + ", " + loc.getBlockZ()
                        + ChatColor.GRAY + " 에 보급 상자가 떨어졌습니다. 사령들이 지키고 있습니다.");
    }

    /**
     * 현재 자기장 크기 안쪽의 무작위 지면 좌표를 고릅니다.
     * 자기장은 (0,0) 중심이므로 크기의 절반이 반경입니다. 축소가 진행 중이면
     * 그 시점의 실제 크기를 쓰므로 후반에는 중앙 근처에만 뜹니다.
     */
    private Location randomLocationInBorder(World world) {
        double radius = world.getWorldBorder().getSize() / 2.0;
        if (radius > 200) radius = 200; // 초반 자기장이 너무 크면 아무도 못 감

        double x = (random.nextDouble() * 2 - 1) * radius;
        double z = (random.nextDouble() * 2 - 1) * radius;

        // 청크를 먼저 로드해야 getHighestBlockYAt이 정확한 높이를 돌려줍니다.
        Chunk chunk = world.getChunkAt((int) x >> 4, (int) z >> 4);
        if (!chunk.isLoaded()) chunk.load(true);

        int y = world.getHighestBlockYAt((int) x, (int) z);
        if (y <= 0) return null;

        return new Location(world, Math.floor(x), y + 1, Math.floor(z));
    }
}
