package org.example.events;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.game.EventPicker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 일정 주기마다 이벤트를 하나 추첨해 발동합니다.
 *
 * Main.gameTimer에서 1초마다 tick()을 받습니다. 별도 스케줄러를 만들지 않는 것은
 * 능력의 checkPassiveAbilities와 같은 방식입니다.
 *
 * 별도의 레지스트리 클래스를 두지 않는 이유: AbilityRegistry에 create(name)/isValid()가
 * 있는 것은 /능력변경 명령어가 그것들을 쓰기 때문입니다. 이벤트에는 그런 명령어가
 * 없으므로 같은 API를 복사하면 아무도 호출하지 않는 코드만 늘어납니다.
 */
public class GameEventManager {

    /** ★ 새 이벤트는 여기에 한 줄만 추가하면 됩니다. */
    private static final List<Supplier<GameEvent>> EVENTS = List.of();

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<String, GameEvent> byName = new LinkedHashMap<>();

    private int secondsSinceLast = 0;
    private String lastPicked = null;

    public GameEventManager(JavaPlugin plugin) {
        this.plugin = plugin;
        for (Supplier<GameEvent> supplier : EVENTS) {
            GameEvent e = supplier.get();
            byName.put(e.getName(), e);
        }
    }

    /** 게임 시작 시 호출해 주기와 직전 기록을 초기화합니다. */
    public void reset() {
        secondsSinceLast = 0;
        lastPicked = null;
    }

    /**
     * @param intervalSeconds 이벤트 간격(초). 0 이하면 이벤트 시스템을 끕니다.
     */
    public void tick(GameContext ctx, int intervalSeconds) {
        if (intervalSeconds <= 0 || byName.isEmpty()) return;

        secondsSinceLast++;
        if (secondsSinceLast < intervalSeconds) return;
        secondsSinceLast = 0;

        List<String> candidates = new ArrayList<>();
        for (GameEvent e : byName.values()) {
            if (e.canRun(ctx)) candidates.add(e.getName());
        }

        String picked = EventPicker.pick(candidates, lastPicked, random);
        if (picked == null) return;

        GameEvent event = byName.get(picked);
        lastPicked = picked;

        // 이벤트 하나가 터져도 게임 타이머 전체가 죽지 않도록 격리합니다.
        try {
            event.start(ctx);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[능력자] 이벤트 '" + picked + "' 실행 중 오류", e);
        }
    }

    /** 이벤트 공지에 공통으로 쓰는 형식입니다. */
    static void announce(String title, String detail) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "◆ [이벤트] " + ChatColor.WHITE + title);
        Bukkit.broadcastMessage(ChatColor.GRAY + "  " + detail);
        Bukkit.broadcastMessage("");
    }
}
