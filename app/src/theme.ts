export const colors = {
  bg: '#F4F6F8',
  surface: '#FFFFFF',
  ink: '#111827',
  muted: '#5B6472',
  border: '#E3E7EC',
  primary: '#2457D6',
  primaryPressed: '#1A43A8',
  heroBg: '#16244A',
  heroMuted: '#C6D2F2',
  heroAccent: '#8FE3BC',
  heroBar: '#8FB2FF',
  heroTrack: 'rgba(255,255,255,0.16)',
  onPrimary: '#FFFFFF',
  primarySoft: '#E8EEFB',
  track: '#E9EDF2',
  amberBar: '#C77700',
  redBar: '#C7222C',
  verdict: {
    green: { text: '#0B6B45', bg: '#E3F4EC' },
    amber: { text: '#8A4B00', bg: '#FFF1DC' },
    red: { text: '#A21C26', bg: '#FDE7E8' },
  },
} as const;

/** Report donut / avatar tints by category name; unknown names get a stable `chartCycle` colour from a name hash. */
export const categoryColors: Record<string, string> = {
  food: '#2457D6',
  shopping: '#DB2777',
  groceries: '#0E9F6E',
  bills: '#64748B',
  travel: '#E08A00',
  fun: '#8B5CF6',
  misc: '#14B8A6',
  health: '#B6BEC9',
};
export const chartCycle = ['#2457D6', '#DB2777', '#0E9F6E', '#64748B', '#E08A00', '#8B5CF6', '#14B8A6', '#B6BEC9'];

/** Same name -> same colour everywhere (chat, pickers, reports), regardless of list position. */
export function categoryColor(name: string): string {
  const key = name.trim().toLowerCase();
  const known = categoryColors[key];
  if (known) return known;
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) >>> 0;
  return chartCycle[h % chartCycle.length];
}

export const radii = { sm: 12, md: 16, lg: 18 } as const;

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 } as const;

export const typography = {
  title: { fontSize: 28, fontWeight: '700' as const, color: colors.ink },
  heading: { fontSize: 20, fontWeight: '600' as const, color: colors.ink },
  body: { fontSize: 16, color: colors.ink },
  label: { fontSize: 14, fontWeight: '500' as const, color: colors.ink },
  caption: { fontSize: 12, color: colors.muted },
  /** Use for money so digits line up in lists. */
  amount: { fontVariant: ['tabular-nums' as const] },
};

export const tabBar = {
  height: 64,
  background: colors.surface,
  activeTint: colors.primary,
  inactiveTint: colors.muted,
} as const;

export const theme = { colors, radii, spacing, typography, tabBar, categoryColors } as const;
export type Theme = typeof theme;
