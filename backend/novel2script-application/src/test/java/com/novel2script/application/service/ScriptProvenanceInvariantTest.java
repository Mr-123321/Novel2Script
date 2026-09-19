package com.novel2script.application.service;

import com.novel2script.common.enums.ContentSource;
import com.novel2script.common.enums.GenerationStatus;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import com.novel2script.infrastructure.mapper.ActionMapper;
import com.novel2script.infrastructure.mapper.CharacterMapper;
import com.novel2script.infrastructure.mapper.DialogueMapper;
import com.novel2script.infrastructure.mapper.PlotEventMapper;
import com.novel2script.infrastructure.mapper.PlotInsertionMapper;
import com.novel2script.infrastructure.mapper.SceneMapper;
import com.novel2script.infrastructure.mapper.ScriptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W06 — 内容来源（source）与生成失败状态（dialogue_status / action_status）不变量。
 *
 * <p>核心契约：<b>人工编辑只把该行的 {@code source} 置为 MANUAL，绝不翻转场景级的
 * FAILED 状态。</b>失败是既成事实，必须长期保留 —— 这样"哪些场景生成失败、后来由谁
 * 手工补齐"才能追溯，也避免了"补一条对白就当作生成成功"的误判。
 *
 * <p>同样的道理：{@code updateScene} 只接受白名单字段，客户端即使在请求体里塞
 * {@code dialogueStatus}/{@code actionStatus} 也无法篡改失败记录。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("W06 — 内容来源与失败状态不变量")
class ScriptProvenanceInvariantTest {

    private static final long SCRIPT_ID = 1L;
    private static final long SCENE_ID = 10L;

    @Mock private ScriptMapper scriptMapper;
    @Mock private CharacterMapper characterMapper;
    @Mock private SceneMapper sceneMapper;
    @Mock private DialogueMapper dialogueMapper;
    @Mock private ActionMapper actionMapper;
    @Mock private PlotEventMapper plotEventMapper;
    @Mock private PlotInsertionMapper plotInsertionMapper;
    @Mock private GenerationOrchestrator orchestrator;

    private ScriptService scriptService;

    /** A scene whose dialogue AND action generation both failed, left empty on purpose. */
    private Scene failedScene;

    @BeforeEach
    void setUp() {
        scriptService = new ScriptService(scriptMapper, characterMapper, sceneMapper,
                dialogueMapper, actionMapper, plotEventMapper, plotInsertionMapper, orchestrator);

        failedScene = Scene.builder()
                .id(SCENE_ID)
                .scriptId(SCRIPT_ID)
                .sceneNumber(1)
                .dialogueStatus(GenerationStatus.FAILED)
                .actionStatus(GenerationStatus.FAILED)
                .build();
    }

    private Script stubScript() {
        Script script = new Script();
        script.setId(SCRIPT_ID);
        return script;
    }

    @Nested
    @DisplayName("updateScene 的字段白名单")
    class UpdateSceneWhitelist {

        @Test
        @DisplayName("请求体携带 dialogueStatus/actionStatus 不得翻转 FAILED")
        void refusesToFlipGenerationStatusFromPayload() {
            when(sceneMapper.selectById(SCENE_ID)).thenReturn(failedScene);
            when(scriptMapper.selectById(SCRIPT_ID)).thenReturn(stubScript());

            Map<String, Object> payload = new HashMap<>();
            payload.put("title", "改个标题");
            payload.put("dialogueStatus", "COMPLETED");
            payload.put("actionStatus", "COMPLETED");

            scriptService.updateScene(SCRIPT_ID, SCENE_ID, payload);

            ArgumentCaptor<Scene> captor = ArgumentCaptor.forClass(Scene.class);
            verify(sceneMapper).updateById(captor.capture());
            Scene saved = captor.getValue();

            assertThat(saved.getTitle()).as("白名单内的字段正常生效").isEqualTo("改个标题");
            assertThat(saved.getDialogueStatus())
                    .as("失败事实不可被客户端改写")
                    .isEqualTo(GenerationStatus.FAILED);
            assertThat(saved.getActionStatus())
                    .as("失败事实不可被客户端改写")
                    .isEqualTo(GenerationStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("人工补充内容")
    class ManualEditing {

        @Test
        @DisplayName("addDialogue 只把新行标为 MANUAL，场景行完全不动")
        void addDialogue_stampsManualAndLeavesSceneRowUntouched() {
            when(scriptMapper.selectById(SCRIPT_ID)).thenReturn(stubScript());
            when(sceneMapper.selectById(SCENE_ID)).thenReturn(failedScene);
            when(dialogueMapper.selectCount(any())).thenReturn(0L);

            Dialogue dialogue = new Dialogue();
            dialogue.setSpeaker("林川");
            dialogue.setContent("手工补写的第一行");
            dialogue.setSequence(10);

            scriptService.addDialogue(SCRIPT_ID, SCENE_ID, dialogue);

            assertThat(dialogue.getSource()).isEqualTo(ContentSource.MANUAL);
            verify(sceneMapper, never()).updateById(any(Scene.class));
        }

        @Test
        @DisplayName("updateDialogue 标 MANUAL，场景行完全不动")
        void updateDialogue_stampsManualAndLeavesSceneRowUntouched() {
            when(scriptMapper.selectById(SCRIPT_ID)).thenReturn(stubScript());
            when(dialogueMapper.selectById(99L)).thenReturn(existingDialogue());

            Map<String, Object> updates = new HashMap<>();
            updates.put("content", "改过的台词");

            scriptService.updateDialogue(SCRIPT_ID, SCENE_ID, 99L, updates);

            ArgumentCaptor<Dialogue> captor = ArgumentCaptor.forClass(Dialogue.class);
            verify(dialogueMapper).updateById(captor.capture());
            Dialogue saved = captor.getValue();

            assertThat(saved.getContent()).isEqualTo("改过的台词");
            assertThat(saved.getSource()).isEqualTo(ContentSource.MANUAL);
            verify(sceneMapper, never()).updateById(any(Scene.class));
        }

        @Test
        @DisplayName("addAction 只把新行标为 MANUAL，场景行完全不动")
        void addAction_stampsManualAndLeavesSceneRowUntouched() {
            when(scriptMapper.selectById(SCRIPT_ID)).thenReturn(stubScript());
            when(sceneMapper.selectById(SCENE_ID)).thenReturn(failedScene);

            Action action = new Action();
            action.setActionType("ACTION");
            action.setDescription("手工补写的动作");
            action.setSequence(20);

            scriptService.addAction(SCRIPT_ID, SCENE_ID, action);

            assertThat(action.getSource()).isEqualTo(ContentSource.MANUAL);
            verify(sceneMapper, never()).updateById(any(Scene.class));
        }

        @Test
        @DisplayName("updateAction 标 MANUAL，场景行完全不动")
        void updateAction_stampsManualAndLeavesSceneRowUntouched() {
            when(scriptMapper.selectById(SCRIPT_ID)).thenReturn(stubScript());
            when(actionMapper.selectById(77L)).thenReturn(existingAction());

            Map<String, Object> updates = new HashMap<>();
            updates.put("description", "改过的动作");

            scriptService.updateAction(SCRIPT_ID, SCENE_ID, 77L, updates);

            ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
            verify(actionMapper).updateById(captor.capture());
            Action saved = captor.getValue();

            assertThat(saved.getDescription()).isEqualTo("改过的动作");
            assertThat(saved.getSource()).isEqualTo(ContentSource.MANUAL);
            verify(sceneMapper, never()).updateById(any(Scene.class));
        }

        /** Failed scene + one human-written line: the row keeps its FAILED flag. */
        @Test
        @DisplayName("补齐一条对白后，场景的 dialogueStatus 仍为 FAILED（可追溯）")
        void patchingOneLineKeepsTheFailureRecord() {
            when(scriptMapper.selectById(SCRIPT_ID)).thenReturn(stubScript());
            when(sceneMapper.selectById(SCENE_ID)).thenReturn(failedScene);
            when(dialogueMapper.selectCount(any())).thenReturn(1L);

            Dialogue dialogue = new Dialogue();
            dialogue.setSpeaker("苏晚");
            dialogue.setContent("补上的台词");
            dialogue.setSequence(10);

            scriptService.addDialogue(SCRIPT_ID, SCENE_ID, dialogue);

            assertThat(dialogue.getSource()).isEqualTo(ContentSource.MANUAL);
            assertThat(failedScene.getDialogueStatus())
                    .as("补一条对白 ≠ 生成成功")
                    .isEqualTo(GenerationStatus.FAILED);
            assertThat(failedScene.getActionStatus()).isEqualTo(GenerationStatus.FAILED);
        }

        private Dialogue existingDialogue() {
            Dialogue dialogue = new Dialogue();
            dialogue.setId(99L);
            dialogue.setSceneId(SCENE_ID);
            dialogue.setSpeaker("林川");
            dialogue.setContent("原始台词");
            dialogue.setSequence(10);
            dialogue.setSource(ContentSource.AI);
            return dialogue;
        }

        private Action existingAction() {
            Action action = new Action();
            action.setId(77L);
            action.setSceneId(SCENE_ID);
            action.setActionType("ACTION");
            action.setDescription("原始动作");
            action.setSequence(20);
            action.setSource(ContentSource.AI);
            return action;
        }
    }
}
