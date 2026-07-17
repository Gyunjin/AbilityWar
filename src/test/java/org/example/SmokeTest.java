package org.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 테스트 실행 환경 자체가 살아있는지 확인하는 최소 테스트입니다.
 * 실제 로직 테스트가 추가되면 이 파일은 삭제해도 됩니다.
 */
class SmokeTest {

    @Test
    void junitRuns() {
        assertEquals(2, 1 + 1);
    }
}
