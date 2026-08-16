package client.creator;

import client.Character;
import provider.Data;
import provider.DataProviderFactory;
import provider.wz.WZFiles;

public class MakeCharInfoValidator {
    private static final MakeCharInfo charFemale;
    private static final MakeCharInfo charMale;
    private static final MakeCharInfo orientCharFemale;
    private static final MakeCharInfo orientCharMale;
    private static final MakeCharInfo premiumCharFemale;
    private static final MakeCharInfo premiumCharMale;
    private static final MakeCharInfo evanCharFemale;
    private static final MakeCharInfo evanCharMale;

    static {
        Data data = DataProviderFactory.getDataProvider(WZFiles.ETC).getData("MakeCharInfo.img");
        charFemale = new MakeCharInfo(data.getChildByPath("Info/CharFemale"));
        charMale = new MakeCharInfo(data.getChildByPath("Info/CharMale"));
        orientCharFemale = new MakeCharInfo(data.getChildByPath("OrientCharFemale"));
        orientCharMale = new MakeCharInfo(data.getChildByPath("OrientCharMale"));
        premiumCharFemale = new MakeCharInfo(data.getChildByPath("PremiumCharFemale"));
        premiumCharMale = new MakeCharInfo(data.getChildByPath("PremiumCharMale"));
        // Evan sits at the top level of MakeCharInfo.img alongside OrientChar* (Aran) and
        // PremiumChar* (Cygnus), not under Info/ like the explorers.
        evanCharFemale = new MakeCharInfo(data.getChildByPath("EvanCharFemale"));
        evanCharMale = new MakeCharInfo(data.getChildByPath("EvanCharMale"));
    }

    private static MakeCharInfo getMakeCharInfo(Character character) {
        return switch (character.getJob()) {
            case BEGINNER, WARRIOR, MAGICIAN, BOWMAN, THIEF, PIRATE -> character.isMale() ? charMale : charFemale;
            case NOBLESSE -> character.isMale() ? premiumCharMale : premiumCharFemale;
            case LEGEND -> character.isMale() ? orientCharMale : orientCharFemale;
            // EvanCreator builds the character as Job.EVAN (2001), the beginner job, the same way
            // LegendCreator uses LEGEND (2000). Without this case the switch fell to default -> null,
            // and isNewCharacterValid reads null as "packet edit" - so EVERY Evan was rejected as a
            // cheat regardless of what the player picked, while explorers created fine.
            case EVAN -> character.isMale() ? evanCharMale : evanCharFemale;
            default -> null;
        };
    }

    public static boolean isNewCharacterValid(Character character) {
        MakeCharInfo makeCharInfo = getMakeCharInfo(character);
        if (makeCharInfo == null) return false;

        return makeCharInfo.verifyCharacter(character);
    }
}
