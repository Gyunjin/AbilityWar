package org.example.abilities;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 완전 투명화. 암살자와 데스웜이 씁니다.
 *
 * 포션(INVISIBILITY)을 쓰지 않는 이유: 바닐라 투명화는 입은 갑옷과 든 아이템이 그대로
 * 보입니다. 암살자는 검을 들어야 하므로 포션만으로는 "완전 투명화"가 성립하지 않습니다.
 * 갑옷을 벗겨 숨기는 방법도 있지만 손에 든 무기는 여전히 남고, 벗긴 갑옷을 되돌리는
 * 과정에서 사망/접속종료가 끼면 장비가 사라집니다.
 *
 * 대신 viewer.hideEntity()로 다른 플레이어들의 클라이언트에서 대상을 통째로 지웁니다.
 * 갑옷, 손아이템, 이름표, 파티클까지 전부 사라집니다. 대상 본인에게는 아무 변화가 없고,
 * 서버 측 히트박스와 대미지 판정은 그대로 살아 있습니다.
 *
 * show()는 반드시 호출돼야 합니다. 누락되면 영구 투명 버그가 됩니다(티모가 겪었던 것과
 * 같은 계열). 호출 경로: 은신 만료 태스크, 공격에 의한 해제, onRevoke, 그리고 안전망으로
 * onPassiveTick의 정합성 체크.
 */
public final class Vanish {

    private static final Set<UUID> hidden = Collections.synchronizedSet(new HashSet<>());

    private Vanish() {
    }

    /** p를 다른 모든 온라인 플레이어에게서 숨깁니다. */
    public static void hide(Plugin plugin, Player p) {
        if (p == null) return;
        hidden.add(p.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(p)) {
                viewer.hidePlayer(plugin, p);
            }
        }
    }

    /** 숨김을 해제합니다. 이미 보이는 상태면 무시(중복 호출은 무해). */
    public static void show(Plugin plugin, Player p) {
        if (p == null) return;
        hidden.remove(p.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(p)) {
                viewer.showPlayer(plugin, p);
            }
        }
    }

    public static boolean isHidden(Player p) {
        return p != null && hidden.contains(p.getUniqueId());
    }

    /**
     * 방금 접속한 플레이어에게 현재 은신 중인 사람들을 다시 숨깁니다.
     *
     * 이게 없으면 은신 도중 접속한 플레이어에게는 hidePlayer가 걸려 있지 않아
     * 그 사람 눈에만 은신자가 보입니다.
     */
    public static void reapplyFor(Plugin plugin, Player joiner) {
        if (joiner == null) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(joiner) && isHidden(other)) {
                joiner.hidePlayer(plugin, other);
            }
        }
    }
}
