package org.example.abilities;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 새 능력을 추가하려면:
 *   1. 이 인터페이스를 구현하는 새 클래스를 abilities 패키지에 만든다.
 *   2. AbilityRegistry의 static 블록에 register(새클래스::new) 한 줄만 추가한다.
 * 그게 전부입니다. 아이템, 쿨타임, 상태는 전부 해당 클래스 내부에 캡슐화되므로
 * AbilityManager나 다른 능력의 코드를 건드릴 필요가 없습니다.
 *
 * 주의: 한 플레이어는 항상 이 인터페이스의 새 인스턴스 하나를 받습니다 (공유 X).
 * 그래야 쿨타임, 소환물, 히스토리 같은 상태가 플레이어별로 독립적으로 유지됩니다.
 */
public interface Ability {

    /**
     * 능력이 발사체(예: 독침, 화살 등)를 자신의 소유로 표시할 때 공통으로 쓰는 메타데이터 키.
     * AbilityManager가 이 키를 보고 어떤 플레이어의 능력 인스턴스로 이벤트를 전달할지 판단합니다.
     * (능력별로 각자 다른 리스너를 등록하지 않고, AbilityManager가 전역에서 한 번만 구독한 뒤
     *  이 키를 통해 정확한 플레이어의 인스턴스로만 이벤트를 위임하기 위함입니다.)
     */
    String OWNER_META_KEY = "ability_owner";

    /** 명령어, 스코어보드, 안내 메시지 등에 표시될 능력 이름 (예: "블링커") */
    String getName();

    /**
     * 능력이 플레이어에게 지급될 때 호출됩니다.
     * @param isReGrant 게임 시작 시 인벤토리가 초기화된 뒤, 능력 변경권으로
     *                   이미 선택돼있던 능력을 장비만 다시 지급하는 경우 true.
     *                   (이 경우 상세 안내 메시지는 생략하는 것이 일반적)
     */
    void onGrant(Player p, boolean isReGrant);

    /**
     * 능력이 다른 능력으로 교체되거나 게임이 종료되어 초기화될 때 호출됩니다.
     * 스탯 원복(예: 최대 체력), 소환물 제거 등 뒷정리를 여기서 처리합니다.
     * p는 null일 수 있습니다 (플레이어가 오프라인 상태에서 정리되는 경우).
     */
    void onRevoke(Player p);

    /** 능력 아이템을 우클릭하는 등 액티브 스킬 발동 시 호출됩니다. */
    default void onInteract(Player p, PlayerInteractEvent event) {}

    /** 게임 진행 중 매 초(1틱) 호출되는 패시브 효과 훅입니다. */
    default void onPassiveTick(Player p) {}

    /** 몬스터가 플레이어를 타겟팅하려 할 때 호출됩니다. */
    default void onEntityTarget(Player p, EntityTargetEvent event) {}

    /** 플레이어가 엔티티에게 대미지를 받을 때 호출됩니다. */
    default void onEntityDamageByEntity(Player p, EntityDamageByEntityEvent event) {}

    default void onBlockPlace(Player p, BlockPlaceEvent event) {}

    default void onBlockBreak(Player p, BlockBreakEvent event) {}

    /**
     * 플레이어가 이동할 때마다 호출됩니다(전역 리스너를 AbilityManager가 한 번만 등록하고
     * 위임합니다). 착지 감지 등 이동 기반 능력에 사용합니다.
     */
    default void onPlayerMove(Player p, PlayerMoveEvent event) {}

    /**
     * 이 플레이어가 소유(OWNER_META_KEY 메타데이터로 표시)한 발사체가 무언가에 맞았을 때 호출됩니다.
     */
    default void onProjectileHit(ProjectileHitEvent event) {}

    /**
     * 플레이어가 어떤 원인이든(엔티티가 아닌 원인 포함, 예: 낙하) 대미지를 받을 때 호출됩니다.
     * 특정 원인(예: 낙하 대미지)만 다루고 싶으면 event.getCause()로 걸러서 처리하세요.
     */
    default void onEntityDamage(Player p, EntityDamageEvent event) {}

    /** 사망회귀처럼 매 틱 상태 스냅샷을 저장해야 하는 능력을 위한 훅입니다. */
    default void recordTick(Player p) {}

    /**
     * 치명적인 대미지(체력이 0 이하가 되는 대미지) 발생 시 호출됩니다.
     * @return true면 이 능력이 이벤트를 처리(취소 등)했다는 뜻입니다.
     */
    default boolean onFatalDamage(Player p, EntityDamageEvent event) { return false; }
}