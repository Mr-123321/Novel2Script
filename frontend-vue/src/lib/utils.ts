export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

export function formatChineseCharCount(count: number): string {
  if (count >= 10_000) {
    return `${(count / 10_000).toFixed(1)} 万字`;
  }
  return `${count} 字`;
}

export function stepLabel(step: string): string {
  const labels: Record<string, string> = {
    CHAPTER_PARSE: '章节解析',
    CHARACTER_EXTRACT: '人物抽取',
    CHARACTER_RESOLVE: '人物去重',
    PLOT_EXTRACT: '剧情分析',
    SCENE_SEGMENT: '场景切分',
    DIALOGUE_GENERATE: '对白生成',
    ACTION_GENERATE: '动作生成',
    SCRIPT_COMPOSE: '剧本合成',
    YAML_EXPORT: 'YAML 导出',
    STORYBOARD_GENERATE: '分镜生成',
  };
  return labels[step] ?? step;
}

export function emotionLabel(emotion: string): string {
  const labels: Record<string, string> = {
    ANGRY: '愤怒',
    HAPPY: '开心',
    SAD: '悲伤',
    CALM: '平静',
    FEARFUL: '恐惧',
    SURPRISED: '惊讶',
    NEUTRAL: '中性',
  };
  return labels[emotion] ?? emotion;
}

export function roleLabel(role: string): string {
  const labels: Record<string, string> = {
    PROTAGONIST: '主角',
    DEUTERAGONIST: '重要配角',
    ANTAGONIST: '反派',
    SUPPORTING: '配角',
    MINOR: '龙套',
  };
  return labels[role] ?? role;
}

export const sceneHeader = (scene: { interior: boolean; location: string; timeOfDay: string; sceneNumber: number }) => {
  const loc = scene.location || '未知地点';
  const timeStr = (!scene.timeOfDay || scene.timeOfDay === 'UNKNOWN' || scene.timeOfDay === 'unknown')
    ? ''
    : ` - ${scene.timeOfDay}`;
  return `${loc}${timeStr}`;
};

/**
 * Sanitize a scene heading string by removing INT/EXT prefix and UNKNOWN suffix.
 */
export function sanitizeSceneHeading(heading: string): string {
  if (!heading) return '未知地点';
  let cleaned = heading
    .replace(/^(INT|EXT)\.\s*/i, '')
    .replace(/\s*-\s*UNKNOWN\s*$/i, '')
    .replace(/\s*-\s*unknown\s*$/i, '')
    .replace(/\s*-\s*未知\s*$/i, '')
    .trim();
  return cleaned || '未知地点';
}

/** Format time of day for display, filtering out UNKNOWN values */
export function formatTimeOfDay(timeOfDay: string): string {
  if (!timeOfDay || timeOfDay === 'UNKNOWN' || timeOfDay === 'unknown' || timeOfDay === '未知') {
    return '';
  }
  return timeOfDay;
}

/** Simple class name concatenation (replaces clsx + tailwind-merge) */
export function cn(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(' ');
}

// ── Client-side Markdown generation (fallback when backend endpoint unavailable) ──

interface MdScene {
  sceneNumber: number
  location?: string
  timeOfDay?: string
  interior?: boolean
  title?: string
  mood?: string
  summary?: string
  characterIds?: number[]
  dialogues: Array<{
    speaker: string
    emotion?: string
    content: string
    parenthetical?: string
    sequence: number
  }>
  actions: Array<{
    actionType: string
    description: string
    durationMs?: number
    sequence: number
  }>
}

interface MdCharacter {
  id: number
  canonicalName: string
}

interface MdScriptData {
  title: string
  scenes: MdScene[]
  characters: MdCharacter[]
}

export function generateMdContent(data: MdScriptData): string {
  const lines: string[] = []

  // Title
  lines.push(`# 《${data.title || '未命名剧本'}》`)
  lines.push('')

  if (!data.scenes || data.scenes.length === 0) {
    lines.push('> （暂无场景内容）')
    return lines.join('\n')
  }

  const charMap = new Map<number, string>()
  for (const c of data.characters) {
    charMap.set(c.id, c.canonicalName)
  }

  for (const scene of data.scenes) {
    lines.push('---')
    lines.push('')

    // Scene heading
    let heading = `## 场景 ${scene.sceneNumber}`
    const loc = scene.location || '未知地点'
    const timeStr = scene.timeOfDay && scene.timeOfDay !== 'UNKNOWN'
      ? ` - ${scene.timeOfDay}` : ''
    heading += `：${loc}${timeStr}`
    if (scene.title) heading += ` — ${scene.title}`
    lines.push(heading)
    lines.push('')

    // Characters
    if (scene.characterIds && scene.characterIds.length > 0) {
      const names = scene.characterIds
        .map((id) => charMap.get(id))
        .filter((n): n is string => !!n)
      if (names.length > 0) {
        lines.push(`**出场角色**：${names.join('、')}`)
        lines.push('')
      }
    }

    // Mood
    if (scene.mood) {
      lines.push(`**场景氛围**：${scene.mood}`)
      lines.push('')
    }

    // Summary
    if (scene.summary) {
      lines.push(`> ${scene.summary}`)
      lines.push('')
    }

    // Content items (dialogues + actions, sorted by sequence)
    const items = [
      ...(scene.dialogues || []).map((d) => ({ type: 'dialogue' as const, seq: d.sequence, data: d })),
      ...(scene.actions || []).map((a) => ({ type: 'action' as const, seq: a.sequence, data: a })),
    ]
    items.sort((a, b) => a.seq - b.seq)

    for (const item of items) {
      if (item.type === 'dialogue') {
        const d = item.data as MdScene['dialogues'][number]
        let line = `**${d.speaker}**`
        if (d.emotion && d.emotion !== 'NEUTRAL') {
          line += `（${emotionLabel(d.emotion)}）`
        }
        if (d.parenthetical) {
          line += `（${d.parenthetical}）`
        }
        line += `："${d.content}"`
        lines.push(line)
        lines.push('')
      } else {
        const a = item.data as MdScene['actions'][number]
        const typeLabel = actionTypeLabelMd(a.actionType)
        let line = `*[${typeLabel}]* ${a.description}`
        if (a.durationMs && a.durationMs > 0) {
          line += ` （约${(a.durationMs / 1000).toFixed(1)}秒）`
        }
        lines.push(line)
        lines.push('')
      }
    }
  }

  return lines.join('\n')
}

function actionTypeLabelMd(type: string): string {
  const labels: Record<string, string> = {
    ACTION: '动作', REACTION: '反应', BEAT: '节拍', BUSINESS: '调度',
  }
  return labels[type] ?? type
}
