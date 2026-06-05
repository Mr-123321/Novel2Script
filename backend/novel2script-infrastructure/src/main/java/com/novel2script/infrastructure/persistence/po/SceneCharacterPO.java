package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("scene_characters")
public class SceneCharacterPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sceneId;

    private Long characterId;

    private String roleInScene;
}
