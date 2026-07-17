package org.example.events;

/**
 * 게임 도중 무작위로 발동하는 사건.
 *
 * 새 이벤트를 추가하려면:
 *   1. 이 인터페이스를 구현하는 클래스를 events 패키지에 만든다.
 *   2. GameEventManager의 EVENTS 목록에 생성자 참조를 한 줄 추가한다.
 *
 * 모든 이벤트는 발동 즉시 끝나는 순간 이벤트다. 지속 효과가 필요하면 유한한
 * 지속시간을 가진 포션 효과를 쓴다(스스로 만료되므로 해제 코드가 필요 없다).
 * 스폰한 엔티티는 EventSpawns.tag()로 표식을 달아 게임 종료 시 회수되게 한다.
 */
public interface GameEvent {

    /** 공지와 로그에 쓰일 이름 (예: "보급 투하") */
    String getName();

    /** 발동. 예외를 던져도 GameEventManager가 잡아 게임 타이머는 계속 돈다. */
    void start(GameContext ctx);

    /**
     * 지금 발동할 수 있는지 여부. false면 추첨 후보에서 빠진다.
     * 전투 전용 이벤트는 ctx.isFarming()이 true일 때 false를 반환한다.
     */
    default boolean canRun(GameContext ctx) {
        return true;
    }
}
