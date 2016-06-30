package com.massimodz8.collaborativegrouporder;

import android.content.Context;

import com.massimodz8.collaborativegrouporder.protocol.nano.LevelAdvancement;
import com.massimodz8.collaborativegrouporder.protocol.nano.MonsterData;
import com.massimodz8.collaborativegrouporder.protocol.nano.RPGClass;

import java.util.HashMap;

/**
 * Created by Massimo on 14/05/2016.
 * Because mapping protobuf enums to strings needs singletons for good!
 */
public abstract class ProtobufSupport {
    public static String monsterRaceToString(int protbufEnum, Context ctx) {
        if(monRace == null) {
            monRace = new HashMap<>();
            monRace.put(MonsterData.INVALID_RACE, ctx.getString(R.string.mobs_race_invalid));
            monRace.put(MonsterData.DWARF, ctx.getString(R.string.mobs_race_dwarf));
            monRace.put(MonsterData.ELF, ctx.getString(R.string.mobs_race_elf));
            monRace.put(MonsterData.GNOME, ctx.getString(R.string.mobs_race_gnome));
            monRace.put(MonsterData.HALF_ORC, ctx.getString(R.string.mobs_race_halfOrc));
            monRace.put(MonsterData.HALFLING, ctx.getString(R.string.mobs_race_halfling));
            monRace.put(MonsterData.HUMAN, ctx.getString(R.string.mobs_race_human));
            monRace.put(MonsterData.AASIMAR, ctx.getString(R.string.mobs_race_aasimar));
            monRace.put(MonsterData.CATFOLK, ctx.getString(R.string.mobs_race_catfolk));
            monRace.put(MonsterData.DHAMPIR, ctx.getString(R.string.mobs_race_dhampir));
            monRace.put(MonsterData.DROW, ctx.getString(R.string.mobs_race_drow));
            monRace.put(MonsterData.FETCHLING, ctx.getString(R.string.mobs_race_fetchling));
            monRace.put(MonsterData.GOBLIN, ctx.getString(R.string.mobs_race_goblin));
            monRace.put(MonsterData.HOBGOBLIN, ctx.getString(R.string.mobs_race_hobgoblin));
            monRace.put(MonsterData.IFRIT, ctx.getString(R.string.mobs_race_ifrit));
            monRace.put(MonsterData.KOBOLD, ctx.getString(R.string.mobs_race_kobold));
            monRace.put(MonsterData.ORC, ctx.getString(R.string.mobs_race_orc));
            monRace.put(MonsterData.OREAD, ctx.getString(R.string.mobs_race_oread));
            monRace.put(MonsterData.RATFOLK, ctx.getString(R.string.mobs_race_ratfolk));
            monRace.put(MonsterData.SYLPH, ctx.getString(R.string.mobs_race_sylph));
            monRace.put(MonsterData.TENGU, ctx.getString(R.string.mobs_race_tengu));
            monRace.put(MonsterData.TIEFLING, ctx.getString(R.string.mobs_race_tiefling));
            monRace.put(MonsterData.UNDINE, ctx.getString(R.string.mobs_race_undine));
            monRace.put(MonsterData.GNOLL, ctx.getString(R.string.mobs_race_gnoll));
            monRace.put(MonsterData.LIZARDFOLK, ctx.getString(R.string.mobs_race_lizardfolk));
            monRace.put(MonsterData.MONKEY_GOBLIN, ctx.getString(R.string.mobs_race_monkeyGoblin));
            monRace.put(MonsterData.SKINWALKER, ctx.getString(R.string.mobs_race_skinWalker));
            monRace.put(MonsterData.TRIAXIAN, ctx.getString(R.string.mobs_race_triaxian));
            monRace.put(MonsterData.ANDROID, ctx.getString(R.string.mobs_race_android));
            monRace.put(MonsterData.GATHLAIN, ctx.getString(R.string.mobs_race_gathlain));
            monRace.put(MonsterData.GHORAN, ctx.getString(R.string.mobs_race_ghoran));
            monRace.put(MonsterData.KASATHA, ctx.getString(R.string.mobs_race_kasatha));
            monRace.put(MonsterData.LASHUNTA, ctx.getString(R.string.mobs_race_lashunta));
            monRace.put(MonsterData.SHABTI, ctx.getString(R.string.mobs_race_shabti));
            monRace.put(MonsterData.SYRINX, ctx.getString(R.string.mobs_race_syrinx));
            monRace.put(MonsterData.WYRWOOD, ctx.getString(R.string.mobs_race_wyrwood));
            monRace.put(MonsterData.WYVARAN, ctx.getString(R.string.mobs_race_wyvaran));
            monRace.put(MonsterData.CENTAUR, ctx.getString(R.string.mobs_race_centaur));
            monRace.put(MonsterData.OGRE, ctx.getString(R.string.mobs_race_ogre));
            monRace.put(MonsterData.SHOBHAD, ctx.getString(R.string.mobs_race_shobhad));
            monRace.put(MonsterData.TROX, ctx.getString(R.string.mobs_race_trox));
            monRace.put(MonsterData.DRIDER, ctx.getString(R.string.mobs_race_drider));
            monRace.put(MonsterData.GARGOYLE, ctx.getString(R.string.mobs_race_gargoyle));
            monRace.put(MonsterData.CHANGELING, ctx.getString(R.string.mobs_race_changeling));
            monRace.put(MonsterData.DUERGAR, ctx.getString(R.string.mobs_race_duergar));
            monRace.put(MonsterData.GILLMEN, ctx.getString(R.string.mobs_race_gillmen));
            monRace.put(MonsterData.GRIPPLI, ctx.getString(R.string.mobs_race_grippli));
            monRace.put(MonsterData.KITSUNE, ctx.getString(R.string.mobs_race_kitsune));
            monRace.put(MonsterData.MERFOLK, ctx.getString(R.string.mobs_race_merfolk));
            monRace.put(MonsterData.NAGAJI, ctx.getString(R.string.mobs_race_nagaji));
            monRace.put(MonsterData.SAMSARAN, ctx.getString(R.string.mobs_race_samsaran));
            monRace.put(MonsterData.STRIX, ctx.getString(R.string.mobs_race_strix));
            monRace.put(MonsterData.SULI, ctx.getString(R.string.mobs_race_suli));
            monRace.put(MonsterData.SVIRFNEBLIN, ctx.getString(R.string.mobs_race_svirfneblin));
            monRace.put(MonsterData.VANARA, ctx.getString(R.string.mobs_race_vanara));
            monRace.put(MonsterData.VISHKANYA, ctx.getString(R.string.mobs_race_vishkanya));
            monRace.put(MonsterData.WAYANG, ctx.getString(R.string.mobs_race_wayang));
            monRace.put(MonsterData.AQUATIC_ELF, ctx.getString(R.string.mobs_race_aquaticElf));
            monRace.put(MonsterData.ASTMOI, ctx.getString(R.string.mobs_race_astmoi));
            monRace.put(MonsterData.CALIGNI, ctx.getString(R.string.mobs_race_caligni));
            monRace.put(MonsterData.DEEP_ONE_HYBRID, ctx.getString(R.string.mobs_race_deepOneHybrid));
            monRace.put(MonsterData.GANZI, ctx.getString(R.string.mobs_race_ganzi));
            monRace.put(MonsterData.KURU, ctx.getString(R.string.mobs_race_kuru));
            monRace.put(MonsterData.MANAVRI, ctx.getString(R.string.mobs_race_manavri));
            monRace.put(MonsterData.ORANG__PENDAK, ctx.getString(R.string.mobs_race_orangPendak));
            monRace.put(MonsterData.REPTOID, ctx.getString(R.string.mobs_race_reptoid));
        }
        final String match = monRace.get(protbufEnum);
        return match == null? monRace.get(MonsterData.INVALID_RACE) : match;
    }
    
    public static String monsterSizeToString(int protbufEnum, Context ctx) {
        if(monSize == null) {
            monSize = new HashMap<>();
            monSize.put(MonsterData.INVALID_MONSTER_SIZE, ctx.getString(R.string.mobs_size_invalid));
            monSize.put(MonsterData.FINE, ctx.getString(R.string.mobs_size_fine));
            monSize.put(MonsterData.DIMINUTIVE, ctx.getString(R.string.mobs_size_diminutive));
            monSize.put(MonsterData.TINY, ctx.getString(R.string.mobs_size_tiny));
            monSize.put(MonsterData.SMALL, ctx.getString(R.string.mobs_size_small));
            monSize.put(MonsterData.MEDIUM, ctx.getString(R.string.mobs_size_medium));
            monSize.put(MonsterData.LARGE, ctx.getString(R.string.mobs_size_large));
            monSize.put(MonsterData.HUGE, ctx.getString(R.string.mobs_size_huge));
            monSize.put(MonsterData.GARGANTUAN, ctx.getString(R.string.mobs_size_gargantuan));
            monSize.put(MonsterData.COLOSSAL, ctx.getString(R.string.mobs_size_colossal));
            
        }
        final String match = monSize.get(protbufEnum);
        return match == null? monSize.get(MonsterData.INVALID_MONSTER_SIZE) : match;
    }

    public static String monsterTypeToString(int protobufEnum, boolean trueIfTypeOtherwiseTags, Context ctx) {
        if(monType == null) {
            monType = new HashMap<>();
            monType.put(MonsterData.ABERRATION, ctx.getString(R.string.mobs_type_aberration));
            monType.put(MonsterData.ANIMAL, ctx.getString(R.string.mobs_type_animal));
            monType.put(MonsterData.CONSTRUCT, ctx.getString(R.string.mobs_type_construct));
            monType.put(MonsterData.DRAGON, ctx.getString(R.string.mobs_type_dragon));
            monType.put(MonsterData.FEY, ctx.getString(R.string.mobs_type_fey));
            monType.put(MonsterData.HUMANOID, ctx.getString(R.string.mobs_type_humanoid));
            monType.put(MonsterData.MAGICAL_BEAST, ctx.getString(R.string.mobs_type_magicalBeast));
            monType.put(MonsterData.MONSTROUS_HUMANOID, ctx.getString(R.string.mobs_type_monstrousHumanoid));
            monType.put(MonsterData.OOZE, ctx.getString(R.string.mobs_type_ooze));
            monType.put(MonsterData.OUTSIDER, ctx.getString(R.string.mobs_type_outsider));
            monType.put(MonsterData.PLANT, ctx.getString(R.string.mobs_type_plant));
            monType.put(MonsterData.UNDEAD, ctx.getString(R.string.mobs_type_undead));
            monType.put(MonsterData.VERMIN, ctx.getString(R.string.mobs_type_vermin));
            monType.put(MonsterData.SUB_ACID, ctx.getString(R.string.mobs_subType_acid));
            monType.put(MonsterData.SUB_ADLET, ctx.getString(R.string.mobs_subType_adlet));
            monType.put(MonsterData.SUB_AEON, ctx.getString(R.string.mobs_subType_aeon));
            monType.put(MonsterData.SUB_AGATHION, ctx.getString(R.string.mobs_subType_agathion));
            monType.put(MonsterData.SUB_AIR, ctx.getString(R.string.mobs_subType_air));
            monType.put(MonsterData.SUB_AMPHIBIOUS, ctx.getString(R.string.mobs_subType_amphibious));
            monType.put(MonsterData.SUB_ANGEL, ctx.getString(R.string.mobs_subType_angel));
            monType.put(MonsterData.SUB_AQUATIC, ctx.getString(R.string.mobs_subType_aquatic));
            monType.put(MonsterData.SUB_ARCHON, ctx.getString(R.string.mobs_subType_archon));
            monType.put(MonsterData.SUB_ASURA, ctx.getString(R.string.mobs_subType_asura));
            monType.put(MonsterData.SUB_AZATA, ctx.getString(R.string.mobs_subType_azata));
            monType.put(MonsterData.SUB_BEHEMOTH, ctx.getString(R.string.mobs_subType_behemoth));
            monType.put(MonsterData.SUB_BOGGARD, ctx.getString(R.string.mobs_subType_boggard));
            monType.put(MonsterData.SUB_CATFOLK, ctx.getString(R.string.mobs_subType_catfolk));
            monType.put(MonsterData.SUB_CHAOTIC, ctx.getString(R.string.mobs_subType_chaotic));
            monType.put(MonsterData.SUB_CHARAU__KA, ctx.getString(R.string.mobs_subType_charauKa));
            monType.put(MonsterData.SUB_CLOCKWORK, ctx.getString(R.string.mobs_subType_clockwork));
            monType.put(MonsterData.SUB_COLD, ctx.getString(R.string.mobs_subType_cold));
            monType.put(MonsterData.SUB_COLOSSUS, ctx.getString(R.string.mobs_subType_colossus));
            monType.put(MonsterData.SUB_DAEMON, ctx.getString(R.string.mobs_subType_daemon));
            monType.put(MonsterData.SUB_DARK_FOLK, ctx.getString(R.string.mobs_subType_darkFolk));
            monType.put(MonsterData.SUB_DEEP_ONE, ctx.getString(R.string.mobs_subType_deepOne));
            monType.put(MonsterData.SUB_DEMODAND, ctx.getString(R.string.mobs_subType_demodand));
            monType.put(MonsterData.SUB_DEMON, ctx.getString(R.string.mobs_subType_demon));
            monType.put(MonsterData.SUB_DERRO, ctx.getString(R.string.mobs_subType_derro));
            monType.put(MonsterData.SUB_DEVIL, ctx.getString(R.string.mobs_subType_devil));
            monType.put(MonsterData.SUB_DIV, ctx.getString(R.string.mobs_subType_div));
            monType.put(MonsterData.SUB_DWARF, ctx.getString(R.string.mobs_subType_dwarf));
            monType.put(MonsterData.SUB_EARTH, ctx.getString(R.string.mobs_subType_earth));
            monType.put(MonsterData.SUB_ELECTRICITY, ctx.getString(R.string.mobs_subType_electricity));
            monType.put(MonsterData.SUB_ELEMENTAL, ctx.getString(R.string.mobs_subType_elemental));
            monType.put(MonsterData.SUB_ELF, ctx.getString(R.string.mobs_subType_elf));
            monType.put(MonsterData.SUB_EVIL, ctx.getString(R.string.mobs_subType_evil));
            monType.put(MonsterData.SUB_EXTRAPLANAR, ctx.getString(R.string.mobs_subType_extraplanar));
            monType.put(MonsterData.SUB_FEYBLOOD, ctx.getString(R.string.mobs_subType_feyblood));
            monType.put(MonsterData.SUB_FIRE, ctx.getString(R.string.mobs_subType_fire));
            monType.put(MonsterData.SUB_GIANT, ctx.getString(R.string.mobs_subType_giant));
            monType.put(MonsterData.SUB_GNOLL, ctx.getString(R.string.mobs_subType_gnoll));
            monType.put(MonsterData.SUB_GNOME, ctx.getString(R.string.mobs_subType_gnome));
            monType.put(MonsterData.SUB_GOBLIN, ctx.getString(R.string.mobs_subType_goblin));
            monType.put(MonsterData.SUB_GOBLINOID, ctx.getString(R.string.mobs_subType_goblinoid));
            monType.put(MonsterData.SUB_GODSPAWN, ctx.getString(R.string.mobs_subType_godspawn));
            monType.put(MonsterData.SUB_GOOD, ctx.getString(R.string.mobs_subType_good));
            monType.put(MonsterData.SUB_GRAVITY, ctx.getString(R.string.mobs_subType_gravity));
            monType.put(MonsterData.SUB_GREAT_OLD_ONE, ctx.getString(R.string.mobs_subType_greatOldOne));
            monType.put(MonsterData.SUB_HALFLING, ctx.getString(R.string.mobs_subType_halfling));
            monType.put(MonsterData.SUB_HERALD, ctx.getString(R.string.mobs_subType_herald));
            monType.put(MonsterData.SUB_HORDE, ctx.getString(R.string.mobs_subType_horde));
            monType.put(MonsterData.SUB_HUMAN, ctx.getString(R.string.mobs_subType_human));
            monType.put(MonsterData.SUB_HUMANOID, ctx.getString(R.string.mobs_subType_humanoid));
            monType.put(MonsterData.SUB_INCORPOREAL, ctx.getString(R.string.mobs_subType_incorporeal));
            monType.put(MonsterData.SUB_INEVITABLE, ctx.getString(R.string.mobs_subType_inevitable));
            monType.put(MonsterData.SUB_KAIJU, ctx.getString(R.string.mobs_subType_kaiju));
            monType.put(MonsterData.SUB_KAMI, ctx.getString(R.string.mobs_subType_kami));
            monType.put(MonsterData.SUB_KASATHA, ctx.getString(R.string.mobs_subType_kasatha));
            monType.put(MonsterData.SUB_KITSUNE, ctx.getString(R.string.mobs_subType_kitsune));
            monType.put(MonsterData.SUB_KUAH__LIJ, ctx.getString(R.string.mobs_subType_kuahLij));
            monType.put(MonsterData.SUB_KYTON, ctx.getString(R.string.mobs_subType_kyton));
            monType.put(MonsterData.SUB_LAWFUL, ctx.getString(R.string.mobs_subType_lawful));
            monType.put(MonsterData.SUB_LESHY, ctx.getString(R.string.mobs_subType_leshy));
            monType.put(MonsterData.SUB_MYTHIC, ctx.getString(R.string.mobs_subType_mythic));
            monType.put(MonsterData.SUB_NATIVE, ctx.getString(R.string.mobs_subType_native));
            monType.put(MonsterData.SUB_NIGHTSHADE, ctx.getString(R.string.mobs_subType_nightshade));
            monType.put(MonsterData.SUB_OGREN, ctx.getString(R.string.mobs_subType_ogren));
            monType.put(MonsterData.SUB_OGRILLON, ctx.getString(R.string.mobs_subType_ogrillon));
            monType.put(MonsterData.SUB_ONI, ctx.getString(R.string.mobs_subType_oni));
            monType.put(MonsterData.SUB_ORC, ctx.getString(R.string.mobs_subType_orc));
            monType.put(MonsterData.SUB_PROTEAN, ctx.getString(R.string.mobs_subType_protean));
            monType.put(MonsterData.SUB_PSYCHOPOMP, ctx.getString(R.string.mobs_subType_psychopomp));
            monType.put(MonsterData.SUB_QLIPPOTH, ctx.getString(R.string.mobs_subType_qlippoth));
            monType.put(MonsterData.SUB_RAKSHASA, ctx.getString(R.string.mobs_subType_rakshasa));
            monType.put(MonsterData.SUB_RATFOLK, ctx.getString(R.string.mobs_subType_ratfolk));
            monType.put(MonsterData.SUB_REPTILIAN, ctx.getString(R.string.mobs_subType_reptilian));
            monType.put(MonsterData.SUB_ROBOT, ctx.getString(R.string.mobs_subType_robot));
            monType.put(MonsterData.SUB_SAMSARAN, ctx.getString(R.string.mobs_subType_samsaran));
            monType.put(MonsterData.SUB_SASQUATCH, ctx.getString(R.string.mobs_subType_sasquatch));
            monType.put(MonsterData.SUB_SHAPECHANGER, ctx.getString(R.string.mobs_subType_shapechanger));
            monType.put(MonsterData.SUB_SKULK, ctx.getString(R.string.mobs_subType_skulk));
            monType.put(MonsterData.SUB_STORMWARDEN, ctx.getString(R.string.mobs_subType_stormwarden));
            monType.put(MonsterData.SUB_SWARM, ctx.getString(R.string.mobs_subType_swarm));
            monType.put(MonsterData.SUB_TABAXI, ctx.getString(R.string.mobs_subType_tabaxi));
            monType.put(MonsterData.SUB_TENGU, ctx.getString(R.string.mobs_subType_tengu));
            monType.put(MonsterData.SUB_TIME, ctx.getString(R.string.mobs_subType_time));
            monType.put(MonsterData.SUB_TROOP, ctx.getString(R.string.mobs_subType_troop));
            monType.put(MonsterData.SUB_UDAEUS, ctx.getString(R.string.mobs_subType_udaeus));
            monType.put(MonsterData.SUB_UNBREATHING, ctx.getString(R.string.mobs_subType_unbreathing));
            monType.put(MonsterData.SUB_VANARA, ctx.getString(R.string.mobs_subType_vanara));
            monType.put(MonsterData.SUB_VAPOR, ctx.getString(R.string.mobs_subType_vapor));
            monType.put(MonsterData.SUB_VISHKANYA, ctx.getString(R.string.mobs_subType_vishkanya));
            monType.put(MonsterData.SUB_WATER, ctx.getString(R.string.mobs_subType_water));
            monType.put(MonsterData.SUB_WAYANG, ctx.getString(R.string.mobs_subType_wayang));
            monType.put(MonsterData.SUB_FUNGUS, ctx.getString(R.string.mobs_subType_fungus));
            monType.put(MonsterData.SUB_PSIONIC, ctx.getString(R.string.mobs_subType_psionic));
        }
        final String match = monType.get(protobufEnum);
        if(match != null) return match;
        return ctx.getString(trueIfTypeOtherwiseTags? R.string.mobs_type_invalidType : R.string.mobs_type_invalidTag);
    }

    public static String monsterAlignmentToString(int protobufEnum, boolean compact, Context ctx) {
        if(align == null) {
            align = new HashMap<>();
            align.put(MonsterData.LEGAL_GOOD, ctx.getString(R.string.mobs_align_lg));
            align.put(MonsterData.LEGAL_NEUTRAL, ctx.getString(R.string.mobs_align_ln));
            align.put(MonsterData.LEGAL_EVIL, ctx.getString(R.string.mobs_align_le));
            align.put(MonsterData.NEUTRAL_GOOD, ctx.getString(R.string.mobs_align_ng));
            align.put(MonsterData.JUST_NEUTRAL, ctx.getString(R.string.mobs_align_nn));
            align.put(MonsterData.NEUTRAL_EVIL, ctx.getString(R.string.mobs_align_ne));
            align.put(MonsterData.CHAOTIC_GOOD, ctx.getString(R.string.mobs_align_cg));
            align.put(MonsterData.CHAOTIC_NEUTRAL, ctx.getString(R.string.mobs_align_cn));
            align.put(MonsterData.CHAOTIC_EVIL, ctx.getString(R.string.mobs_align_ce));
            align.put(MonsterData.ALIGNMENT_RESTRICTED, ctx.getString(R.string.mobs_align_restricted));
            align.put(MonsterData.ALIGNMENT_AS_CREATOR, ctx.getString(R.string.mobs_align_asCreator));
        }
        String match = align.get(protobufEnum);
        if(match == null) match = align.get(MonsterData.INVALID_ALIGNMENT);
        if(compact) {
            switch(protobufEnum) {
                case MonsterData.LEGAL_GOOD:
                case MonsterData.LEGAL_NEUTRAL:
                case MonsterData.LEGAL_EVIL:
                case MonsterData.NEUTRAL_GOOD:
                case MonsterData.JUST_NEUTRAL:
                case MonsterData.NEUTRAL_EVIL:
                case MonsterData.CHAOTIC_GOOD:
                case MonsterData.CHAOTIC_NEUTRAL:
                case MonsterData.CHAOTIC_EVIL:
                    String[] words = match.split(" ");
                    match = "";
                    for(String part : words) match += part.toUpperCase().charAt(0);
            }
        }
        return match;
    }

    public static String knownClassToString(int protobufEnum, Context ctx) {
        if(null == knownClass) {
            knownClass = new HashMap<>();
            knownClass.put(RPGClass.KC_INVALID, ctx.getString(R.string.knownClass_invalid));
            knownClass.put(RPGClass.KC_PF_CORE_BARBARIAN, ctx.getString(R.string.knownClass_barbarian));
            knownClass.put(RPGClass.KC_PF_CORE_BARD, ctx.getString(R.string.knownClass_bard));
            knownClass.put(RPGClass.KC_PF_CORE_CLERIC, ctx.getString(R.string.knownClass_cleric));
            knownClass.put(RPGClass.KC_PF_CORE_DRUID, ctx.getString(R.string.knownClass_druid));
            knownClass.put(RPGClass.KC_PF_CORE_FIGHTER, ctx.getString(R.string.knownClass_fighter));
            knownClass.put(RPGClass.KC_PF_CORE_MONK, ctx.getString(R.string.knownClass_monk));
            knownClass.put(RPGClass.KC_PF_CORE_PALADIN, ctx.getString(R.string.knownClass_paladin));
            knownClass.put(RPGClass.KC_PF_CORE_RANGER, ctx.getString(R.string.knownClass_ranger));
            knownClass.put(RPGClass.KC_PF_CORE_ROGUE, ctx.getString(R.string.knownClass_rogue));
            knownClass.put(RPGClass.KC_PF_CORE_SORCERER, ctx.getString(R.string.knownClass_sorcerer));
            knownClass.put(RPGClass.KC_PF_CORE_WIZARD, ctx.getString(R.string.knownClass_wizard));
            knownClass.put(RPGClass.KC_BASE_ALCHEMIST, ctx.getString(R.string.knownClass_alchemist));
            knownClass.put(RPGClass.KC_BASE_CAVALIER, ctx.getString(R.string.knownClass_cavalier));
            knownClass.put(RPGClass.KC_BASE_GUNSLINGER, ctx.getString(R.string.knownClass_gunslinger));
            knownClass.put(RPGClass.KC_BASE_INQUISITOR, ctx.getString(R.string.knownClass_inquisitor));
            knownClass.put(RPGClass.KC_BASE_MAGUS, ctx.getString(R.string.knownClass_magus));
            knownClass.put(RPGClass.KC_BASE_ORACLE, ctx.getString(R.string.knownClass_oracle));
            knownClass.put(RPGClass.KC_BASE_SUMMONER, ctx.getString(R.string.knownClass_summoner));
            knownClass.put(RPGClass.KC_BASE_VIGILANTE, ctx.getString(R.string.knownClass_vigilante));
            knownClass.put(RPGClass.KC_BASE_WITCH, ctx.getString(R.string.knownClass_witch));
            knownClass.put(RPGClass.KC_ALT_ANTIPALADIN, ctx.getString(R.string.knownClass_antipaladin));
            knownClass.put(RPGClass.KC_ALT_NINJA, ctx.getString(R.string.knownClass_ninja));
            knownClass.put(RPGClass.KC_ALT_SAMURAI, ctx.getString(R.string.knownClass_samurai));
            knownClass.put(RPGClass.KC_HYB_ARCHANIST, ctx.getString(R.string.knownClass_archanist));
            knownClass.put(RPGClass.KC_HYB_BLOODRAGER, ctx.getString(R.string.knownClass_bloodrager));
            knownClass.put(RPGClass.KC_HYB_BRAWLER, ctx.getString(R.string.knownClass_brawler));
            knownClass.put(RPGClass.KC_HYB_HUNTER, ctx.getString(R.string.knownClass_hunter));
            knownClass.put(RPGClass.KC_HYB_INVESTIGATOR, ctx.getString(R.string.knownClass_investigator));
            knownClass.put(RPGClass.KC_HYB_SHAMAN, ctx.getString(R.string.knownClass_shaman));
            knownClass.put(RPGClass.KC_HYB_SKALD, ctx.getString(R.string.knownClass_skald));
            knownClass.put(RPGClass.KC_HYB_SLAYER, ctx.getString(R.string.knownClass_slayer));
            knownClass.put(RPGClass.KG_HYB_SWASHBUCKLER, ctx.getString(R.string.knownClass_swashbuckler));
            knownClass.put(RPGClass.KC_HYB_WARPRIEST, ctx.getString(R.string.knownClass_warpriest));
            knownClass.put(RPGClass.KC_UNC_BARBARIAN, ctx.getString(R.string.knownClass_unchainedBarbarian));
            knownClass.put(RPGClass.KC_UNC_MONK, ctx.getString(R.string.knownClass_unchainedMonk));
            knownClass.put(RPGClass.KC_UNC_ROGUE, ctx.getString(R.string.knownClass_unchainedRogue));
            knownClass.put(RPGClass.KC_UNC_SUMMONER, ctx.getString(R.string.knownClass_unchainedSummoner));
            knownClass.put(RPGClass.KC_OCC_KINETICIST, ctx.getString(R.string.knownClass_kineticist));
            knownClass.put(RPGClass.KC_OCC_MEDIUM, ctx.getString(R.string.knownClass_medium));
            knownClass.put(RPGClass.KC_OCC_MESMERIST, ctx.getString(R.string.knownClass_mesmerist));
            knownClass.put(RPGClass.KC_OCC_OCCULTIST, ctx.getString(R.string.knownClass_occultist));
            knownClass.put(RPGClass.KC_OCC_PSYCHIC, ctx.getString(R.string.knownClass_psychic));
            knownClass.put(RPGClass.KC_OCC_SPIRITUALIST, ctx.getString(R.string.knownClass_spiritualist));
            knownClass.put(RPGClass.KC_PRES_ADV_BATTLE_HERALD, ctx.getString(R.string.knownClass_battleHerald));
            knownClass.put(RPGClass.KC_PRES_ADV_HOLY_VINDICATOR, ctx.getString(R.string.knownClass_holyVindicator));
            knownClass.put(RPGClass.KC_PRES_ADV_HORIZON_WALKER, ctx.getString(R.string.knownClass_horizonWalker));
            knownClass.put(RPGClass.KC_PRES_ADV_CHYMIST, ctx.getString(R.string.knownClass_chymist));
            knownClass.put(RPGClass.KC_PRES_ADV_SPY, ctx.getString(R.string.knownClass_spy));
            knownClass.put(RPGClass.KC_PRES_ADV_WARDEN, ctx.getString(R.string.knownClass_warden));
            knownClass.put(RPGClass.KC_PRES_ADV_RAGE_PROPHET, ctx.getString(R.string.knownClass_rageProphet));
            knownClass.put(RPGClass.KC_PRES_ADV_STALWART_DEFENDER, ctx.getString(R.string.knownClass_stalwartDefender));
            knownClass.put(RPGClass.KC_PRES_CORE_ARCHER, ctx.getString(R.string.knownClass_archer));
            knownClass.put(RPGClass.KC_PRES_CORE_TRICKSTER, ctx.getString(R.string.knownClass_trickster));
            knownClass.put(RPGClass.KC_PRES_CORE_ASSASSIN, ctx.getString(R.string.knownClass_assassin));
            knownClass.put(RPGClass.KC_PRES_CORE_DRAGON_DISCIPLE, ctx.getString(R.string.knownClass_dragonDisciple));
            knownClass.put(RPGClass.KC_PRES_CORE_DUELIST, ctx.getString(R.string.knownClass_duelist));
            knownClass.put(RPGClass.KC_PRES_CORE_ELDRITCH_KNIGHT, ctx.getString(R.string.knownClass_eldritchKnight));
            knownClass.put(RPGClass.KC_PRES_CORE_LOREMASTER, ctx.getString(R.string.knownClass_loremaster));
            knownClass.put(RPGClass.KC_PRES_CORE_MYSTIC_THEURGE, ctx.getString(R.string.knownClass_mysticTheurge));
            knownClass.put(RPGClass.KC_PRES_CORE_PF_CHRONICLER, ctx.getString(R.string.knownClass_chronicler));
            knownClass.put(RPGClass.KC_PRES_CORE_SHADOWDANCER, ctx.getString(R.string.knownClass_shadowdancer));
            knownClass.put(RPGClass.KC_PRES_OPZ_AGENT_OF_THE_GRAVE, ctx.getString(R.string.knownClass_agentOfTheGrave));
            knownClass.put(RPGClass.KC_PRES_OPZ_ARCANE_SAVANT, ctx.getString(R.string.knownClass_arcaneSavant));
            knownClass.put(RPGClass.KC_PRES_OPZ_BLOODMAGE, ctx.getString(R.string.knownClass_bloodMage));
            knownClass.put(RPGClass.KC_PRES_OPZ_BRIGHTNESS_SEEKER, ctx.getString(R.string.knownClass_brightnessSeeker));
            knownClass.put(RPGClass.KC_PRES_OPZ_BROTHER_OF_THE_SEAL, ctx.getString(R.string.knownClass_brotherOfTheSeal));
            knownClass.put(RPGClass.KC_PRES_OPZ_CELESTIAL_KNIGHT, ctx.getString(R.string.knownClass_celestialKnight));
            knownClass.put(RPGClass.KC_PRES_OPZ_CHAMPION_OF_THE_ENLIGHTENED, ctx.getString(R.string.knownClass_championOfTheEnlighted));
            knownClass.put(RPGClass.KC_PRES_OPZ_CHEVALIER, ctx.getString(R.string.knownClass_chevalier));
            knownClass.put(RPGClass.KC_PRES_OPZ_COASTAL_PRIRATE, ctx.getString(R.string.knownClass_coastalPirate));
            knownClass.put(RPGClass.KC_PRES_OPZ_COLLEGIATE_ARCANIST, ctx.getString(R.string.knownClass_collegiateArcanist));
            knownClass.put(RPGClass.KC_PRES_OPZ_CRIMSON_ASSASSIN, ctx.getString(R.string.knownClass_crimsonAssassin));
            knownClass.put(RPGClass.KC_PRES_OPZ_CYPHERMAGE, ctx.getString(R.string.knownClass_cyphermage));
            knownClass.put(RPGClass.KC_PRES_OPZ_DAIVRAT, ctx.getString(R.string.knownClass_daivrat));
            knownClass.put(RPGClass.KC_PRES_OPZ_DARK_DELVER, ctx.getString(R.string.knownClass_darkDelver));
            knownClass.put(RPGClass.KC_PRES_OPZ_DARKFIRE_ADEPT, ctx.getString(R.string.knownClass_darkfireAdept));
            knownClass.put(RPGClass.KC_PRES_OPZ_DEEP_SEA_PIRATE, ctx.getString(R.string.knownClass_deepSeaPirate));
            knownClass.put(RPGClass.KC_PRES_OPZ_DEMONIAC, ctx.getString(R.string.knownClass_demoniac));
            knownClass.put(RPGClass.KC_PRES_OPZ_DIABOLIST, ctx.getString(R.string.knownClass_diabolist));
            knownClass.put(RPGClass.KC_PRES_OPZ_DISSIDENT_OF_DAWN, ctx.getString(R.string.knownClass_dissidentOfDawn));
            knownClass.put(RPGClass.KC_PRES_OPZ_DIVINE_ASSESSOR, ctx.getString(R.string.knownClass_divineAssessor));
            knownClass.put(RPGClass.KC_PRES_OPZ_DIVINE_SCION, ctx.getString(R.string.knownClass_divineScion));
            knownClass.put(RPGClass.KC_PRES_OPZ_ENVOY_OF_BALANCE, ctx.getString(R.string.knownClass_envoyOfBalance));
            knownClass.put(RPGClass.KC_PRES_OPZ_EVANGELIST, ctx.getString(R.string.knownClass_evangelist));
            knownClass.put(RPGClass.KC_PRES_OPZ_EXALTED, ctx.getString(R.string.knownClass_exalted));
            knownClass.put(RPGClass.KC_PRES_OPZ_FALSE_PRIEST, ctx.getString(R.string.knownClass_falsePriest));
            knownClass.put(RPGClass.KC_PRES_OPZ_FIELD_AGENT, ctx.getString(R.string.knownClass_fieldAgent));
            knownClass.put(RPGClass.KC_PRES_OPZ_FURIOUS_GUARDIAN, ctx.getString(R.string.knownClass_furiousGuardian));
            knownClass.put(RPGClass.KC_PRES_OPZ_GENIE_BINDER, ctx.getString(R.string.knownClass_genieBinder));
            knownClass.put(RPGClass.KC_PRES_OPZ_GOLDEN_LEGIONNAIRE, ctx.getString(R.string.knownClass_goldenLegionnaire));
            knownClass.put(RPGClass.KC_PRES_OPZ_GRAND_MARSHAL, ctx.getString(R.string.knownClass_grandMarshal));
            knownClass.put(RPGClass.KC_PRES_OPZ_GRAY_WARDEN, ctx.getString(R.string.knownClass_grayWarden));
            knownClass.put(RPGClass.KC_PRES_OPZ_GREEN_FAITH_ACOLYTE, ctx.getString(R.string.knownClass_greenFaithAcolyte));
            knownClass.put(RPGClass.KC_PRES_OPZ_GROUP_LEADER, ctx.getString(R.string.knownClass_groupLeader));
            knownClass.put(RPGClass.KC_PRES_OPZ_GUILD_AGENT, ctx.getString(R.string.knownClass_guildAgent));
            knownClass.put(RPGClass.KC_PRES_OPZ_GUILD_POISONER, ctx.getString(R.string.knownClass_poisoner));
            knownClass.put(RPGClass.KC_PRES_OPZ_HALFLING_OPPORTUNIST, ctx.getString(R.string.knownClass_halflingOpportunist));
            knownClass.put(RPGClass.KC_PRES_OPZ_HARROWER, ctx.getString(R.string.knownClass_harrower));
            knownClass.put(RPGClass.KC_PRES_OPZ_HELL_KNIGHT_COMMANDER, ctx.getString(R.string.knownClass_hellKnightCommander));
            knownClass.put(RPGClass.KC_PRES_OPZ_HELL_KNIGHT_ENFORCER, ctx.getString(R.string.knownClass_hellKnightEnforcer));
            knownClass.put(RPGClass.KC_PRES_OPZ_INHERITORS_CRUSADER, ctx.getString(R.string.knownClass_inheritorsCrusader));
            knownClass.put(RPGClass.KC_PRES_OPZ_JUSTICIAR, ctx.getString(R.string.knownClass_justiciar));
            knownClass.put(RPGClass.KC_PRES_OPZ_LANTERN_BEARER, ctx.getString(R.string.knownClass_lanternBearer));
            knownClass.put(RPGClass.KC_PRES_OPZ_LIBERATOR, ctx.getString(R.string.knownClass_liberator));
            knownClass.put(RPGClass.KC_PRES_OPZ_LION_BLADE, ctx.getString(R.string.knownClass_lionBlade));
            knownClass.put(RPGClass.KC_PRES_OPZ_LIVING_MONOLITH, ctx.getString(R.string.knownClass_livingMonolith));
            knownClass.put(RPGClass.KC_PRES_OPZ_LOW_TEMPLAR, ctx.getString(R.string.knownClass_lowTemplar));
            knownClass.put(RPGClass.KC_PRES_OPZ_MAGE_OF_THE_THIRD_EYE, ctx.getString(R.string.knownClass_mageOfTheThirdEye));
            knownClass.put(RPGClass.KC_PRES_OPZ_MAMMOTH_RIDER, ctx.getString(R.string.knownClass_mammothRider));
            knownClass.put(RPGClass.KC_PRES_OPZ_MASTER_OF_STORMS, ctx.getString(R.string.knownClass_masterOfStorms));
            knownClass.put(RPGClass.KC_PRES_OPZ_MYSTERY_CULTIST, ctx.getString(R.string.knownClass_mysteryCultist));
            knownClass.put(RPGClass.KC_PRES_OPZ_NATURAL_ALCHEMIST, ctx.getString(R.string.knownClass_naturalAlchemist));
            knownClass.put(RPGClass.KC_PRES_OPZ_NOBLE_SCION, ctx.getString(R.string.knownClass_nobleScion));
            knownClass.put(RPGClass.KC_PRES_OPZ_PAIN_TASTER, ctx.getString(R.string.knownClass_painTaster));
            knownClass.put(RPGClass.KC_PRES_OPZ_PIT_FIGHTER, ctx.getString(R.string.knownClass_pitFighter));
            knownClass.put(RPGClass.KC_PRES_OPZ_PLANES_WALKER, ctx.getString(R.string.knownClass_planesWalker));
            knownClass.put(RPGClass.KC_PRES_OPZ_PURITY_LEGION_ENFORCER, ctx.getString(R.string.knownClass_purityLegionEnforcer));
            knownClass.put(RPGClass.KC_PRES_OPZ_SANCTIFIED_PROPHET, ctx.getString(R.string.knownClass_sanctifiedProphet));
            knownClass.put(RPGClass.KC_PRES_OPZ_SENTINEL, ctx.getString(R.string.knownClass_sentinel));
            knownClass.put(RPGClass.KC_PRES_OPZ_SLEEPLESS_DETECTIVE, ctx.getString(R.string.knownClass_sleeplessDetective));
            knownClass.put(RPGClass.KC_PRES_OPZ_SOUL_WARDEN, ctx.getString(R.string.knownClass_soulWarden));
            knownClass.put(RPGClass.KC_PRES_OPZ_SOULEATER, ctx.getString(R.string.knownClass_souleater));
            knownClass.put(RPGClass.KC_PRES_OPZ_SPHEREWALKER, ctx.getString(R.string.knownClass_spherewalker));
            knownClass.put(RPGClass.KC_PRES_OPZ_STEEL_FALCON, ctx.getString(R.string.knownClass_steelFalcon));
            knownClass.put(RPGClass.KC_PRES_OPZ_STUDENT_OF_WAR, ctx.getString(R.string.knownClass_studentOfWar));
            knownClass.put(RPGClass.KC_PRES_OPZ_SUN_SEEKER, ctx.getString(R.string.knownClass_sunSeeker));
            knownClass.put(RPGClass.KC_PRES_OPZ_SWORDLORD, ctx.getString(R.string.knownClass_swordlord));
            knownClass.put(RPGClass.KC_PRES_OPZ_TATTOED_MYSTIC, ctx.getString(R.string.knownClass_tattoedMystic));
            knownClass.put(RPGClass.KC_PRES_OPZ_TECHNOMANCER, ctx.getString(R.string.knownClass_technomancer));
            knownClass.put(RPGClass.KC_PRES_OPZ_UMBRAL_AGENT, ctx.getString(R.string.knownClass_umbralAgent));
            knownClass.put(RPGClass.KC_PRES_OPZ_VEILED_ILLUSIONIST, ctx.getString(R.string.knownClass_veiledIllusionist));
            knownClass.put(RPGClass.KC_PRES_OPZ_WINTER_WITCH, ctx.getString(R.string.knownClass_winterWitch));
            knownClass.put(RPGClass.KC_NPC_ADEPT, ctx.getString(R.string.knownClass_npc_adept));
            knownClass.put(RPGClass.KC_NPC_ARISTOCRAT, ctx.getString(R.string.knownClass_npc_aristocrat));
            knownClass.put(RPGClass.KC_NPC_COMMONER, ctx.getString(R.string.knownClass_npc_commoner));
            knownClass.put(RPGClass.KC_NPC_EXPERT, ctx.getString(R.string.knownClass_npc_expert));
            knownClass.put(RPGClass.KC_NPC_WARRIOR, ctx.getString(R.string.knownClass_npc_warrior));
        }
        final String match = knownClass.get(protobufEnum);
        return match == null? knownClass.get(RPGClass.KC_INVALID) : match;
    }

    public static String levelAdvToString(int protobufEnum, Context ctx) {
        // So little! We switch this.
        switch(protobufEnum) {
            case LevelAdvancement.LA_PF_FAST: return ctx.getString(R.string.levelAdv_fast);
            case LevelAdvancement.LA_PF_MEDIUM: return ctx.getString(R.string.levelAdv_medium);
            case LevelAdvancement.LA_PF_SLOW: return ctx.getString(R.string.levelAdv_slow);
        }
        return ctx.getString(R.string.levelAdv_invalid);
    }

    private static HashMap<Integer, String> monRace, monSize, monType;
    private static HashMap<Integer, String> knownClass;
    private static HashMap<Integer, String> align;
}
