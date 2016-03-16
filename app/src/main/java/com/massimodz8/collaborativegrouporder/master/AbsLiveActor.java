package com.massimodz8.collaborativegrouporder.master;

import android.support.annotation.StringRes;

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

    protected AbsLiveActor(String displayName, int type) {
        this.displayName = displayName;
        this.type = type;
    }

    abstract int getInitiativeBonus();

    /**
     * Health is fairly complicated.
     * @return A 2D vector with [0] being current health and [1] being max defined health.
     * Note [0] can be > [1] as some actors might get temporary hit points.
     */
    abstract int[] getHealth();
}
