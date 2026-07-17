package org.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.map.MapView;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.example.events.EventSpawns;
import org.example.events.GameContext;
import org.example.events.GameEventManager;

public class Main extends JavaPlugin implements Listener {

    private boolean isGameStarted = false;
    private boolean countdownInProgress = false;
    private int timeElapsed = 0;
    private BukkitRunnable gameTimer;
    private World gameWorld = null;

    private Scoreboard gameScoreboard;
    private Objective scoreboardObjective;

    /**
     * 게임 시작 전(대기시간) 동안 변경된 블록의 "변경되기 전" 상태를 위치별로 1개씩만 기록합니다.
     * key = "월드이름;x;y;z", value = BlockData.getAsString() (변경 전 상태)
     *
     * 예전에는 List<BlockState>에 변경 순서대로 계속 쌓아두고 게임 시작 시 역순으로 되돌리는
     * 방식이었는데, 이 리스트가 메모리에만 있다 보니 작업대를 설치해둔 뒤 서버가 재시작되거나
     * 플러그인이 리로드되면 기록이 통째로 사라져서 해당 블록이 초기화되지 않고 그대로 남는
     * 문제가 있었습니다. 이제는 각 위치의 "최초 변경 전 상태"만 config에도 함께 저장해두므로,
     * 재시작이 발생해도 다음 /게임시작 때 정확히 원래 상태로 복원됩니다.
     */
    private final Map<String, String> pendingLobbyChanges = new LinkedHashMap<>();

    private AbilityManager abilityManager;
    private final TeamManager teamManager = new TeamManager();
    private HealthBarListener healthBarListener;
    private GameEventManager gameEventManager;

    // 게임 종료 후 결과 요약(이름/능력/킬수)에 쓰이는 정보
    private final Set<UUID> gameParticipants = new HashSet<>();
    private final Map<UUID, Integer> killCounts = new HashMap<>();

    // 실제로 "사망하여 탈락 처리된" 플레이어만 추적합니다.
    // 테스트용으로 게임모드를 크리에이티브 등으로 바꾸는 것과 실제 탈락(죽음)을 구분하기 위함입니다.
    private final Set<UUID> eliminatedPlayers = new HashSet<>();

    // 게임 진행 중 나간 플레이어가 재접속했을 때 원래 있던 자리로 되돌리기 위해 기억해둡니다.
    private final Map<UUID, Location> lastQuitLocation = new HashMap<>();

    /** 자기장 크기의 최소값. Bukkit의 WorldBorder는 1 미만을 받지 않습니다. */
    private static final double MIN_BORDER_SIZE = 1.0;

    /** 게임용으로 자동 생성하는 월드 이름의 접두사. 이 접두사가 붙은 월드만 자동 삭제 대상입니다. */
    private static final String GAME_WORLD_PREFIX = "AbilityWarWorld_";
    /** 자동 삭제 대상 판정용. 실수로 다른 월드를 지우지 않도록 "접두사 + 숫자"만 정확히 매칭합니다. */
    private static final Pattern GAME_WORLD_DIR_PATTERN = Pattern.compile("^AbilityWarWorld_\\d+$");

    private double cfgInitialBorderSize = 100.0;
    private double cfgFinalBorderSize = 10.0;
    private double cfgBorderDamage = 1.0;
    private int cfgFarmingTime = 1800;
    private int cfgCombatTime = 300;
    private int cfgEventInterval = 300;

    private boolean borderShrinking = false;
    // timeElapsed == cfgFarmingTime 같은 "정확히 같을 때만" 트리거하는 방식은
    // cfgFarmingTime이 0으로 설정되거나(시작 직후 1로 증가하므로 0과 절대 같아지지 않음),
    // 게임 도중 관리자가 /게임설정으로 시간을 이미 지나간 값으로 바꾸면 그 게임 내내
    // 조건이 영원히 성립하지 않아 PVP도 켜지지 않고 자기장도 줄어들지 않는 문제가 있었습니다.
    // 그래서 "이미 발동했는지"를 별도 플래그로 관리하고, 비교도 >=로 바꿔 한 번은 반드시 발동하도록 합니다.
    private boolean pvpActivated = false;

    @Override
    public void onEnable() {
        try {
            getConfig().options().copyDefaults(true);
            saveDefaultConfig();
        } catch (Exception e) {
            getLogger().warning("[능력자] 콘피그 파일을 생성하는 중 문제가 발생했으나 무시하고 진행합니다.");
        }

        if (this.getCommand("게임시작") != null) this.getCommand("게임시작").setExecutor(this);
        if (this.getCommand("맵변경") != null) this.getCommand("맵변경").setExecutor(this);
        if (this.getCommand("게임종료") != null) this.getCommand("게임종료").setExecutor(this);
        if (this.getCommand("능력변경권") != null) this.getCommand("능력변경권").setExecutor(this);

        if (this.getCommand("게임설정확인") != null) this.getCommand("게임설정확인").setExecutor(this);

        if (this.getCommand("능력변경") != null) {
            this.getCommand("능력변경").setExecutor(this);
            this.getCommand("능력변경").setTabCompleter(this);
        }

        if (this.getCommand("게임설정") != null) {
            this.getCommand("게임설정").setExecutor(this);
            this.getCommand("게임설정").setTabCompleter(this);
        }

        // 아래 네 개는 plugin.yml에 선언되어 있고 onCommand()에 로직도 전부 구현돼 있었지만
        // setExecutor 등록이 빠져 있어서, 실행하면 executor가 null이라 usage 메시지만 뜨고
        // 코드가 한 줄도 실행되지 않았습니다(팀전 기능 전체와 관전 기능이 사용 불가 상태).
        if (this.getCommand("팀전") != null) this.getCommand("팀전").setExecutor(this);
        if (this.getCommand("팀자동편성") != null) this.getCommand("팀자동편성").setExecutor(this);
        if (this.getCommand("팀설정") != null) {
            this.getCommand("팀설정").setExecutor(this);
            this.getCommand("팀설정").setTabCompleter(this);
        }
        if (this.getCommand("관전") != null) {
            this.getCommand("관전").setExecutor(this);
            this.getCommand("관전").setTabCompleter(this);
        }

        // /게임설정으로 바꾼 값들이 서버 재시작 후 기본값으로 되돌아가던 문제 수정:
        // 이전에는 인스턴스 필드에만 저장되고 config에는 저장되지 않았습니다.
        cfgInitialBorderSize = getConfig().getDouble("cfg-initial-border-size", cfgInitialBorderSize);
        cfgFinalBorderSize = getConfig().getDouble("cfg-final-border-size", cfgFinalBorderSize);
        cfgBorderDamage = getConfig().getDouble("cfg-border-damage", cfgBorderDamage);
        cfgFarmingTime = getConfig().getInt("cfg-farming-time", cfgFarmingTime);
        cfgCombatTime = getConfig().getInt("cfg-combat-time", cfgCombatTime);
        cfgEventInterval = getConfig().getInt("cfg-event-interval", cfgEventInterval);

        this.abilityManager = new AbilityManager(this);
        this.gameEventManager = new GameEventManager(this);
        getServer().getPluginManager().registerEvents(this.abilityManager, this);
        getServer().getPluginManager().registerEvents(this.teamManager, this);
        getServer().getPluginManager().registerEvents(this, this);

        setupScoreboard();

        // 서버를 열면 항상 새 게임용 맵을 생성합니다.
        //
        // 예전에는 config의 was-game-in-progress 플래그를 보고 "게임 도중 서버가 내려간
        // 경우"에만 옛 맵을 이어받았습니다. 그런데 onDisable()이 이 플래그를 정리하지 않아서,
        // /게임종료 없이 서버를 한 번이라도 내리면 플래그가 true로 굳어버렸습니다. 그 뒤로는
        // 매 재시작마다 옛 맵을 다시 물고 늘어져 새 맵이 영영 생성되지 않았고,
        // /게임종료로도 풀 수 없었습니다(isGameStarted가 false라 명령어가 그냥 무시됨).
        //
        // 게다가 startGame()의 폴백(Bukkit.getWorlds().get(0))이 한 번이라도 타면
        // config에 current-game-world="world"(기본 월드)가 저장되어, 이후 매 서버 시작마다
        // 기본 월드의 PVP를 꺼놓고 타이머는 돌리지 않는 상태가 됐습니다.
        // 이것이 "자기장이 안 줄어듦", "평타/헐크 슬램은 안 들어가는데 독 데미지만 들어감"
        // (PVP가 꺼져 있으면 공격 주체가 플레이어인 모든 대미지가 차단됨)의 원인이었습니다.
        //
        // 이어받기 기능 자체를 없애 이 고착 상태가 원천적으로 생길 수 없게 했습니다.

        // 새 맵을 만들기 전에 실행해야 합니다. createFreshGameWorld()가 current-game-world를
        // 덮어쓰기 때문에, 예전 값을 읽을 수 있는 마지막 시점입니다.
        repairNonGameWorldBorder();

        getLogger().info("[능력자] 새 게임용 맵을 자동 생성합니다...");
        gameWorld = createFreshGameWorld();
        if (gameWorld != null) {
            getLogger().info("[능력자] 새 게임용 월드 생성 완료: " + gameWorld.getName());
        } else {
            getLogger().warning("[능력자] 새 게임용 월드 생성에 실패했습니다. /맵변경 명령어로 직접 생성해 주세요.");
        }

        cleanupOldGameWorlds();

        // 서버 재시작 등으로 세션이 끊기기 전, 대기시간 동안 기록해둔(아직 게임 시작으로
        // 초기화되지 않은) 블록 변경 내역이 config에 남아있다면 다시 불러옵니다.
        // 이렇게 해야 재시작이 있었더라도 다음 /게임시작 때 대기시간 변경분이 정확히 초기화됩니다.
        loadPendingLobbyChanges();

        healthBarListener = new HealthBarListener(this);
        getServer().getPluginManager().registerEvents(healthBarListener, this);

        getLogger().info("능력자 플러그인이 성공적으로 활성화되었습니다!");
    } // onEnable() 메서드가 끝나는 괄호

    @Override
    public void onDisable() {
        stopTimer();

        // 능력 정리를 하지 않고 내려가면 헐크의 최대 체력 40이 플레이어 데이터에 그대로 남고,
        // 네크로맨서 좀비(persistent 설정)는 월드에 영구히 박제됩니다. 서버가 어떻게 내려가든
        // 항상 원상복구되도록 여기서 일괄 정리합니다.
        if (gameWorld != null) {
            EventSpawns.sweep(gameWorld, this);
        }

        if (abilityManager != null) {
            abilityManager.clearAbilities();
        }

        if (healthBarListener != null) {
            healthBarListener.shutdown();
        }
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            gameScoreboard = manager.getNewScoreboard();
            scoreboardObjective = gameScoreboard.registerNewObjective("abilitywar", "dummy", ChatColor.GOLD + ChatColor.BOLD.toString() + "[ 능력자 전쟁 ]");
            scoreboardObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
    }

    private void updateScoreboard() {
        if (gameScoreboard == null || scoreboardObjective == null) return;

        for (String entry : gameScoreboard.getEntries()) {
            gameScoreboard.resetScores(entry);
        }

        int minutes = timeElapsed / 60;
        int seconds = timeElapsed % 60;
        String timeStr = String.format("%02d:%02d", minutes, seconds);

        String pvpStatus = (timeElapsed < cfgFarmingTime) ? ChatColor.GREEN + "파밍 기간 (PVP Off)" : ChatColor.RED + "전투 기간 (PVP On)";
        double borderSize = (gameWorld != null) ? gameWorld.getWorldBorder().getSize() : cfgInitialBorderSize;
        String borderStr = String.format("%.0fx%.0f", borderSize, borderSize);

        int survivors = getSurvivorCount();

        scoreboardObjective.getScore(ChatColor.WHITE + "----------------------").setScore(9);
        scoreboardObjective.getScore(ChatColor.YELLOW + "▶ 진행 시간: " + ChatColor.WHITE + timeStr).setScore(8);
        scoreboardObjective.getScore(ChatColor.GREEN + "▶ 남은 생존자: " + ChatColor.WHITE + survivors + "명").setScore(7);
        scoreboardObjective.getScore(ChatColor.WHITE + " ").setScore(6);
        scoreboardObjective.getScore(ChatColor.AQUA + "▶ 현재 상태: ").setScore(5);
        scoreboardObjective.getScore("  " + pvpStatus).setScore(4);
        scoreboardObjective.getScore(ChatColor.WHITE + "  ").setScore(3);
        scoreboardObjective.getScore(ChatColor.LIGHT_PURPLE + "▶ 전장 크기: " + ChatColor.WHITE + borderStr).setScore(2);
        scoreboardObjective.getScore(ChatColor.WHITE + "---------------------- ").setScore(1);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(gameScoreboard);
        }
    }

    /**
     * 자기장 중심(0,0)의 스폰 좌표를 계산합니다. 청크가 아직 생성/로드되기 전에
     * getHighestBlockYAt()을 호출하면 부정확한 높이가 나와 플레이어가 땅에 박히는
     * 문제가 있어, 좌표 계산 전에 항상 청크를 강제로 로드합니다.
     */
    private Location getGameSpawnLocation() {
        if (gameWorld == null) return null;

        // 안전한 기본 스폰 지점 계산
        org.bukkit.Chunk chunk = gameWorld.getChunkAt(0, 0);
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }

        int y = gameWorld.getHighestBlockYAt(0, 0);
        // 만약 Y 좌표가 음수이거나 비정상적일 경우 기본값 64(혹은 안전지대)로 보정
        if (y <= 0) {
            y = 64;
        }
        return new Location(gameWorld, 0.5, y + 2, 0.5);
    }

    private void clearScoreboardAllPlayers() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setScoreboard(manager.getMainScoreboard());
            }
        }
    }

    private String locationKey(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    /** 대기시간 중 블록이 바뀔 때마다 호출됩니다. 같은 위치는 "최초 변경 전 상태"만 유지합니다. */
    private void recordLobbyChange(Location loc, BlockData beforeData) {
        String key = locationKey(loc);
        if (pendingLobbyChanges.containsKey(key)) return; // 이미 원래 상태가 기록돼있으면 덮어쓰지 않음
        pendingLobbyChanges.put(key, beforeData.getAsString());
        savePendingLobbyChanges();
    }

    private void savePendingLobbyChanges() {
        List<String> serialized = new ArrayList<>();
        for (Map.Entry<String, String> entry : pendingLobbyChanges.entrySet()) {
            serialized.add(entry.getKey() + "|" + entry.getValue());
        }
        try {
            getConfig().set("pending-lobby-changes", serialized);
            saveConfig();
        } catch (Exception e) {
            getLogger().warning("[능력자] 대기시간 블록 변경 기록 저장 중 오류: " + e.getMessage());
        }
    }

    private void loadPendingLobbyChanges() {
        pendingLobbyChanges.clear();
        List<String> serialized = getConfig().getStringList("pending-lobby-changes");
        for (String line : serialized) {
            int idx = line.indexOf('|');
            if (idx < 0) continue;
            pendingLobbyChanges.put(line.substring(0, idx), line.substring(idx + 1));
        }
    }

    private void clearPendingLobbyChanges() {
        pendingLobbyChanges.clear();
        try {
            getConfig().set("pending-lobby-changes", new ArrayList<String>());
            saveConfig();
        } catch (Exception e) {
            getLogger().warning("[능력자] 대기시간 블록 변경 기록 초기화 중 오류: " + e.getMessage());
        }
    }

    /**
     * 대기시간(게임 시작 전) 동안 변경된 모든 블록을 원래 상태로 되돌립니다.
     * config에 저장해둔 기록을 사용하므로, 기록 이후 서버가 재시작됐어도 항상 정확히 복원됩니다.
     */
    private void revertLobbyChanges() {
        if (gameWorld == null || pendingLobbyChanges.isEmpty()) {
            clearPendingLobbyChanges();
            return;
        }
        for (Map.Entry<String, String> entry : pendingLobbyChanges.entrySet()) {
            String[] parts = entry.getKey().split(";");
            if (parts.length != 4) continue;
            try {
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                world.getChunkAt(x >> 4, z >> 4).load(true);
                BlockData data = Bukkit.createBlockData(entry.getValue());
                world.getBlockAt(x, y, z).setBlockData(data, false);
            } catch (Exception e) {
                getLogger().warning("[능력자] 대기시간 블록 복원 중 오류(" + entry.getKey() + "): " + e.getMessage());
            }
        }
        clearPendingLobbyChanges();
    }

    /** 능력 아이템(귀속 장비, "[능력]" 태그)인지 확인합니다. 사망 드랍 방지 등에 공용으로 사용합니다. */
    private boolean isBoundItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains("[능력]");
    }

    /**
     * "왜 서로 때려도 데미지가 안 들어가지?" 같은 상황을 추측 없이 바로 진단하기 위한 정보입니다.
     *
     * 핵심은 두 가지입니다:
     *  1) 내가 지금 게임 월드에 있는가 (다른 월드에 있으면 자기장도 PVP도 나와 무관합니다)
     *  2) 내가 있는 월드의 PVP가 켜져 있는가
     * PVP가 꺼져 있으면 마인크래프트가 "공격 주체가 플레이어인 대미지"만 전부 차단합니다.
     * 그래서 평타/헐크 슬램/독침 직격은 안 들어가는데 독 데미지만 들어가는 현상이 생깁니다.
     */
    private void sendDiagnostics(Player player) {
        player.sendMessage(ChatColor.GOLD + "---------------- [ 현재 상태 진단 ] ----------------");

        World here = player.getWorld();
        boolean inGameWorld = (gameWorld != null) && here.equals(gameWorld);

        player.sendMessage(ChatColor.YELLOW + "▶ 게임 진행 중: " + ChatColor.WHITE + (isGameStarted ? "예 (" + timeElapsed + "초 경과)" : "아니오"));
        player.sendMessage(ChatColor.YELLOW + "▶ 게임 월드: " + ChatColor.WHITE + (gameWorld != null ? gameWorld.getName() : "없음"));
        player.sendMessage(ChatColor.YELLOW + "▶ 내가 있는 월드: " + (inGameWorld ? ChatColor.WHITE : ChatColor.RED) + here.getName()
                + (inGameWorld ? ChatColor.GREEN + " (게임 월드 O)" : ChatColor.RED + " (게임 월드 X - 자기장/PVP가 적용되지 않습니다!)"));

        boolean pvp = GameRules.isPvp(here);
        player.sendMessage(ChatColor.YELLOW + "▶ 이 월드의 PVP: " + (pvp ? ChatColor.GREEN + "켜짐" : ChatColor.RED + "꺼짐 (플레이어가 준 대미지가 전부 차단됩니다)"));

        double size = here.getWorldBorder().getSize();
        player.sendMessage(ChatColor.YELLOW + "▶ 이 월드의 자기장: " + ChatColor.WHITE + String.format("%.0f", size) + "x" + String.format("%.0f", size));
    }

    /** 관리자(OP) 전용 명령어인지 확인합니다. /게임설정확인, /관전, /팀전은 누구나 쓸 수 있습니다. */
    private boolean isAdminCommand(String name) {
        switch (name) {
            case "게임시작":
            case "게임종료":
            case "맵변경":
            case "게임설정":
            case "능력변경":
            case "능력변경권":
            case "팀자동편성":
            case "팀설정":
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        // 게임 진행에 직접 영향을 주는 명령어는 관리자만 쓸 수 있게 합니다.
        // (예전에는 /능력변경권만 OP 검사를 했고, 일반 플레이어가 /게임종료나 /맵변경으로
        //  진행 중인 게임을 끝내거나 맵을 통째로 갈아버릴 수 있었습니다.)
        if (isAdminCommand(cmd.getName()) && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "이 명령어는 오피(OP) 권한이 있는 관리자만 사용할 수 있습니다.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("게임설정확인")) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "========== [ 능력자 게임 설정 현황 ] ==========");
            player.sendMessage(ChatColor.YELLOW + "▶ 초기 자기장 크기: " + ChatColor.WHITE + (int)cfgInitialBorderSize + "x" + (int)cfgInitialBorderSize);
            player.sendMessage(ChatColor.YELLOW + "▶ 자기장 외곽 대미지: " + ChatColor.WHITE + cfgBorderDamage + " (초당)");
            player.sendMessage(ChatColor.YELLOW + "▶ 평화 파밍 시간: " + ChatColor.WHITE + cfgFarmingTime + "초 (" + (cfgFarmingTime / 60) + "분)");
            player.sendMessage(ChatColor.YELLOW + "▶ 자기장 축소 시간: " + ChatColor.WHITE + cfgCombatTime + "초 (" + (cfgCombatTime / 60) + "분)");
            player.sendMessage(ChatColor.YELLOW + "▶ 최종 자기장 고정 크기: " + ChatColor.WHITE + (int)cfgFinalBorderSize + "x" + (int)cfgFinalBorderSize);
            player.sendMessage(ChatColor.YELLOW + "▶ 이벤트 간격: " + ChatColor.WHITE
                    + (cfgEventInterval > 0 ? cfgEventInterval + "초" : "꺼짐"));
            sendDiagnostics(player);
            player.sendMessage(ChatColor.GOLD + "===============================================");
            player.sendMessage("");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("능력변경권")) {
            // OP 검사는 위 isAdminCommand()에서 공통으로 처리합니다.
            ItemStack ticket = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = ticket.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + ChatColor.BOLD.toString() + "능력 변경권");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "게임 시작 전 우클릭하면 능력이 무작위로 변경됩니다.");
                meta.setLore(lore);
                ticket.setItemMeta(meta);
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("@a")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.getInventory().addItem(ticket.clone());
                }
                player.sendMessage(ChatColor.GREEN + "모든 플레이어(@a)에게 능력 변경권을 지급했습니다.");
                return true;
            }

            if (args.length > 0) {
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null) {
                    target.getInventory().addItem(ticket);
                    player.sendMessage(ChatColor.GREEN + target.getName() + "님에게 능력 변경권을 지급했습니다.");
                } else {
                    player.sendMessage(ChatColor.RED + "해당 플레이어를 찾을 수 없습니다.");
                }
                return true;
            }

            player.getInventory().addItem(ticket);
            player.sendMessage(ChatColor.GREEN + "능력 변경권을 인벤토리에 지급했습니다.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("게임설정")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "사용법: /게임설정 [설정명] [숫자값]");
                return true;
            }

            String option = args[0];
            double value;
            try { value = Double.parseDouble(args[1]); } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "값은 숫자로만 입력해 주세요.");
                return true;
            }

            switch (option) {
                case "시작크기":
                    if (value < MIN_BORDER_SIZE) {
                        player.sendMessage(ChatColor.RED + "자기장 크기는 " + (int) MIN_BORDER_SIZE + " 이상이어야 합니다.");
                        return true;
                    }
                    cfgInitialBorderSize = value;
                    break;
                case "최종크기":
                    // 1 미만이면 WorldBorder.setSize()가 예외를 던집니다.
                    if (value < MIN_BORDER_SIZE) {
                        player.sendMessage(ChatColor.RED + "자기장 크기는 " + (int) MIN_BORDER_SIZE + " 이상이어야 합니다.");
                        return true;
                    }
                    cfgFinalBorderSize = value;
                    break;
                case "자기장대미지": cfgBorderDamage = value; break;
                case "평화시간(초)": cfgFarmingTime = (int) value; break;
                case "전투시간(초)": cfgCombatTime = (int) value; break;
                case "이벤트간격(초)": cfgEventInterval = (int) value; break;
                default:
                    player.sendMessage(ChatColor.RED + "존재하지 않는 설정입니다.");
                    return true;
            }

            // 최종 크기가 시작 크기보다 크면 자기장이 줄어드는 대신 넓어집니다.
            // 막지는 않되(의도적으로 그렇게 쓸 수도 있으므로) 실수일 가능성이 높아 알려줍니다.
            if (cfgFinalBorderSize > cfgInitialBorderSize) {
                player.sendMessage(ChatColor.YELLOW + "주의: 최종 크기(" + (int) cfgFinalBorderSize
                        + ")가 시작 크기(" + (int) cfgInitialBorderSize + ")보다 큽니다. 자기장이 줄어들지 않고 넓어집니다.");
            }

            try {
                getConfig().set("cfg-initial-border-size", cfgInitialBorderSize);
                getConfig().set("cfg-final-border-size", cfgFinalBorderSize);
                getConfig().set("cfg-border-damage", cfgBorderDamage);
                getConfig().set("cfg-farming-time", cfgFarmingTime);
                getConfig().set("cfg-combat-time", cfgCombatTime);
                getConfig().set("cfg-event-interval", cfgEventInterval);
                saveConfig();
            } catch (Exception e) {
                getLogger().warning("[능력자] 게임 설정 저장 중 오류: " + e.getMessage());
            }
            player.sendMessage(ChatColor.GREEN + option + " 설정이 " + args[1] + "(으)로 변경되었습니다.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("게임시작")) {
            if (isGameStarted || countdownInProgress) return true;
            startCountdownThenGame();
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("맵변경")) {
            if (isGameStarted) {
                player.sendMessage(ChatColor.RED + "게임 진행 중에는 맵을 변경할 수 없습니다. 먼저 /게임종료로 게임을 종료해 주세요.");
                return true;
            }
            if (countdownInProgress) {
                player.sendMessage(ChatColor.RED + "게임 시작 카운트다운 중에는 맵을 변경할 수 없습니다.");
                return true;
            }
            generateNewGameWorld(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("게임종료")) {
            if (!isGameStarted) return true;
            revealAllAbilities(); // 강제 종료 시에도 공개
            stopGameForce();
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("능력변경")) {
            if (args.length < 2) return true;
            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null) return true;

            String targetAbility = args[1];
            if (abilityManager.isValidAbility(targetAbility)) {
                abilityManager.changePlayerAbilityForce(targetPlayer, targetAbility);
                player.sendMessage(ChatColor.GREEN + targetPlayer.getName() + "님의 능력을 [" + targetAbility + "](으)로 변경했습니다.");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("팀전")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "현재 팀전 모드: " + (teamManager.isEnabled() ? "켜짐" : "꺼짐"));
                return true;
            }
            boolean on = args[0].equalsIgnoreCase("on");
            teamManager.setEnabled(on);
            player.sendMessage(ChatColor.GREEN + "팀전 모드를 " + (on ? "켰습니다." : "껐습니다."));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("팀자동편성")) {
            if (isGameStarted) {
                player.sendMessage(ChatColor.RED + "게임 진행 중에는 팀을 편성할 수 없습니다.");
                return true;
            }
            teamManager.autoAssignPairs(Bukkit.getOnlinePlayers());
            teamManager.setEnabled(true);
            player.sendMessage(ChatColor.GREEN + "온라인 플레이어를 2인 1팀으로 무작위 편성하고 팀전 모드를 켰습니다.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("팀설정")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "사용법: /팀설정 [팀번호] [플레이어]");
                return true;
            }
            int teamId;
            try {
                teamId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "팀번호는 숫자로 입력해 주세요.");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "해당 플레이어를 찾을 수 없습니다.");
                return true;
            }
            teamManager.assignManual(teamId, target);
            teamManager.setEnabled(true);
            player.sendMessage(ChatColor.GREEN + target.getName() + "님을 " + teamId + "팀으로 설정했습니다. (팀전 모드 자동 활성화)");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("관전")) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.sendMessage(ChatColor.RED + "관전자 모드일 때만 사용할 수 있는 명령어입니다.");
                return true;
            }
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "사용법: /관전 [플레이어]");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || target.getGameMode() != GameMode.SURVIVAL) {
                player.sendMessage(ChatColor.RED + "생존 중인 플레이어만 관전할 수 있습니다.");
                return true;
            }
            HealthBarListener.safeTeleport(player, target.getLocation());
            player.sendMessage(ChatColor.GREEN + target.getName() + " 님을 관전합니다.");
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("게임설정")) {
            if (args.length == 1) {
                String[] options = {"시작크기", "최종크기", "자기장대미지", "평화시간(초)", "전투시간(초)", "이벤트간격(초)"};
                for (String opt : options) { if (opt.startsWith(args[0])) completions.add(opt); }
            }
        }

        if (command.getName().equalsIgnoreCase("능력변경")) {
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) completions.add(p.getName());
                }
            }
            else if (args.length == 2) {
                for (String ability : abilityManager.getAbilityList()) { if (ability.startsWith(args[1])) completions.add(ability); }
            }
        }

        if (command.getName().equalsIgnoreCase("능력변경권") && args.length == 1) {
            completions.add("@a");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) completions.add(p.getName());
            }
        }
        if (command.getName().equalsIgnoreCase("관전") && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.SURVIVAL
                        && p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }

    /**
     * 새 게임용 월드를 생성하고 설정(자기장 크기, PVP off)까지 마친 뒤 config에 저장합니다.
     * 플레이어 텔레포트나 메시지 출력은 포함하지 않아, 서버 시작 시(온라인 플레이어 없음)와
     * /맵변경 명령어(플레이어 있음) 양쪽에서 공용으로 씁니다.
     */
    /**
     * 예전 버전이 게임 월드가 아닌 월드(기본 월드 등)를 게임 월드로 잘못 사용한 흔적이
     * 남아있으면 그 월드의 자기장을 바닐라 기본값으로 되돌립니다.
     *
     * 자기장(WorldBorder)은 PVP와 달리 level.dat에 저장됩니다. 그래서 예전 코드가
     * startGame()의 폴백으로 기본 월드에 100x100(또는 축소된 10x10) 자기장을 걸어버렸다면,
     * 플러그인을 고쳐도 그 자기장은 월드 데이터에 남아 계속 플레이어를 가둡니다.
     * (참고: PVP는 저장되지 않고 월드 로드 때마다 server.properties 값으로 초기화되므로
     *  더 이상 우리가 끄지만 않으면 저절로 정상으로 돌아옵니다.)
     */
    private void repairNonGameWorldBorder() {
        String saved = getConfig().getString("current-game-world", "");
        if (saved == null || saved.isEmpty()) return;
        if (saved.startsWith(GAME_WORLD_PREFIX)) return; // 정상적으로 만든 게임 월드

        World poisoned = Bukkit.getWorld(saved);
        if (poisoned == null) return;

        try {
            poisoned.getWorldBorder().reset(); // 바닐라 기본값(중심 0,0 / 크기 6천만)으로 복원
            getLogger().warning("[능력자] 예전 버전이 게임 월드로 잘못 사용한 '" + saved
                    + "' 월드의 자기장을 기본값으로 되돌렸습니다.");
        } catch (Exception e) {
            getLogger().warning("[능력자] '" + saved + "' 월드의 자기장 복구 중 오류: " + e.getMessage());
        }
    }

    private World createFreshGameWorld() {
        String worldName = GAME_WORLD_PREFIX + new Random().nextInt(10000);
        WorldCreator creator = new WorldCreator(worldName);
        creator.seed(new Random().nextLong());

        World newWorld;
        try {
            newWorld = Bukkit.createWorld(creator);
        } catch (Exception e) {
            getLogger().warning("[능력자] 월드를 생성하는 중 오류가 발생했습니다.");
            e.printStackTrace();
            return null;
        }

        if (newWorld != null) {
            try {
                getConfig().set("current-game-world", worldName);
                // 이어받기 기능을 없애면서 was-game-in-progress는 더 이상 읽지 않습니다.
                // 예전 config에 true로 굳어 남아있을 수 있으니 명시적으로 지웁니다.
                getConfig().set("was-game-in-progress", null);
                saveConfig();
            } catch (Exception e) {
                getLogger().warning("[능력자] 월드 이름을 콘피그에 저장하지 못했습니다.");
            }

            newWorld.getWorldBorder().setCenter(0, 0);
            newWorld.getWorldBorder().setSize(cfgInitialBorderSize);
            GameRules.setPvp(newWorld, false);
            // 새로 생성된 월드는 서버 기본 설정(또는 자동 생성 시 기본값)에 따라
            // 난이도가 평화로움으로 잡히는 경우가 있어, 매번 명시적으로 보통 난이도로 고정합니다.
            newWorld.setDifficulty(Difficulty.NORMAL);
            // 새 맵이므로 이전 맵에서 남아있던 대기시간 변경 기록은 의미가 없어 초기화합니다.
            clearPendingLobbyChanges();
        }
        return newWorld;
    }

    /**
     * 서버를 열 때마다 새 맵을 만들기 때문에 AbilityWarWorld_* 폴더가 계속 쌓입니다.
     * 최근 것 몇 개(cfg-keep-old-worlds, 기본 3개 - 방금 만든 현재 맵 포함)만 남기고
     * 나머지를 삭제합니다.
     *
     * 삭제는 되돌릴 수 없으므로 아래 조건을 모두 만족할 때만 지웁니다:
     *   - 폴더명이 정확히 "AbilityWarWorld_숫자" 형식일 것 (다른 월드는 절대 건드리지 않음)
     *   - 내부에 level.dat가 있어 실제 월드 폴더임이 확인될 것
     *   - 현재 게임 월드가 아닐 것, 서버 기본 월드가 아닐 것
     *   - 로드돼 있다면 플레이어가 없고 언로드에 성공할 것
     */
    private void cleanupOldGameWorlds() {
        int keep = Math.max(1, getConfig().getInt("cfg-keep-old-worlds", 3));

        File container = getServer().getWorldContainer();
        File[] entries = container.listFiles();
        if (entries == null) return;

        String currentName = (gameWorld != null) ? gameWorld.getName() : null;
        String defaultName = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getName();

        List<File> candidates = new ArrayList<>();
        for (File dir : entries) {
            if (!dir.isDirectory()) continue;
            if (!GAME_WORLD_DIR_PATTERN.matcher(dir.getName()).matches()) continue;
            if (!new File(dir, "level.dat").isFile()) continue; // 월드 폴더가 확실한 것만
            if (dir.getName().equals(currentName)) continue;
            if (dir.getName().equals(defaultName)) continue;
            candidates.add(dir);
        }

        // 최근에 쓴 것부터 정렬 후, 현재 맵을 뺀 나머지 보관 수만큼만 남깁니다.
        candidates.sort(Comparator.comparingLong(File::lastModified).reversed());
        int keepOthers = Math.max(0, keep - 1); // 현재 맵이 보관 개수 1개를 차지
        if (candidates.size() <= keepOthers) return;

        List<File> toDelete = candidates.subList(keepOthers, candidates.size());
        int deleted = 0;
        for (File dir : toDelete) {
            World loaded = Bukkit.getWorld(dir.getName());
            if (loaded != null) {
                if (!loaded.getPlayers().isEmpty()) {
                    getLogger().info("[능력자] '" + dir.getName() + "'에 플레이어가 있어 삭제를 건너뜁니다.");
                    continue;
                }
                if (!Bukkit.unloadWorld(loaded, false)) {
                    getLogger().warning("[능력자] '" + dir.getName() + "' 월드를 언로드하지 못해 삭제를 건너뜁니다.");
                    continue;
                }
            }
            if (deleteDirectory(dir)) {
                deleted++;
            } else {
                getLogger().warning("[능력자] '" + dir.getName() + "' 폴더를 완전히 삭제하지 못했습니다.");
            }
        }

        if (deleted > 0) {
            getLogger().info("[능력자] 오래된 게임 맵 " + deleted + "개를 삭제했습니다. (최근 " + keep + "개 보관)");
        }
    }

    /** 폴더를 통째로 삭제합니다. 전부 지워졌을 때만 true. */
    private boolean deleteDirectory(File dir) {
        File[] children = dir.listFiles();
        boolean ok = true;
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    ok &= deleteDirectory(child);
                } else {
                    ok &= child.delete();
                }
            }
        }
        return dir.delete() && ok;
    }

    private void generateNewGameWorld(Player admin) {
        Bukkit.broadcastMessage(ChatColor.RED + "[능력자] 새로운 게임용 월드를 생성하는 중입니다. 잠시만 기다려 주세요...");
        gameWorld = createFreshGameWorld();

        if (gameWorld == null) {
            admin.sendMessage(ChatColor.RED + "월드를 생성하는 중 오류가 발생했습니다. 콘솔을 확인하세요.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                Location spawnLocation = getGameSpawnLocation();
                if (spawnLocation == null) {
                    admin.sendMessage(ChatColor.RED + "스폰 위치를 계산하는 데 실패했습니다.");
                    return;
                }

                int moved = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setGameMode(GameMode.SURVIVAL);
                    p.getInventory().clear();

                    PlayerStats.resetToFullHealth(p);
                    p.setFoodLevel(20);
                    p.setSaturation(20.0f);

                    if (HealthBarListener.safeTeleport(p, spawnLocation)) moved++;
                }

                // 예전에는 텔레포트 성공 여부와 무관하게 "완료" 메시지를 띄웠습니다. 그래서
                // 체력바 마커 때문에 teleport()가 전부 실패하는 동안에도 "완료되었습니다"만
                // 뜨고 실제로는 아무도 이동하지 않아, 원인을 찾기 어려웠습니다.
                int online = Bukkit.getOnlinePlayers().size();
                if (moved < online) {
                    Bukkit.broadcastMessage(ChatColor.RED + "[능력자] 새 맵은 생성됐지만 "
                            + (online - moved) + "명이 이동하지 못했습니다. 콘솔을 확인해 주세요.");
                    getLogger().warning("[능력자] 새 맵으로 이동 실패: " + (online - moved) + "명 (탑승 상태 등으로 텔레포트 거부)");
                } else {
                    Bukkit.broadcastMessage(ChatColor.GOLD + "[능력자] 새로운 맵 생성 및 자동 이동이 완료되었습니다!");
                }
            }
        }.runTaskLater(this, 5L); // 5틱(0.25초) 대기 후 안전하게 텔레포트
    }

    private void startCountdownThenGame() {
        countdownInProgress = true;

        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());

        Bukkit.broadcastMessage(ChatColor.GOLD + "========================================");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "참가자 (" + names.size() + "명): " + ChatColor.WHITE + String.join(", ", names));
        if (teamManager.isEnabled()) {
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "팀전 모드가 활성화되어 있습니다.");
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "========================================");

        new BukkitRunnable() {
            int secondsLeft = 5;

            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle(ChatColor.GREEN + "게임 시작!", "", 0, 20, 10);
                    }
                    countdownInProgress = false;
                    startGame();
                    cancel();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(ChatColor.YELLOW + "" + secondsLeft, "", 0, 25, 5);
                }
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startGame() {
        isGameStarted = true;
        timeElapsed = 0;
        borderShrinking = false;
        pvpActivated = false;

        gameParticipants.clear();
        killCounts.clear();
        eliminatedPlayers.clear();
        lastQuitLocation.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            gameParticipants.add(p.getUniqueId());
        }

        // 대기시간(로비) 동안 플레이어들이 설치/파괴한 블록(작업대 등 포함)을 전부
        // 원래 상태로 되돌립니다. config 기반 기록을 사용하므로 서버 재시작 여부와
        // 관계없이 항상 정확하게 초기화됩니다.
        revertLobbyChanges();

        // 예전에는 gameWorld가 없으면 조용히 기본 월드(Bukkit.getWorlds().get(0))로 폴백했습니다.
        // 그러면 아래에서 기본 월드에 PVP 해제와 자기장이 걸리고, config의
        // current-game-world에 "world"가 저장되어 그 뒤로는 새 맵이 영영 생성되지 않았습니다.
        // 그 상태에서 서버를 재시작하면 기본 월드가 PVP off인 채로 방치되어
        // "평타/헐크 슬램은 안 들어가는데 독 데미지만 들어가는" 현상이 생겼습니다.
        // (PVP가 꺼져 있으면 공격 주체가 플레이어인 대미지만 차단되고 독은 통과합니다.)
        // 이제는 기본 월드를 절대 게임 월드로 쓰지 않고, 없으면 새로 만들거나 시작을 중단합니다.
        if (gameWorld == null) {
            getLogger().warning("[능력자] 게임 월드가 없어 새로 생성합니다.");
            gameWorld = createFreshGameWorld();
        }
        if (gameWorld == null) {
            isGameStarted = false;
            Bukkit.broadcastMessage(ChatColor.RED + "[능력자] 게임용 월드를 준비하지 못해 게임을 시작할 수 없습니다. /맵변경으로 맵을 먼저 생성해 주세요.");
            return;
        }

        gameWorld.getWorldBorder().setCenter(0, 0);
        gameWorld.getWorldBorder().setSize(cfgInitialBorderSize);
        GameRules.setPvp(gameWorld, false);
        gameWorld.setDifficulty(Difficulty.NORMAL);

        try {
            getConfig().set("current-game-world", gameWorld.getName());
            saveConfig();
        } catch (Exception e) {
            getLogger().warning("[능력자] 월드 이름을 콘피그에 저장하지 못했습니다.");
        }

        Location centerSpawn = getGameSpawnLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();

            PlayerStats.resetToFullHealth(p);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);

            HealthBarListener.safeTeleport(p, centerSpawn);
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "[능력자] 게임이 시작되었습니다! 모든 플레이어가 자기장 중심(0,0)으로 이동했습니다.");

        abilityManager.assignAbilitiesIfNone(Bukkit.getOnlinePlayers());
        giveBattleMapToAll();
        gameEventManager.reset();
        startTimer();
    }

    /**
     * 파밍이 끝나는 순간 이번 판에 등장한 능력의 "이름만" 공개합니다.
     * 누가 무엇을 가졌는지는 알리지 않습니다. 사망 시 공개(onPlayerDeath)와 맞물려
     * 소거법이 성립하고, 그것이 후반 교전의 긴장을 만듭니다.
     */
    private void revealAbilityLineup() {
        List<String> names = abilityManager.getAssignedAbilityNames();
        if (names.isEmpty()) return;

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "========== [ 이번 판 등장 능력 ] ==========");
        Bukkit.broadcastMessage(ChatColor.AQUA + "  " + String.join(ChatColor.GRAY + " · " + ChatColor.AQUA, names));
        Bukkit.broadcastMessage(ChatColor.GRAY + "  (누가 어떤 능력인지는 공개되지 않습니다)");
        Bukkit.broadcastMessage(ChatColor.GOLD + "==========================================");
        Bukkit.broadcastMessage("");
    }

    // 전체 플레이어의 능력을 채팅창에 공개하는 프라이빗 메서드
    private void revealAllAbilities() {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "=============== [ 전체 능력 공개 ] ===============");
        for (Player p : Bukkit.getOnlinePlayers()) {
            String abilityName = abilityManager.getPlayerAbilityName(p.getUniqueId());
            if (abilityName == null || abilityName.isEmpty()) abilityName = "없음";
            Bukkit.broadcastMessage(ChatColor.YELLOW + "▶ " + p.getName() + " 님의 능력: " + ChatColor.AQUA + "[" + abilityName + "]");
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "==================================================");
        Bukkit.broadcastMessage("");
    }

    // 게임 종료 시 참가자별 능력/킬수를 요약해서 보여줌 (abilityManager.clearAbilities()보다 반드시 먼저 호출)
    private void printGameSummary() {
        if (gameParticipants.isEmpty()) return;

        Bukkit.broadcastMessage(ChatColor.GOLD + "-------- [ 이번 게임 결과 ] --------");
        for (UUID uuid : gameParticipants) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = "알 수 없음";

            String ability = abilityManager.getPlayerAbilityName(uuid);
            if (ability == null || ability.isEmpty()) ability = "없음";

            int kills = killCounts.getOrDefault(uuid, 0);

            Bukkit.broadcastMessage(ChatColor.YELLOW + name + ChatColor.WHITE + " - 능력: " + ChatColor.AQUA + ability
                    + ChatColor.WHITE + " / 킬: " + ChatColor.RED + kills);
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "----------------------------------");
    }

    private void stopGameForce() {
        stopTimer();
        clearScoreboardAllPlayers();
        clearPendingLobbyChanges();
        borderShrinking = false;

        // 능력 정보가 초기화되기 전에 결과 요약부터 출력합니다.
        printGameSummary();

        // 오프라인 플레이어의 소환물/상태까지 포함해 모든 능력을 일괄 정리합니다.
        if (abilityManager != null) {
            abilityManager.clearAbilities();
        }

        // 이벤트가 스폰한 몬스터/상자를 회수합니다. 정리 지점은 여기 한 곳뿐입니다.
        if (gameWorld != null) {
            int removed = EventSpawns.sweep(gameWorld, this);
            if (removed > 0) {
                getLogger().info("[능력자] 이벤트 스폰물 " + removed + "개를 정리했습니다.");
            }
        }

        teamManager.clear();
        teamManager.setEnabled(false);
        lastQuitLocation.clear();

        if (gameWorld != null) {
            gameWorld.getWorldBorder().setSize(cfgInitialBorderSize);
            GameRules.setPvp(gameWorld, false);
        }

        Location centerReset = (gameWorld != null)
                ? getGameSpawnLocation()
                : new Location(Bukkit.getWorlds().get(0), 0, Bukkit.getWorlds().get(0).getHighestBlockYAt(0, 0) + 2, 0);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().clear();

            PlayerStats.resetToFullHealth(p);
            p.setFoodLevel(20);
            p.setSaturation(20.0f);
            p.setGameMode(GameMode.SURVIVAL);
            HealthBarListener.safeTeleport(p, centerReset);
            for (org.bukkit.potion.PotionEffect effect : p.getActivePotionEffects()) {
                p.removePotionEffect(effect.getType());
            }
        }

        try {
            getConfig().set("current-game-world", "");
            getConfig().set("was-game-in-progress", null);
            saveConfig();
        } catch (Exception e) {
            // 무시
        }

        Bukkit.broadcastMessage(ChatColor.DARK_RED + "[능력자] 게임이 종료되었습니다!");
    }

    private void giveBattleMapToAll() {
        if (gameWorld == null) return;

        MapView mapView = Bukkit.createMap(gameWorld);
        mapView.setCenterX(0);
        mapView.setCenterZ(0);

        String scaleStr = "NORMAL";
        if (cfgInitialBorderSize <= 128) {
            scaleStr = "CLOSEST";
        } else if (cfgInitialBorderSize <= 256) {
            scaleStr = "CLOSE";
        } else if (cfgInitialBorderSize <= 512) {
            scaleStr = "NORMAL";
        } else if (cfgInitialBorderSize <= 1024) {
            scaleStr = "FAR";
        } else {
            scaleStr = "FARTHEST";
        }

        try {
            mapView.setScale(MapView.Scale.valueOf(scaleStr));
        } catch (Exception e) {
            mapView.setScale(MapView.Scale.NORMAL);
        }
        mapView.setTrackingPosition(true);

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        if (mapMeta != null) {
            mapMeta.setMapView(mapView);
            mapMeta.setDisplayName(ChatColor.GOLD + "능력자 전장 지도 (" + (int)cfgInitialBorderSize + "x" + (int)cfgInitialBorderSize + ")");
            mapItem.setItemMeta(mapMeta);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getInventory().remove(Material.FILLED_MAP);
            p.getInventory().addItem(mapItem.clone());
        }
    }

    private void startTimer() {
        gameTimer = new BukkitRunnable() {
            @Override
            public void run() {
                timeElapsed++;
                updateScoreboard();

                if (abilityManager != null) {
                    abilityManager.checkPassiveAbilities(Bukkit.getOnlinePlayers(), isGameStarted);
                    abilityManager.recordHistoryTick(Bukkit.getOnlinePlayers(), isGameStarted);
                }

                int survivorCount = getSurvivorCount();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR) {
                        String info = ChatColor.GREEN + "생존자: " + ChatColor.WHITE + survivorCount + "명"
                                + ChatColor.GRAY + "  (/관전 [이름] 으로 다른 생존자 관전 가능)";
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(info));
                    }
                }

                if (gameWorld != null) {
                    double currentSize = gameWorld.getWorldBorder().getSize() / 2.0;
                    for (Player p : gameWorld.getPlayers()) {
                        if (p.getGameMode() != GameMode.SURVIVAL) continue;
                        Location loc = p.getLocation();
                        if (Math.abs(loc.getX()) > currentSize || Math.abs(loc.getZ()) > currentSize) {
                            // p.setHealth()로 직접 체력을 깎으면 EntityDamageEvent가 발생하지 않아
                            // 사망회귀 같은 능력이 반응할 수 없었음. p.damage()로 정식 대미지 이벤트를 태움.
                            p.damage(cfgBorderDamage);
                            // p.damage()는 마인크래프트 엔진의 "무적 프레임(noDamageTicks)"을 남깁니다.
                            // 이 자기장 대미지가 매초 반복되면서 무적 프레임이 계속 갱신되어, 그 사이에
                            // 들어오는 PVP 공격(자기장 대미지보다 작거나 같은 대미지)이 통째로 씹혀버려
                            // "서로 공격이 안 먹히는" 현상의 원인이 되었습니다. 사망회귀 능력이 회귀 직후
                            // 무적 프레임을 0으로 초기화하는 것과 동일한 방식으로 여기서도 초기화합니다.
                            p.setNoDamageTicks(0);
                            if (p.isOnline() && p.getHealth() > 0) {
                                p.sendMessage(ChatColor.RED + "⚠ 경고! 자기장 밖에 배치되어 대미지를 입고 있습니다! ⚠");
                            }
                        }
                    }
                }

                if (!pvpActivated && timeElapsed >= cfgFarmingTime) {
                    pvpActivated = true;
                    if (gameWorld != null) {
                        GameRules.setPvp(gameWorld, true);
                        // 단위를 반드시 명시할 것. WorldBorder.changeSize()의 두 번째 인자는
                        // "초"가 아니라 "틱"입니다. 예전 API의 setSize(크기, 초)를 그대로
                        // changeSize(크기, 초)로 바꿔 쓰는 바람에 cfgCombatTime이 틱으로
                        // 해석되어 축소가 20배 빨리 끝났습니다(300초 설정 -> 실제 15초).
                        // setSize(크기, TimeUnit, 시간)이 내부에서 초->틱 변환을 해줍니다.
                        gameWorld.getWorldBorder().setSize(cfgFinalBorderSize, TimeUnit.SECONDS, cfgCombatTime);
                    }
                    borderShrinking = true;
                    Bukkit.broadcastMessage(ChatColor.RED + "[능력자] 파밍 시간이 종료되었습니다! PVP가 활성화되며, "
                            + cfgCombatTime + "초에 걸쳐 자기장이 서서히 " + (int) cfgFinalBorderSize + "x" + (int) cfgFinalBorderSize + "(으)로 축소됩니다!");
                    revealAbilityLineup();
                }

                if (borderShrinking && timeElapsed >= cfgFarmingTime + cfgCombatTime) {
                    borderShrinking = false;
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "[능력자] 자기장이 최종 크기(" + (int) cfgFinalBorderSize + "x" + (int) cfgFinalBorderSize + ")로 축소 완료되었습니다!");
                }

                if (gameEventManager != null && gameWorld != null) {
                    List<Player> survivors = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (isSurvivingParticipant(p)) survivors.add(p);
                    }
                    GameContext ctx = new GameContext(Main.this, gameWorld, survivors,
                            timeElapsed < cfgFarmingTime, killCounts, abilityManager);
                    gameEventManager.tick(ctx, cfgEventInterval);
                }

                checkWinner();
            }
        };
        gameTimer.runTaskTimer(this, 0L, 20L);
    }

    private void stopTimer() {
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
        isGameStarted = false;
    }

    private int getSurvivorCount() {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isSurvivingParticipant(p)) count++;
        }
        return count;
    }

    /** 게임 참가자이면서 아직 탈락(사망) 처리되지 않은 플레이어인지 확인합니다.
     *  테스트용으로 게임모드를 바꾸는 것과 실제 탈락을 구분하기 위해 GameMode가 아닌
     *  eliminatedPlayers 기록을 기준으로 판정합니다. */
    private boolean isSurvivingParticipant(Player p) {
        return gameParticipants.contains(p.getUniqueId()) && !eliminatedPlayers.contains(p.getUniqueId());
    }

    public void checkWinner() {
        if (!isGameStarted) return;

        int onlineCount = Bukkit.getOnlinePlayers().size();
        List<Player> survivors = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isSurvivingParticipant(p)) survivors.add(p);
        }

        if (survivors.isEmpty()) {
            revealAllAbilities();
            String msg = (onlineCount > 1)
                    ? "[능력자] 모든 플레이어가 사망하여 우승자 없이 게임이 종료되었습니다."
                    : "[능력자] 테스트 모드: 플레이어가 사망하여 게임을 종료합니다.";
            Bukkit.broadcastMessage(ChatColor.RED + msg);
            stopGameForce();
            return;
        }

        if (onlineCount <= 1) return; // 테스트 모드에서는 전멸 전까지 계속 진행

        if (teamManager.isEnabled()) {
            // 팀 배정된 생존자는 팀 번호로, 미배정 생존자는 각자 독립된 그룹으로 취급합니다.
            Set<Object> remainingGroups = new HashSet<>();
            for (Player p : survivors) {
                Integer team = teamManager.getTeam(p.getUniqueId());
                remainingGroups.add(team != null ? team : p.getUniqueId());
            }

            if (remainingGroups.size() == 1) {
                revealAllAbilities();
                Integer winningTeam = teamManager.getTeam(survivors.get(0).getUniqueId());
                if (winningTeam != null) {
                    announceTeamWinner(winningTeam);
                } else {
                    announceWinner(survivors.get(0).getName());
                }
            }
        } else if (survivors.size() == 1) {
            revealAllAbilities();
            announceWinner(survivors.get(0).getName());
        }
    }

    private void announceTeamWinner(int teamId) {
        List<String> members = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Integer t = teamManager.getTeam(p.getUniqueId());
            if (t != null && t == teamId) members.add(p.getName());
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "========================================");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "★ 능력자 전쟁 최종 우승 팀 발표 ★");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.WHITE + "우승 팀: " + ChatColor.AQUA + String.join(", ", members));
        Bukkit.broadcastMessage(ChatColor.GOLD + "========================================");
        Bukkit.broadcastMessage("");
        stopGameForce();
    }

    private void announceWinner(String name) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "========================================");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "★ 능력자 전쟁 최종 우승자 발표 ★");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.WHITE + "최후의 생존자: " + ChatColor.AQUA + name);
        Bukkit.broadcastMessage(ChatColor.GOLD + "========================================");
        Bukkit.broadcastMessage("");
        stopGameForce();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();

        // 능력 아이템(귀속 장비, "[능력]" 태그)은 사망해도 드랍되지 않도록 제거합니다.
        event.getDrops().removeIf(this::isBoundItem);

        // 게임이 시작되지 않았어도(예: /맵변경 직후 테스트 중 사망) 항상 게임용 월드의
        // 스폰으로 리스폰되도록 덮어씁니다. 이걸 isGameStarted로 가드해버리면, 예전에
        // 다른(플러그인 없는 기본) 월드에서 저장된 침대 스폰이 그대로 남아있어서
        // 그쪽으로 되돌아가는 문제가 있었습니다.
        if (gameWorld != null) {
            Location respawnLoc = getGameSpawnLocation();
            deadPlayer.setBedSpawnLocation(respawnLoc, true);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    deadPlayer.spigot().respawn();
                } catch (Exception e) {
                    // 무시
                }
            }
        }.runTaskLater(this, 1L);

        if (!isGameStarted) return; // 게임 중이 아니면 킬수 집계/탈락 처리는 하지 않음

        Player killer = deadPlayer.getKiller();
        if (killer != null) {
            killCounts.merge(killer.getUniqueId(), 1, Integer::sum);
        }

        // 사망자의 능력을 공개합니다. 파밍 종료 시 공개된 등장 능력 목록과 맞물려
        // 남은 사람의 능력을 소거법으로 좁힐 수 있게 됩니다.
        String deadAbility = abilityManager.getPlayerAbilityName(deadPlayer.getUniqueId());
        if (deadAbility != null && !deadAbility.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "☠ " + ChatColor.WHITE + deadPlayer.getName()
                    + ChatColor.GRAY + " 님의 능력은 " + ChatColor.AQUA + "[" + deadAbility + "]"
                    + ChatColor.GRAY + " 이었습니다.");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                deadPlayer.setGameMode(GameMode.SPECTATOR);
                eliminatedPlayers.add(deadPlayer.getUniqueId());
                deadPlayer.sendMessage(ChatColor.RED + "탈락하셨습니다! 이제부터 다른 플레이어들을 관전합니다.");
                checkWinner();
            }
        }.runTaskLater(this, 3L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 게임 중이었다면 나간 위치를 기억해뒀다가, 재접속 시 그 자리로 되돌려줍니다.
        if (isGameStarted) {
            lastQuitLocation.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation().clone());
        }

        if (!isGameStarted) return;
        new BukkitRunnable() {
            @Override
            public void run() { checkWinner(); }
        }.runTaskLater(this, 10L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (gameWorld == null) {
            String savedWorldName = getConfig().getString("current-game-world", "");
            if (savedWorldName != null && !savedWorldName.isEmpty()) {
                gameWorld = Bukkit.getWorld(savedWorldName);
            }
        }

        if (gameWorld == null) return;

        // 게임이 진행 중이고, 이 플레이어가 게임 도중 나갔던 기록이 있다면(=이번 게임의
        // 같은 맵) 스폰(맵 중앙)이 아니라 나갔던 그 자리로 그대로 복귀시킵니다.
        // 그 외의 경우(게임 시작 전 로비 등)에는 기존처럼 스폰으로 이동시킵니다.
        Location savedLoc = lastQuitLocation.remove(player.getUniqueId());
        boolean restoreExactPosition = isGameStarted && savedLoc != null
                && savedLoc.getWorld() != null && savedLoc.getWorld().equals(gameWorld);

        Location destination = restoreExactPosition ? savedLoc : getGameSpawnLocation();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!restoreExactPosition) {
                    // 게임 중이 아니거나(로비) 이번 게임에서 나간 기록이 없는 경우에만
                    // 체력/허기를 초기화합니다. 자리를 그대로 복원하는 경우엔 상태도
                    // 나갔을 때 그대로 유지되도록 건드리지 않습니다.
                    PlayerStats.resetToFullHealth(player);
                    player.setFoodLevel(20);
                    player.setSaturation(20.0f);
                }

                HealthBarListener.safeTeleport(player, destination);
                if (isGameStarted) {
                    player.setScoreboard(gameScoreboard);
                }
            }
        }.runTaskLater(this, 5L);
    }

    // 파밍 시간 동안 플레이어가 받는 모든 대미지를 막던 onEntityDamage 핸들러를 제거했습니다.
    //
    // 파밍 중 플레이어끼리 못 때리는 것은 startGame()의 PVP 게임룰 해제가 이미
    // 처리합니다. PVP가 꺼져 있으면 마인크래프트가 "공격 주체가 플레이어인" 대미지를 전부
    // 차단하기 때문입니다(평타, 헐크 슬램, 블링커 대쉬, 티모 독침 직격 등). 그래서 여기서
    // 추가로 막을 필요가 없었고, 오히려 낙하/용암/몬스터 대미지까지 같이 막아버려서
    // 파밍 구간이 완전 무적 상태가 되어 있었습니다.
    //
    // 이제 파밍 중에도 환경/몬스터 위험은 정상적으로 살아있고, PVP만 잠깁니다.

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isGameStarted) recordLobbyChange(event.getBlock().getLocation(), event.getBlock().getBlockData());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isGameStarted) recordLobbyChange(event.getBlock().getLocation(), event.getBlockReplacedState().getBlockData());
    }

    public boolean isGameStarted() { return isGameStarted; }
}