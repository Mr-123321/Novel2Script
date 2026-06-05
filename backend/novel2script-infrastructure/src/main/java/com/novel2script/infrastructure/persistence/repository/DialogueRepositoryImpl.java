package com.novel2script.infrastructure.persistence.repository;

import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.repository.DialogueRepository;
import com.novel2script.infrastructure.persistence.converter.DialogueConverter;
import com.novel2script.infrastructure.persistence.mapper.DialogueMapper;
import com.novel2script.infrastructure.persistence.po.DialoguePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DialogueRepositoryImpl implements DialogueRepository {

    private final DialogueMapper dialogueMapper;
    private final DialogueConverter dialogueConverter;

    @Override
    public void saveAll(List<Dialogue> dialogues) {
        List<DialoguePO> poList = dialogueConverter.toPoList(dialogues);
        for (DialoguePO po : poList) {
            dialogueMapper.insert(po);
        }
    }

    @Override
    public List<Dialogue> findBySceneIdOrderBySequence(Long sceneId) {
        return dialogueConverter.toDomainList(dialogueMapper.findBySceneIdOrderBySequence(sceneId));
    }

    @Override
    public void deleteBySceneId(Long sceneId) {
        dialogueMapper.deleteBySceneId(sceneId);
    }

    @Override
    public void deleteByScriptId(Long scriptId) {
        dialogueMapper.deleteByScriptId(scriptId);
    }
}
