export type CharacterRoleType = 'PROTAGONIST' | 'ANTAGONIST' | 'SUPPORTING' | 'MINOR';

export interface Relationship {
  target: string;
  relation: string;
}

export interface Character {
  id: number;
  scriptId: number;
  canonicalName: string;
  aliases: string[];
  roleType: CharacterRoleType;
  gender?: string;
  ageRange?: string;
  description?: string;
  personality: string[];
  relationships: Relationship[];
  appearanceCount: number;
  firstAppearance?: number;
  resolved: boolean;
  mergedFrom?: number[];
}
