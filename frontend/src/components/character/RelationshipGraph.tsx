'use client';

import { useCallback, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  Position,
  type Node,
  type Edge,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useScriptStore } from '@/stores/script-store';
import { roleLabel } from '@/lib/utils';
import type { Character } from '@/types/character';

// ---- Relationship color mapping ----
const RELATION_COLORS: Record<string, { stroke: string; glow: string }> = {
  家人:    { stroke: 'oklch(0.65 0.18 40 / 0.8)',  glow: 'oklch(0.65 0.18 40 / 0.3)' },   // warm orange
  亲戚:    { stroke: 'oklch(0.65 0.18 40 / 0.7)',  glow: 'oklch(0.65 0.18 40 / 0.25)' },
  恋人:    { stroke: 'oklch(0.58 0.2 0 / 0.8)',    glow: 'oklch(0.58 0.2 0 / 0.3)' },     // rose/pink
  情侣:    { stroke: 'oklch(0.58 0.2 0 / 0.8)',    glow: 'oklch(0.58 0.2 0 / 0.3)' },
  夫妻:    { stroke: 'oklch(0.58 0.2 0 / 0.85)',   glow: 'oklch(0.58 0.2 0 / 0.35)' },
  朋友:    { stroke: 'oklch(0.65 0.14 140 / 0.8)',  glow: 'oklch(0.65 0.14 140 / 0.3)' },  // green
  好友:    { stroke: 'oklch(0.65 0.14 140 / 0.85)', glow: 'oklch(0.65 0.14 140 / 0.35)' },
  盟友:    { stroke: 'oklch(0.65 0.14 160 / 0.8)',  glow: 'oklch(0.65 0.14 160 / 0.3)' },  // teal
  敌人:    { stroke: 'oklch(0.55 0.2 20 / 0.85)',   glow: 'oklch(0.55 0.2 20 / 0.35)' },   // red
  对手:    { stroke: 'oklch(0.55 0.2 20 / 0.75)',   glow: 'oklch(0.55 0.2 20 / 0.3)' },
  师徒:    { stroke: 'oklch(0.62 0.15 260 / 0.8)',  glow: 'oklch(0.62 0.15 260 / 0.3)' },  // blue-purple
  师傅:    { stroke: 'oklch(0.62 0.15 260 / 0.85)', glow: 'oklch(0.62 0.15 260 / 0.35)' },
  徒弟:    { stroke: 'oklch(0.62 0.15 260 / 0.75)', glow: 'oklch(0.62 0.15 260 / 0.3)' },
  师生:    { stroke: 'oklch(0.62 0.15 260 / 0.8)',  glow: 'oklch(0.62 0.15 260 / 0.3)' },
  上下级:  { stroke: 'oklch(0.55 0.08 270 / 0.8)',  glow: 'oklch(0.55 0.08 270 / 0.25)' }, // gray-blue
  同事:    { stroke: 'oklch(0.55 0.08 270 / 0.75)', glow: 'oklch(0.55 0.08 270 / 0.25)' },
  相识:    { stroke: 'oklch(0.5 0.03 280 / 0.6)',   glow: 'oklch(0.5 0.03 280 / 0.2)' },   // muted gray
  邻居:    { stroke: 'oklch(0.5 0.03 280 / 0.6)',   glow: 'oklch(0.5 0.03 280 / 0.2)' },
};

function getRelationColor(relation: string): { stroke: string; glow: string } {
  // Try exact match first
  if (RELATION_COLORS[relation]) return RELATION_COLORS[relation];
  // Try partial match
  for (const [key, color] of Object.entries(RELATION_COLORS)) {
    if (relation.includes(key) || key.includes(relation)) return color;
  }
  // Default teal
  return { stroke: 'oklch(0.72 0.14 185 / 0.6)', glow: 'oklch(0.72 0.14 185 / 0.2)' };
}

// ---- Layout helpers ----
function buildLayout(characters: Character[]) {
  const nodes: Node[] = [];
  const edges: Edge[] = [];
  const edgeSet = new Set<string>();

  // Position characters in a circle
  const centerX = 400;
  const centerY = 300;
  const radius = Math.min(240, characters.length * 60);
  const angleStep = (2 * Math.PI) / Math.max(1, characters.length);

  characters.forEach((char, i) => {
    const angle = i * angleStep - Math.PI / 2;
    const x = centerX + radius * Math.cos(angle);
    const y = centerY + radius * Math.sin(angle);

    const isProtagonist = char.roleType === 'PROTAGONIST';
    const isAntagonist = char.roleType === 'ANTAGONIST';

    nodes.push({
      id: String(char.id),
      type: 'default',
      position: { x, y },
      data: {
        label: char.canonicalName,
        aliases: char.aliases?.join(', ') || '',
        role: roleLabel(char.roleType),
        appearanceCount: char.appearanceCount,
        gender: char.gender,
        isProtagonist,
        isAntagonist,
      },
      style: {
        background: isProtagonist
          ? 'oklch(0.72 0.14 185 / 0.15)'
          : isAntagonist
          ? 'oklch(0.55 0.2 20 / 0.15)'
          : 'oklch(0.9 0.005 250 / 0.08)',
        border: isProtagonist
          ? '2px solid oklch(0.72 0.14 185 / 0.6)'
          : isAntagonist
          ? '2px solid oklch(0.55 0.2 20 / 0.6)'
          : '1px solid oklch(0.9 0.005 250 / 0.15)',
        borderRadius: '12px',
        padding: '10px 16px',
        color: 'oklch(0.95 0.005 250)',
        fontSize: '13px',
        fontWeight: 600,
        width: 'auto',
        minWidth: 120,
      },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
    });
  });

  // Build edges from relationships
  characters.forEach((char) => {
    char.relationships?.forEach((rel) => {
      const target = characters.find(
        (c) => c.canonicalName === rel.target || c.aliases?.includes(rel.target)
      );
      if (target) {
        const edgeKey = [char.id, target.id].sort().join('-');
        if (!edgeSet.has(edgeKey)) {
          edgeSet.add(edgeKey);
          const colors = getRelationColor(rel.relation);
          edges.push({
            id: `e${char.id}-${target.id}`,
            source: String(char.id),
            target: String(target.id),
            label: rel.relation,
            style: {
              stroke: colors.stroke,
              strokeWidth: 2,
            },
            labelStyle: {
              fill: 'oklch(0.95 0.005 250)',
              fontSize: 11,
              fontWeight: 600,
            },
            labelBgStyle: {
              fill: 'oklch(0.15 0.01 250 / 0.9)',
              rx: 4,
              ry: 4,
            },
            labelBgPadding: [6, 3] as [number, number],
            labelBgBorderRadius: 4,
            animated: true,
          });
        }
      }
    });
  });

  return { nodes, edges };
}

// ---- Custom node ----
interface CharacterNodeData {
  label: string;
  aliases: string;
  role: string;
  appearanceCount: number;
  gender?: string;
  isProtagonist?: boolean;
  isAntagonist?: boolean;
}

function CharacterNode({ data }: { data: CharacterNodeData }) {
  const genderLabel = data.gender === 'MALE' ? '♂' : data.gender === 'FEMALE' ? '♀' : '';
  return (
    <div className="relative">
      <div className="text-xs opacity-60 mb-0.5">{data.role}</div>
      <div className="font-semibold text-sm">
        {data.label}
        {genderLabel && (
          <span className={`ml-1 text-xs ${data.gender === 'MALE' ? 'text-blue-400' : 'text-pink-400'}`}>
            {genderLabel}
          </span>
        )}
      </div>
      {data.aliases && (
        <div className="text-[10px] opacity-40 mt-0.5">{data.aliases}</div>
      )}
      <div className="text-[10px] opacity-50 mt-1">
        出场 {data.appearanceCount} 次
      </div>
    </div>
  );
}

const nodeTypes = { default: CharacterNode };

// ---- Component ----
export function RelationshipGraph() {
  const { script, selectCharacter } = useScriptStore();
  const characters = script?.characters ?? [];

  const { nodes: initialNodes, edges: initialEdges } = useMemo(
    () => buildLayout(characters),
    [characters]
  );

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  const onNodeClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      selectCharacter(Number(node.id));
    },
    [selectCharacter]
  );

  if (characters.length === 0) return null;

  return (
    <div className="rounded-xl border border-white/5 bg-white/[0.02] overflow-hidden">
      <div className="px-4 py-3 border-b border-white/5">
        <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
          角色关系图谱
        </h3>
      </div>
      <div style={{ height: 500, width: '100%' }}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={onNodeClick}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.3 }}
          minZoom={0.3}
          maxZoom={2}
          defaultViewport={{ x: 0, y: 0, zoom: 1 }}
          proOptions={{ hideAttribution: true }}
        >
          <Background
            color="oklch(1 0 0 / 0.06)"
            gap={20}
            size={0.5}
          />
          <Controls
            className="!bg-oklch(0.17 0.012 250 / 0.9) !border !border-white/10 !rounded-lg"
            style={{
              // @ts-expect-error custom css vars
              '--xy-controls-button-background-color': 'oklch(0.22 0.012 255 / 0.8)',
              '--xy-controls-button-background-color-hover': 'oklch(0.28 0.012 255 / 0.8)',
              '--xy-controls-button-color': 'oklch(0.9 0.005 250)',
              '--xy-controls-button-color-hover': 'oklch(0.95 0.005 250)',
              '--xy-controls-button-border-color': 'oklch(1 0 0 / 0.1)',
            }}
          />
          <MiniMap
            className="!bg-oklch(0.17 0.012 250 / 0.9) !border !border-white/10 !rounded-lg"
            maskColor="oklch(0.13 0.015 250 / 0.7)"
            nodeColor={(node) => {
              const d = node.data as unknown as CharacterNodeData | undefined;
              return d?.isProtagonist
                ? 'oklch(0.72 0.14 185)'
                : d?.isAntagonist
                ? 'oklch(0.55 0.2 20)'
                : 'oklch(0.62 0.02 250)';
            }}
            pannable
            zoomable
          />
        </ReactFlow>
      </div>
    </div>
  );
}
