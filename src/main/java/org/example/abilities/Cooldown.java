package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.function.LongSupplier;

/**
 * 능력 쿨타임. 기존 5종이 각자 lastUsed 필드와 남은시간 포맷 문자열을 복붙하고 있던 것을
 * 한 곳으로 모읍니다.
 *
 * 시계를 주입 가능하게 둔 이유: 테스트에서 20초를 실제로 기다릴 수 없습니다.
 * 운영 코드는 인자 없는 생성자를 쓰면 됩니다.
 *
 * 이 클래스는 tryUse()의 안내 메시지 때문에 Bukkit 타입을 참조하지만, 나머지 메서드는
 * 순수 계산이라 paper-api 없이도 테스트에서 호출할 수 있습니다.
 */
public final class Cooldown {

    /**
     * 전역 쿨타임 무시 스위치(/쿨타임 명령어). static인 이유: 서버 전역 토글인데
     * 인스턴스는 능력마다 따로 생기므로 다른 선택지가 없습니다.
     * 게임 종료 시 자동으로 꺼지지 않습니다 - OP가 명시적으로 끄는 물건입니다.
     */
    private static volatile boolean disabled = false;

    private final long durationMs;
    private final LongSupplier clock;
    private long lastUsed = 0;

    public Cooldown(long durationMs) {
        this(durationMs, System::currentTimeMillis);
    }

    public Cooldown(long durationMs, LongSupplier clock) {
        this.durationMs = durationMs;
        this.clock = clock;
    }

    public static void setDisabled(boolean value) {
        disabled = value;
    }

    public static boolean isDisabled() {
        return disabled;
    }

    /** 남은 밀리초. 준비됐으면 0. */
    public long remainingMs() {
        if (disabled) return 0;
        long left = (lastUsed + durationMs) - clock.getAsLong();
        return Math.max(0, left);
    }

    public boolean isReady() {
        return remainingMs() <= 0;
    }

    /**
     * 준비됐으면 소모하고 true. 아니면 아무것도 하지 않고 false.
     *
     * 무시 모드에서도 lastUsed는 갱신합니다 - 마우가의 둔화 패시브처럼 "쿨이 도는 중"을
     * 참조하는 로직이 무시 모드에서도 일관되게 "항상 준비됨"으로 보이게 하기 위함입니다.
     */
    public boolean consume() {
        if (!isReady()) return false;
        lastUsed = clock.getAsLong();
        return true;
    }

    /** 준비됐으면 소모하고 true. 아니면 p에게 남은시간을 안내하고 false. */
    public boolean tryUse(Player p, String busyMessage) {
        long left = remainingMs();
        if (left > 0) {
            p.sendMessage(ChatColor.RED + busyMessage
                    + " (남은 시간: " + String.format("%.1f", left / 1000.0) + "초)");
            return false;
        }
        lastUsed = clock.getAsLong();
        return true;
    }

    public void reset() {
        lastUsed = 0;
    }
}
