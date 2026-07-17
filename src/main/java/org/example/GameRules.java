package org.example;

import org.bukkit.GameRule;
import org.bukkit.World;

/**
 * 월드 게임룰 접근을 한 곳에 모아둡니다.
 *
 * PVP는 마인크래프트 1.21.9부터 게임룰(GameRule.PVP)로 바뀌었고, 그에 맞춰
 * World.setPVP()/getPVP()는 deprecated 되었습니다. 두 방식은 의미가 다릅니다:
 *
 *   - 예전(1.21 이하): PVP는 서버 런타임 필드였습니다. 월드에 저장되지 않고,
 *     월드를 로드할 때마다 server.properties의 pvp 값으로 초기화됐습니다.
 *   - 지금(1.21.9~): PVP는 게임룰이라 level.dat에 함께 저장됩니다.
 *     즉 한 번 꺼두면 서버를 재시작해도 꺼진 채로 남습니다.
 *
 * 이 차이가 중요한 이유는, PVP를 끈 채로 방치된 월드가 생기면 플레이어가 준 대미지가
 * 전부 차단되어(평타/헐크 슬램/블링커 대쉬/티모 독침 직격) "독 데미지만 들어가는"
 * 상태가 되기 때문입니다. 게임 월드를 매번 새로 만드는 현재 정책 덕분에 실제로 문제가
 * 되진 않지만, 코드가 옛 가정 위에 서 있지 않도록 게임룰 API로 통일합니다.
 */
public final class GameRules {

    private GameRules() {
    }

    /** 해당 월드에서 플레이어 간 전투를 허용할지 설정합니다. */
    public static void setPvp(World world, boolean allowed) {
        if (world == null) return;
        world.setGameRule(GameRule.PVP, allowed);
    }

    /**
     * 해당 월드에서 플레이어 간 전투가 허용되는지 확인합니다.
     * 게임룰 값이 없는 예외적인 경우에는 false로 봅니다.
     */
    public static boolean isPvp(World world) {
        if (world == null) return false;
        return Boolean.TRUE.equals(world.getGameRuleValue(GameRule.PVP));
    }
}
