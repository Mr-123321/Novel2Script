package com.novel2script.application.service.agent.model;

import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;

import java.util.List;

/**
 * Aggregate input for {@link com.novel2script.application.service.agent.ScriptComposer}.
 * Bundles all agent outputs and source material into a single composition request.
 *
 * @param novelId     the source novel ID
 * @param title       the script title
 * @param characters  resolved characters from {@code CharacterResolverAgent}
 * @param scenes      segmented scenes from {@code SceneAgent}
 * @param dialogues   generated dialogues from {@code DialogueAgent}
 * @param actions     generated actions from {@code ActionAgent}
 * @param plotEvents  extracted plot events from {@code PlotExtractionAgent}
 * @param chapters    source chapters from {@code ChapterParser}
 */
public record CompositionInput(
        Long novelId,
        String title,
        List<com.novel2script.domain.model.Character> characters,
        List<Scene> scenes,
        List<Dialogue> dialogues,
        List<Action> actions,
        List<PlotEvent> plotEvents,
        List<Chapter> chapters
) {
    /**
     * Validates that the input has the minimum required data for composition.
     */
    public boolean isValid() {
        return novelId != null
                && title != null && !title.isBlank()
                && characters != null && !characters.isEmpty()
                && scenes != null && !scenes.isEmpty();
    }
}
