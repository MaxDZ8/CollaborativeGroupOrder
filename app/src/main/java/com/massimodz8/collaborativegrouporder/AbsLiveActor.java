package com.massimodz8.collaborativegrouporder;

/**
 * It might be a monster, it might be a PG, it might be mananged remotely or locally, we don't
 * care! For the purpose of the game, there's a set of operations we want to do on the thing!
 */
public abstract class AbsLiveActor {
    public final String displayName;
    public final int type;

    public static final int TYPE_PLAYING_CHARACTER = 0;
    public static final int TYPE_MONSTER = 1;
    public static final int TYPE_NPC = 2;

    public String actionCondition; // if non-null (can be empty) this is condition text memo, skip normal order.
    public boolean conditionTriggered; // true if the condition has been triggered and is being resolved.

    public int nextRollRequestIndex;

    protected AbsLiveActor(String displayName, int type) {
        this.displayName = displayName;
        this.type = type;
    }

    public abstract int getInitiativeBonus();

    /**
     * Health is fairly complicated.
     * @return A 2D vector with [0] being current health and [1] being max defined health.
     * Note [0] can be > [1] as some actors might get temporary hit points.
     */
    public abstract int[] getHealth();
}
