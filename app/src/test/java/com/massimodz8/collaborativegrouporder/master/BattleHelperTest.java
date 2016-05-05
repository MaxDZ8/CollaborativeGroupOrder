package com.massimodz8.collaborativegrouporder.master;

import com.massimodz8.collaborativegrouporder.InitiativeScore;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

/**
 * Created by Massimo on 04/05/2016.
 * Ticking the battle state takes care. I have just rewritten it and I'm not sure it works against
 * the surrounding UI code as it should so let's be at least sure about what it does and how it
 * operates with some known data in isolation.
 */
public class BattleHelperTest {
    private InitiativeScore[] knownOrder;
    private BattleHelper helper;

    @Before
    public void setUp() throws Exception {
        knownOrder = new InitiativeScore[]{
                new InitiativeScore(23, 6, 123, 2),
                new InitiativeScore(18, 5, 123, 3),
                new InitiativeScore(12, 3, 999, 1),
                new InitiativeScore(12, 3, 123, 6),
                new InitiativeScore(12, 0, 123, 4),
                new InitiativeScore( 3, 0, 123, 5),
                new InitiativeScore(-1, 0, 123, 0)
        };
        helper = new BattleHelper(Arrays.copyOf(knownOrder, knownOrder.length));
        helper.round = 1;
    }

    /// TEST 1: Shuffling something to itself is always nop. ///////////////////////////////////////
    @Test
    public void shuffleToYourselfShouldBeNOP() throws Exception {
        BattleHelper helper = new BattleHelper(Arrays.copyOf(knownOrder, knownOrder.length));
        helper.round = 1;

        for(int loop = 0; loop < knownOrder.length; loop++) {
            helper.currentActor = knownOrder[loop].actorID;
            final boolean moved = helper.before(loop);
            Assert.assertArrayEquals(knownOrder, helper.ordered);
            Assert.assertFalse(moved);
            Assert.assertEquals(knownOrder[loop].actorID, helper.ordered[loop].actorID);
        }
    }

    /// TEST 2: act for first //////////////////////////////////////////////////////////////////////
    @Test
    public void shuffleToFirstShouldMatch() throws Exception {
        helper.currentActor = knownOrder[1].actorID;
        boolean moved = helper.before(0);
        Assert.assertArrayEquals(new int[] {3,2,1,6,4,5,0}, getActorIds(helper.ordered));
        Assert.assertTrue(moved);
        Assert.assertTrue(helper.actorCompleted(true) == 3);
        Assert.assertTrue(helper.currentActor == 1);

        helper = new BattleHelper(Arrays.copyOf(knownOrder, knownOrder.length));
        helper.currentActor = knownOrder[6].actorID;
        moved = helper.before(0);
        Assert.assertArrayEquals(new int[] {0,2,3,1,6,4,5}, getActorIds(helper.ordered));
        Assert.assertTrue(moved);
        Assert.assertTrue(helper.actorCompleted(true) == 0);
        Assert.assertTrue(helper.currentActor == 0); // super odd but makes sense

        helper = new BattleHelper(Arrays.copyOf(knownOrder, knownOrder.length));
        helper.currentActor = knownOrder[4].actorID;
        moved = helper.before(0);
        Assert.assertArrayEquals(new int[] {4,2,3,1,6,5,0}, getActorIds(helper.ordered));
        Assert.assertTrue(moved);
        Assert.assertTrue(helper.actorCompleted(true) == 4);
        Assert.assertTrue(helper.currentActor == 5);
    }

    /// TEST 3: act for last ///////////////////////////////////////////////////////////////////////
    @Test
    public void shuffleToLastShouldMatch() throws Exception {
        helper.currentActor = knownOrder[0].actorID;
        boolean moved = helper.before(helper.ordered.length - 1);
        Assert.assertArrayEquals(new int[] {3,1,6,4,5,2,0}, getActorIds(helper.ordered));
        Assert.assertEquals(true, moved);
        Assert.assertTrue(helper.actorCompleted(true) == 2);
        Assert.assertTrue(helper.currentActor == 3);

        helper = new BattleHelper(Arrays.copyOf(knownOrder, knownOrder.length));
        helper.currentActor = knownOrder[2].actorID;
        moved = helper.before(helper.ordered.length - 1);
        Assert.assertArrayEquals(new int[] {2,3,6,4,5,1,0}, getActorIds(helper.ordered));
        Assert.assertEquals(true, moved);
        Assert.assertTrue(helper.actorCompleted(true) == 1);
        Assert.assertTrue(helper.currentActor == 6);

        helper = new BattleHelper(Arrays.copyOf(knownOrder, knownOrder.length));
        helper.currentActor = knownOrder[5].actorID;
        moved = helper.before(helper.ordered.length - 1);
        Assert.assertArrayEquals(new int[] {2,3,1,6,4,5,0}, getActorIds(helper.ordered));
        Assert.assertEquals(false, moved);
        Assert.assertTrue(helper.actorCompleted(true) == 5);
        Assert.assertTrue(helper.currentActor == 0);
    }

    // TEST 4: put me at some Nth position /////////////////////////////////////////////////////////
    @Test
    public void shuffleBeforeNthShouldMatch() throws Exception {
        helper.currentActor = knownOrder[0].actorID;
        boolean moved = helper.before(2);
        Assert.assertArrayEquals(new int[] {3,2,1,6,4,5,0}, getActorIds(helper.ordered));
        Assert.assertEquals(true, moved);
        Assert.assertTrue(helper.actorCompleted(true) == 2);
        Assert.assertTrue(helper.currentActor == 3);

        helper = new BattleHelper(Arrays.copyOf(knownOrder, knownOrder.length));
        helper.currentActor = knownOrder[2].actorID;
        moved = helper.before(5);
        Assert.assertArrayEquals(new int[] {2,3,6,4,1,5,0}, getActorIds(helper.ordered));
        Assert.assertEquals(true, moved);
        Assert.assertTrue(helper.actorCompleted(true) == 1);
        Assert.assertTrue(helper.currentActor == 6);

        helper = new BattleHelper(Arrays.copyOf(knownOrder, knownOrder.length));
        helper.currentActor = knownOrder[4].actorID;
        moved = helper.before(1);
        Assert.assertArrayEquals(new int[] {2,4,3,1,6,5,0}, getActorIds(helper.ordered));
        Assert.assertEquals(true, moved);
        Assert.assertTrue(helper.actorCompleted(true) == 4);
        Assert.assertTrue(helper.currentActor == 5);

        helper = new BattleHelper(Arrays.copyOf(knownOrder, knownOrder.length));
        helper.currentActor = knownOrder[6].actorID;
        moved = helper.before(2);
        Assert.assertArrayEquals(new int[] {2,3,0,1,6,4,5}, getActorIds(helper.ordered));
        Assert.assertEquals(true, moved);
        Assert.assertTrue(helper.actorCompleted(true) == 0);
        Assert.assertTrue(helper.currentActor == 2);
        Assert.assertTrue(helper.round == 0);
    }

    private int[] getActorIds(InitiativeScore[] arr) {
        int[] res = new int[arr.length];
        int cp = 0;
        for (InitiativeScore el : arr) res[cp++] = el.actorID;
        return res;
    }
}