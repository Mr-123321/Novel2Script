package com.novel2script.application.service.workflow;

import com.novel2script.application.service.agent.ActionAgent;
import com.novel2script.application.service.agent.CharacterAgent;
import com.novel2script.application.service.agent.CharacterResolverAgent;
import com.novel2script.application.service.agent.DialogueAgent;
import com.novel2script.application.service.agent.PlotExtractionAgent;
import com.novel2script.application.service.agent.SceneAgent;
import com.novel2script.application.service.agent.ScriptComposer;

/**
 * Bundles the real generation agents for injection into workflow steps.
 * <p>
 * A single record parameter replaces seven constructor/lambda-capture
 * parameters — workflow step factories take {@code (GenerationContext, AgentBundle)}
 * without the parameter list exploding.
 * <p>
 * <b>NOTE(接入前置，W12)</b>: 本 record 当前尚未被任何工作流步骤使用，
 * 是 {@code WorkflowEngine} 接入真实 Agent 的前置准备。接入方案见论文「总结与展望」。
 *
 * @param characterAgent        角色提取
 * @param characterResolverAgent 角色消歧（向量 + 规则）
 * @param plotExtractionAgent    情节提取
 * @param sceneAgent             场景切分
 * @param dialogueAgent          对白生成
 * @param actionAgent            动作生成
 * @param scriptComposer         剧本合成
 */
public record AgentBundle(
        CharacterAgent characterAgent,
        CharacterResolverAgent characterResolverAgent,
        PlotExtractionAgent plotExtractionAgent,
        SceneAgent sceneAgent,
        DialogueAgent dialogueAgent,
        ActionAgent actionAgent,
        ScriptComposer scriptComposer
) {
}
