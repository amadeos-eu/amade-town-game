package eu.amadeos.games.town.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {
    private static final Rules RULES = Rules.load();

    @Test
    void theRulesHaveThreeMissionsAndEveryButtonSomethingToBuild() {
        assertEquals(3, RULES.missions());
        for (BuildingType.Tool tool : BuildingType.Tool.values()) {
            assertFalse(RULES.buildings(tool).isEmpty(), tool.name());
        }
        for (BuildingType type : RULES.buildings()) {
            assertTrue(type.cost() > 0 && type.width() > 0 && type.height() > 0, type.id());
        }
    }

    @Test
    void theThirdMissionHasNoCoal() {
        assertTrue(RULES.mission(3).forbidden().contains("coal"));
        assertTrue(RULES.mission(2).fire());
    }

    @Test
    void thePlanOnlyBuildsWhatTheRulesKnow() {
        for (Plan.Step step : RULES.plan().steps()) {
            if (step.action().equals("build")) assertTrue(RULES.isBuilding(step.what()), step.what());
        }
    }
}
