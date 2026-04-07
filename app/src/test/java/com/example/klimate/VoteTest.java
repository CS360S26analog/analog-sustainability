/**
 * VoteTest.java
 *
 * Unit tests for the Vote model class.
 * Tests cover constructor initialisation, upvote and downvote flag
 * correctness, and getter/setter behaviour.
 * No Firebase or Android dependencies needed — pure Java logic only.
 *
 * @author Karar
 */
package com.example.klimate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.klimate.model.Vote;
import com.google.firebase.Timestamp;

import org.junit.Test;

public class VoteTest {

    @Test
    public void constructor_setsAllFieldsCorrectly() {
        Timestamp now = Timestamp.now();

        Vote vote = new Vote(
                "vote001",
                "log123",
                "user456",
                true,
                now
        );

        assertEquals("vote001", vote.getVoteId());
        assertEquals("log123", vote.getLogId());
        assertEquals("user456", vote.getVoterId());
        assertTrue(vote.isUpvote());
        assertEquals(now, vote.getTimestamp());
    }

    @Test
    public void settersAndGetters_workCorrectly() {
        Timestamp now = Timestamp.now();

        Vote vote = new Vote();
        vote.setVoteId("vote777");
        vote.setLogId("log999");
        vote.setVoterId("user222");
        vote.setUpvote(false);
        vote.setTimestamp(now);

        assertEquals("vote777", vote.getVoteId());
        assertEquals("log999", vote.getLogId());
        assertEquals("user222", vote.getVoterId());
        assertFalse(vote.isUpvote());
        assertEquals(now, vote.getTimestamp());
    }

    @Test
    public void noArgConstructor_createsUsableObject() {
        Vote vote = new Vote();

        assertNotNull(vote);
        assertFalse(vote.isUpvote());
    }

    @Test
    public void setUpvote_togglesVoteDirectionCorrectly() {
        Vote vote = new Vote();

        vote.setUpvote(true);
        assertTrue(vote.isUpvote());

        vote.setUpvote(false);
        assertFalse(vote.isUpvote());
    }
}