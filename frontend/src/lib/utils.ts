import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

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
  // Skip timeOfDay if it's UNKNOWN/unknown or empty
  const timeStr = (!scene.timeOfDay || scene.timeOfDay === 'UNKNOWN' || scene.timeOfDay === 'unknown')
    ? ''
    : ` - ${scene.timeOfDay}`;
  return `${loc}${timeStr}`;
};

/**
 * Sanitize a scene heading string by removing INT/EXT prefix and UNKNOWN suffix.
 * Handles backend-generated headings like "INT. 教室 - UNKNOWN" → "教室"
 */
export function sanitizeSceneHeading(heading: string): string {
  if (!heading) return '未知地点';
  let cleaned = heading
    .replace(/^(INT|EXT)\.\s*/i, '')   // Remove INT/EXT prefix
    .replace(/\s*-\s*UNKNOWN\s*$/i, '') // Remove trailing "- UNKNOWN"
    .replace(/\s*-\s*unknown\s*$/i, '')
    .replace(/\s*-\s*未知\s*$/i, '')   // Also handle Chinese "未知"
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
