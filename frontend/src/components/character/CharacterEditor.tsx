'use client';

import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useScriptStore } from '@/stores/script-store';
import { updateCharacter } from '@/lib/api';
import { roleLabel } from '@/lib/utils';
import { X, Plus, User, Save } from 'lucide-react';
import type { Character, Relationship } from '@/types/character';

interface CharacterEditorProps {
  character: Character;
}

export function CharacterEditor({ character }: CharacterEditorProps) {
  const { selectCharacter, updateCharacter: updateStore } = useScriptStore();
  const [saving, setSaving] = useState(false);

  const handleClose = () => selectCharacter(null);

  const handleSave = async () => {
    setSaving(true);
    try {
      await updateCharacter(character.scriptId, character.id, {
        canonicalName: character.canonicalName,
        roleType: character.roleType,
        description: character.description,
        aliases: character.aliases,
        personality: character.personality,
        relationships: character.relationships,
      });
      handleClose();
    } catch (err) {
      console.error('Failed to update character:', err);
    } finally {
      setSaving(false);
    }
  };

  const roleTypes = [
    { value: 'PROTAGONIST', color: 'teal' },
    { value: 'DEUTERAGONIST', color: 'blue' },
    { value: 'ANTAGONIST', color: 'red' },
    { value: 'SUPPORTING', color: 'purple' },
    { value: 'MINOR', color: 'gray' },
  ] as const;

  const genders = [
    { value: 'MALE', label: '男' },
    { value: 'FEMALE', label: '女' },
    { value: 'UNKNOWN', label: '未知' },
  ] as const;

  return (
    <Dialog open onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2.5">
            <span className="inline-flex h-7 w-7 items-center justify-center rounded-lg bg-teal-500/10">
              <User className="h-3.5 w-3.5 text-teal-400" />
            </span>
            编辑角色 — {character.canonicalName}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-5 mt-2">
          {/* Name */}
          <div>
            <Label className="mb-1.5 block">标准名称</Label>
            <Input
              defaultValue={character.canonicalName}
              onChange={(e) => updateStore(character.id, { canonicalName: e.target.value })}
            />
          </div>

          {/* Role Type & Gender */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label className="mb-1.5 block">角色类型</Label>
              <div className="flex flex-wrap gap-1.5">
                {roleTypes.map(({ value, color }) => (
                  <Badge
                    key={value}
                    variant={character.roleType === value ? 'default' : 'outline'}
                    className={`cursor-pointer transition-all ${
                      character.roleType === value
                        ? ''
                        : 'hover:border-white/20'
                    }`}
                    onClick={() => updateStore(character.id, { roleType: value })}
                  >
                    {roleLabel(value)}
                  </Badge>
                ))}
              </div>
            </div>

            <div>
              <Label className="mb-1.5 block">性别</Label>
              <div className="flex gap-1.5">
                {genders.map(({ value, label }) => (
                  <Badge
                    key={value}
                    variant={character.gender === value ? 'default' : 'outline'}
                    className={`cursor-pointer transition-all ${
                      character.gender === value
                        ? ''
                        : 'hover:border-white/20'
                    }`}
                    onClick={() => updateStore(character.id, { gender: value })}
                  >
                    {label}
                  </Badge>
                ))}
              </div>
            </div>
          </div>

          {/* Description */}
          <div>
            <Label className="mb-1.5 block">描述</Label>
            <textarea
              className="w-full min-h-[80px] rounded-lg border border-white/10 bg-white/5 px-3 py-2.5 text-sm backdrop-blur-sm transition-all duration-200 placeholder:text-muted-foreground/60 hover:border-white/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-500/40 focus-visible:border-teal-500/40 resize-y"
              defaultValue={character.description ?? ''}
              onChange={(e) => updateStore(character.id, { description: e.target.value })}
              placeholder="角色描述..."
            />
          </div>

          {/* Aliases */}
          <div>
            <Label className="mb-1.5 block">别名</Label>
            <div className="flex flex-wrap gap-1.5">
              {character.aliases.map((alias, i) => (
                <Badge key={i} variant="secondary" className="gap-1.5 pr-1">
                  {alias}
                  <button
                    className="ml-0.5 rounded-full p-0.5 hover:bg-white/10 transition-colors"
                    onClick={() => {
                      const updated = character.aliases.filter((_, j) => j !== i);
                      updateStore(character.id, { aliases: updated });
                    }}
                  >
                    <X className="h-2.5 w-2.5" />
                  </button>
                </Badge>
              ))}
              <Button
                variant="ghost"
                size="sm"
                className="h-6 text-[11px]"
                onClick={() => {
                  const name = prompt('输入新别名:');
                  if (name) updateStore(character.id, { aliases: [...character.aliases, name] });
                }}
              >
                <Plus className="h-3 w-3 mr-1" /> 添加
              </Button>
            </div>
          </div>

          {/* Personality */}
          <div>
            <Label className="mb-1.5 block">性格特征</Label>
            <div className="flex flex-wrap gap-1.5">
              {character.personality.map((trait, i) => (
                <Badge key={i} variant="accent" className="gap-1.5 pr-1">
                  {trait}
                  <button
                    className="ml-0.5 rounded-full p-0.5 hover:bg-white/10 transition-colors"
                    onClick={() => {
                      const updated = character.personality.filter((_, j) => j !== i);
                      updateStore(character.id, { personality: updated });
                    }}
                  >
                    <X className="h-2.5 w-2.5" />
                  </button>
                </Badge>
              ))}
              <Button
                variant="ghost"
                size="sm"
                className="h-6 text-[11px]"
                onClick={() => {
                  const trait = prompt('输入性格特征:');
                  if (trait) updateStore(character.id, { personality: [...character.personality, trait] });
                }}
              >
                <Plus className="h-3 w-3 mr-1" /> 添加
              </Button>
            </div>
          </div>

          {/* Relationships */}
          <div>
            <Label className="mb-1.5 block">角色关系</Label>
            <div className="space-y-2">
              {character.relationships.map((rel, i) => (
                <div key={i} className="flex items-center gap-2">
                  <Input
                    className="flex-1 h-8 text-xs"
                    value={rel.target}
                    onChange={(e) => {
                      const updated: Relationship[] = [...character.relationships];
                      updated[i] = { ...updated[i], target: e.target.value };
                      updateStore(character.id, { relationships: updated });
                    }}
                    placeholder="角色名"
                  />
                  <Input
                    className="w-28 h-8 text-xs"
                    value={rel.relation}
                    onChange={(e) => {
                      const updated: Relationship[] = [...character.relationships];
                      updated[i] = { ...updated[i], relation: e.target.value };
                      updateStore(character.id, { relationships: updated });
                    }}
                    placeholder="关系"
                  />
                  <button
                    className="p-1 rounded-md hover:bg-red-500/10 text-muted-foreground hover:text-red-400 transition-colors shrink-0"
                    onClick={() => {
                      const updated = character.relationships.filter((_, j) => j !== i);
                      updateStore(character.id, { relationships: updated });
                    }}
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>
              ))}
              <Button
                variant="ghost"
                size="sm"
                className="text-[11px]"
                onClick={() => {
                  updateStore(character.id, {
                    relationships: [...character.relationships, { target: '', relation: '' }],
                  });
                }}
              >
                <Plus className="h-3 w-3 mr-1" /> 添加关系
              </Button>
            </div>
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={handleClose}>
              取消
            </Button>
            <Button variant="gradient" onClick={handleSave} disabled={saving}>
              <Save className="h-4 w-4" />
              {saving ? '保存中...' : '保存'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
