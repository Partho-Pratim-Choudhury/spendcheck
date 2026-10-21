export const colors = {
  bg: '#F4F6F8',
  surface: '#FFFFFF',
  ink: '#111827',
  muted: '#5B6472',
  border: '#E3E7EC',
  primary: '#2457D6',
  primaryPressed: '#1A43A8',
  heroBg: '#16244A',
  onPrimary: '#FFFFFF',
  verdict: {
    green: { text: '#0B6B45', bg: '#E3F4EC' },
    amber: { text: '#8A4B00', bg: '#FFF1DC' },
    red: { text: '#A21C26', bg: '#FDE7E8' },
  },
} as const;

export const radii = { sm: 12, md: 16, lg: 20 } as const;

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

export const theme = { colors, radii, spacing, typography, tabBar } as const;
export type Theme = typeof theme;
