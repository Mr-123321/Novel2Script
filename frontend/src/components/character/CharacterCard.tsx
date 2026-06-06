'use client';

import { useScriptStore } from '@/stores/script-store';
import { Badge } from '@/components/ui/badge';
import { roleLabel } from '@/lib/utils';
import { User, Users, Hash } from 'lucide-react';
import type { Character } from '@/types/character';

const GENDER_DISPLAY: Record<string, { label: string; color: string; icon: string }> = {
  MALE: { label: '男', color: 'text-blue-400', icon: '♂' },
  FEMALE: { label: '女', color: 'text-pink-400', icon: '♀' },
};

interface CharacterCardProps {
  character: Character;
}

function getRoleBadgeVariant(role: string) {
  switch (role) {
    case 'PROTAGONIST':
      return 'default' as const;
    case 'ANTAGONIST':
      return 'destructive' as const;
    case 'SUPPORTING':
      return 'accent' as const;
    default:
      return 'secondary' as const;
  }
}

export function CharacterCard({ character }: CharacterCardProps) {
  const { selectCharacter } = useScriptStore();

  return (
    <button
      onClick={() => selectCharacter(character.id)}
      className="text-left rounded-xl border border-white/5 bg-white/[0.02] p-5 card-lift group w-full"
    >
      {/* Header */}
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-3 min-w-0">
          <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-teal-500/20 to-purple-500/20 flex items-center justify-center shrink-0 ring-1 ring-white/5 group-hover:ring-teal-500/20 transition-all">
            <User className="h-5 w-5 text-teal-400" />
          </div>
          <div className="min-w-0">
            <h3 className="font-semibold text-sm truncate">
              {character.canonicalName}
            </h3>
            {character.aliases.length > 0 && (
              <p className="text-[11px] text-muted-foreground truncate mt-0.5">
                {character.aliases.slice(0, 2).join('、')}
                {character.aliases.length > 2 && ` +${character.aliases.length - 2}`}
              </p>
            )}
          </div>
        </div>
        <Badge variant={getRoleBadgeVariant(character.roleType)} className="text-[10px] shrink-0">
          {roleLabel(character.roleType)}
        </Badge>
      </div>

      {/* Description */}
      {character.description && (
        <p className="text-xs text-muted-foreground line-clamp-2 mb-3 leading-relaxed">
          {character.description}
        </p>
      )}

      {/* Stats */}
      <div className="flex items-center gap-4 text-[11px] text-muted-foreground">
        {character.gender && GENDER_DISPLAY[character.gender] && (
          <span className={`flex items-center gap-1 ${GENDER_DISPLAY[character.gender].color}`}>
            <span className="text-xs font-bold">{GENDER_DISPLAY[character.gender].icon}</span>
            {GENDER_DISPLAY[character.gender].label}
          </span>
        )}
        <span className="flex items-center gap-1">
          <Hash className="h-3 w-3" />
          出场 {character.appearanceCount} 次
        </span>
        {character.relationships.length > 0 && (
          <span className="flex items-center gap-1">
            <Users className="h-3 w-3" />
            {character.relationships.length} 关系
          </span>
        )}
      </div>

      {/* Personality tags */}
      {character.personality.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-3 pt-3 border-t border-white/5">
          {character.personality.slice(0, 3).map((trait: string, i: number) => (
            <span
              key={i}
              className="text-[10px] px-2 py-0.5 rounded-full bg-white/5 text-muted-foreground"
            >
              {trait}
            </span>
          ))}
          {character.personality.length > 3 && (
            <span className="text-[10px] text-muted-foreground">
              +{character.personality.length - 3}
            </span>
          )}
        </div>
      )}
    </button>
  );
}
