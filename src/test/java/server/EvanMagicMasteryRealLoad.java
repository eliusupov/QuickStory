package server;

import constants.skills.Evan;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static server.V84Wz.wz;

/** Magic Mastery's WZ values: {@code x} is server MATK; {@code mastery} is the client damage floor. */
class EvanMagicMasteryRealLoad {

    private static Data level(DataProvider skills, int skillLevel) {
        Data node = skills.getData("2217.img").getChildByPath("skill/" + Evan.MAGIC_MASTERY + "/level/" + skillLevel);
        assertNotNull(node, "Skill.wz/2217.img/skill/" + Evan.MAGIC_MASTERY + "/level/" + skillLevel);
        return node;
    }

    @Test
    void v84WzSeparatesServerMatkFromClientDamageFloor() {
        DataProvider skills = wz("Skill.wz");

        assertAll(
                () -> assertEquals(0, DataTool.getInt("x", level(skills, 1))),
                () -> assertEquals(15, DataTool.getInt("x", level(skills, 30))),
                () -> assertEquals(11, DataTool.getInt("mastery", level(skills, 1))),
                () -> assertEquals(16, DataTool.getInt("mastery", level(skills, 30)))
        );
    }
}
