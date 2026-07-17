package org.example.events;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

import java.util.List;
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

        Location loc = randomLocationInBorder(world, ctx);
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

    private static final int MAX_CANDIDATES = 12;

    /**
     * 현재 자기장 크기 안쪽의 무작위 지면 좌표를 고릅니다.
     * 자기장은 (0,0) 중심이므로 크기의 절반이 반경입니다. 축소가 진행 중이면
     * 그 시점의 실제 크기를 쓰므로 후반에는 중앙 근처에만 뜹니다.
     *
     * world.getChunkAt()은 청크가 없으면 그 자리에서 동기 생성을 해버려 메인 스레드가
     * 멈춥니다. 반경이 최대 200(400x400)이라 초반에는 미탐사 지역에 꽂힐 확률이 높으므로,
     * isChunkGenerated로 생성 여부만 확인하고(생성은 트리거하지 않음) 안 된 곳은 후보에서
     * 제외합니다. 여러 번 실패하면 생존자 근처(청크 로드가 보장됨)로 대체합니다.
     */
    private Location randomLocationInBorder(World world, GameContext ctx) {
        double radius = world.getWorldBorder().getSize() / 2.0;
        if (radius > 200) radius = 200; // 초반 자기장이 너무 크면 아무도 못 감

        for (int i = 0; i < MAX_CANDIDATES; i++) {
            double x = (random.nextDouble() * 2 - 1) * radius;
            double z = (random.nextDouble() * 2 - 1) * radius;

            Location candidate = groundLocationIfSafe(world, x, z);
            if (candidate != null) return candidate;
        }

        // 유효한 후보를 못 찾았으면 생존자 근처로 대체합니다. 이벤트 슬롯을 그냥 버리는
        // 것보다 상자가 조금 가까이 떨어지는 편이 낫습니다.
        List<Player> survivors = ctx.getSurvivors();
        if (survivors.isEmpty()) return null;

        Player anchor = survivors.get(random.nextInt(survivors.size()));
        Location base = anchor.getLocation();
        for (int i = 0; i < MAX_CANDIDATES; i++) {
            double x = base.getX() + (random.nextDouble() * 2 - 1) * 16;
            double z = base.getZ() + (random.nextDouble() * 2 - 1) * 16;

            Location candidate = groundLocationIfSafe(world, x, z);
            if (candidate != null) return candidate;
        }

        // 검증을 통과하는 후보를 끝내 못 찾았으면 검증 없이 배치하는 대신 이번 회차를
        // 건너뜁니다. 용암 속에 상자가 생기는 것보다 이벤트 슬롯을 버리는 편이 낫습니다.
        ctx.getPlugin().getLogger().info("[능력자] 보급 투하: 적합한 지점을 찾지 못해 이번 회차를 건너뜁니다.");
        return null;
    }

    /**
     * 주어진 x, z가 이미 생성된 청크이고, 상자를 놓을 자리가 용암/액체나 지형 속이
     * 아니면 배치 가능한 좌표를 반환합니다. 그렇지 않으면 null을 반환해 다음 후보를
     * 시도하게 합니다.
     */
    private Location groundLocationIfSafe(World world, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        // 청크 좌표와 블록 좌표를 같은 정수에서 유도해야 서로 다른 청크를 가리키지 않습니다.
        if (!world.isChunkGenerated(bx >> 4, bz >> 4)) return null;

        int y = world.getHighestBlockYAt(bx, bz);
        if (y <= 0) return null;

        // getHighestBlockYAt은 그냥 최상단 블록이라 용암 표면이나 나뭇잎 위일 수 있습니다.
        // 바닥은 단단해야 하고, 상자가 들어갈 자리와 그 위 칸은 비어 있어야 합니다.
        Block ground = world.getBlockAt(bx, y, bz);
        if (ground.isPassable() || ground.isLiquid()) return null;

        Block chestSpot = world.getBlockAt(bx, y + 1, bz);
        Block above = world.getBlockAt(bx, y + 2, bz);
        if (!chestSpot.isPassable() || chestSpot.isLiquid()) return null;
        if (!above.isPassable() || above.isLiquid()) return null;

        return new Location(world, bx, y + 1, bz);
    }
}
