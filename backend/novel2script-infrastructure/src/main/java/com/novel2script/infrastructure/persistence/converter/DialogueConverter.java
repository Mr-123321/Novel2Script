package com.novel2script.infrastructure.persistence.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.common.enums.Emotion;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.infrastructure.persistence.po.DialoguePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DialogueConverter {

    private final ObjectMapper objectMapper;

    public DialoguePO toPo(Dialogue dialogue) {
        if (dialogue == null) {
            return null;
        }
        DialoguePO po = new DialoguePO();
        po.setId(dialogue.getId());
        po.setSceneId(dialogue.getSceneId());
        po.setCharacterId(dialogue.getCharacterId());
        po.setSequence(dialogue.getSequence());
        po.setSpeaker(dialogue.getSpeaker());
        po.setEmotion(dialogue.getEmotion() != null ? dialogue.getEmotion().name() : null);
        po.setContent(dialogue.getContent());
        po.setParenthetical(dialogue.getParenthetical());
        po.setReplyTo(dialogue.getReplyTo());
        po.setCreatedAt(dialogue.getCreatedAt());
        return po;
    }

    public Dialogue toDomain(DialoguePO po) {
        if (po == null) {
            return null;
        }
        Dialogue dialogue = new Dialogue();
        dialogue.setId(po.getId());
        dialogue.setSceneId(po.getSceneId());
        dialogue.setCharacterId(po.getCharacterId());
        dialogue.setSequence(po.getSequence() != null ? po.getSequence() : 0);
        dialogue.setSpeaker(po.getSpeaker());
        dialogue.setEmotion(po.getEmotion() != null ? Emotion.valueOf(po.getEmotion()) : null);
        dialogue.setContent(po.getContent());
        dialogue.setParenthetical(po.getParenthetical());
        dialogue.setReplyTo(po.getReplyTo());
        dialogue.setCreatedAt(po.getCreatedAt());
        return dialogue;
    }

    public List<DialoguePO> toPoList(List<Dialogue> dialogues) {
        if (dialogues == null) {
            return Collections.emptyList();
        }
        return dialogues.stream().map(this::toPo).collect(Collectors.toList());
    }

    public List<Dialogue> toDomainList(List<DialoguePO> pos) {
        if (pos == null) {
            return Collections.emptyList();
        }
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }
}
