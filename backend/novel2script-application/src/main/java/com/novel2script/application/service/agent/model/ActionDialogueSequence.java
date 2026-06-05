package com.novel2script.application.service.agent.model;

import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Dialogue;

/**
 * A unified timeline item that interleaves {@link Action} and {@link Dialogue}
 * in chronological order within a scene.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   List<ActionDialogueSequence> timeline = actionAgent.interleave(actions, dialogues);
 *   for (ActionDialogueSequence item : timeline) {
 *       switch (item) {
 *           case ActionDialogueSequence.ActionItem(var act) ->
 *               System.out.println("  [ACTION] " + act.getDescription());
 *           case ActionDialogueSequence.DialogueItem(var dia) ->
 *               System.out.println(dia.getSpeaker() + ": " + dia.getContent());
 *       }
 *   }
 * }</pre>
 *
 * <p>This is a sealed interface (Java 17) — only {@link ActionItem} and
 * {@link DialogueItem} are permitted implementations.
 */
public sealed interface ActionDialogueSequence
        permits ActionDialogueSequence.ActionItem, ActionDialogueSequence.DialogueItem {

    /**
     * An action step in the timeline.
     */
    record ActionItem(Action action) implements ActionDialogueSequence {
        /**
         * Convenience: the sequence number of the wrapped action.
         */
        public int sequence() {
            return action != null ? action.getSequence() : 0;
        }
    }

    /**
     * A dialogue line in the timeline.
     */
    record DialogueItem(Dialogue dialogue) implements ActionDialogueSequence {
        /**
         * Convenience: the sequence number of the wrapped dialogue.
         */
        public int sequence() {
            return dialogue != null ? dialogue.getSequence() : 0;
        }

        /**
         * Convenience: the speaker name.
         */
        public String speaker() {
            return dialogue != null ? dialogue.getSpeaker() : null;
        }
    }
}
