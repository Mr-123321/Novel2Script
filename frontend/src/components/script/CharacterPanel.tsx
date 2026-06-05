'use client';

import { useScriptStore } from '@/stores/script-store';
import { Badge } from '@/components/ui/badge';
import { roleLabel } from '@/lib/utils';
import type { Character } from '@/types/character';
import type { Scene } from '@/types/script';
import { Users, User } from 'lucide-react';

interface CharacterPanelProps {
  characters: Character[];
  selectedScene: Scene | null;
}

export function CharacterPanel({ characters, selectedScene }: CharacterPanelProps) {
  const { selectCharacter, selectedCharacterId } = useScriptStore();

  return (
    <div className="p-3 space-y-4">
      {/* Characters in selected scene */}
      {selectedScene && (
        <div>
          <h4 className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-2 px-1">
            场景角色
          </h4>
          <div className="space-y-0.5">
            {characters
              .filter((c) =>
                selectedScene.dialogues.some((d) => d.characterId === c.id)
              )
              .map((c) => (
                <button
                  key={c.id}
                  onClick={() => selectCharacter(c.id)}
                  className={`w-full text-left text-sm px-2.5 py-1.5 rounded-lg transition-all duration-200 ${
                    selectedCharacterId === c.id
                      ? 'bg-teal-500/10 text-teal-400 ring-1 ring-teal-500/20'
                      : 'hover:bg-white/5 text-muted-foreground hover:text-foreground'
                  }`}
                >
                  <span className="flex items-center gap-2 truncate">
                    <span
                      className="h-2 w-2 rounded-full shrink-0"
                      style={{
                        background:
                          'linear-gradient(135deg, oklch(0.72 0.14 185), oklch(0.65 0.16 200))',
                      }}
                    />
                    {c.canonicalName}
                  </span>
                </button>
              ))}
            {characters.filter((c) =>
              selectedScene.dialogues.some((d) => d.characterId === c.id)
            ).length === 0 && (
              <p className="text-xs text-muted-foreground px-2.5 py-2">
                当前场景无角色
              </p>
            )}
          </div>
        </div>
      )}

      {/* All characters */}
      <div>
        <h4 className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-2 px-1 flex items-center gap-1.5">
          <Users className="h-3 w-3" />
          所有角色
          <span className="text-[10px] bg-white/5 px-1.5 py-0.5 rounded-md ml-auto">
            {characters.length}
          </span>
        </h4>
        <div className="space-y-0.5">
          {characters.map((c) => (
            <button
              key={c.id}
              onClick={() => selectCharacter(c.id)}
              className={`w-full text-left text-sm px-2.5 py-1.5 rounded-lg transition-all duration-200 flex items-center justify-between ${
                selectedCharacterId === c.id
                  ? 'bg-teal-500/10 text-teal-400 ring-1 ring-teal-500/20'
                  : 'hover:bg-white/5 text-muted-foreground hover:text-foreground'
              }`}
            >
              <span className="flex items-center gap-2 truncate">
                <User className="h-3 w-3 shrink-0 opacity-40" />
                <span className="truncate">{c.canonicalName}</span>
              </span>
              <Badge
                variant="outline"
                className="text-[10px] px-1.5 py-0 shrink-0 ml-2"
              >
                {roleLabel(c.roleType)}
              </Badge>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
