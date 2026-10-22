/**
 * Spread on an icon-font icon (Feather renders a Text glyph): without it TalkBack reads the glyph,
 * e.g. a button announced as ", Add goal". Pressables still get an explicit accessibilityLabel.
 */
export const decorative = { accessible: false, importantForAccessibility: 'no' } as const;
