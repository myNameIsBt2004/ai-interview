"use client";

import React from "react";

type Ability = { name?: string; score?: number };

/** 简易五维雷达图 */
export default function AbilityRadar({
  abilities,
  size = 260,
}: {
  abilities: Ability[];
  size?: number;
}) {
  const items = (abilities.length ? abilities : []).slice(0, 5);
  while (items.length < 5) {
    items.push({ name: `维度${items.length + 1}`, score: 0 });
  }

  const cx = size / 2;
  const cy = size / 2;
  const maxR = size * 0.34;
  const levels = [0.2, 0.4, 0.6, 0.8, 1];

  const pointAt = (index: number, ratio: number) => {
    const angle = -Math.PI / 2 + (index * 2 * Math.PI) / items.length;
    return {
      x: cx + maxR * ratio * Math.cos(angle),
      y: cy + maxR * ratio * Math.sin(angle),
    };
  };

  const gridPolys = levels.map((lv) =>
    items
      .map((_, i) => {
        const p = pointAt(i, lv);
        return `${p.x},${p.y}`;
      })
      .join(" "),
  );

  const dataPoly = items
    .map((a, i) => {
      const ratio = Math.max(0, Math.min(10, a.score || 0)) / 10;
      const p = pointAt(i, ratio);
      return `${p.x},${p.y}`;
    })
    .join(" ");

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="ability-radar">
      {gridPolys.map((pts, i) => (
        <polygon
          key={i}
          points={pts}
          fill="none"
          stroke="#e8eaef"
          strokeWidth={1}
        />
      ))}
      {items.map((_, i) => {
        const p = pointAt(i, 1);
        return (
          <line
            key={`axis-${i}`}
            x1={cx}
            y1={cy}
            x2={p.x}
            y2={p.y}
            stroke="#e8eaef"
            strokeWidth={1}
          />
        );
      })}
      <polygon
        points={dataPoly}
        fill="rgba(245, 197, 24, 0.35)"
        stroke="#f5c518"
        strokeWidth={2}
      />
      {items.map((a, i) => {
        const ratio = Math.max(0, Math.min(10, a.score || 0)) / 10;
        const p = pointAt(i, ratio);
        const label = pointAt(i, 1.22);
        return (
          <g key={`pt-${i}`}>
            <circle cx={p.x} cy={p.y} r={3.5} fill="#f5c518" />
            <text
              x={label.x}
              y={label.y}
              textAnchor="middle"
              dominantBaseline="middle"
              fontSize={11}
              fill="#595959"
            >
              {a.name}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
