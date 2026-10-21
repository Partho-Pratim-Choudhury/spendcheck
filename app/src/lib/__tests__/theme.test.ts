import { describe, expect, it } from '@jest/globals';

import { categoryColor, categoryColors, chartCycle } from '../../theme';

describe('categoryColor', () => {
  it('uses the fixed tint for known categories, ignoring case and spaces', () => {
    expect(categoryColor('Food')).toBe(categoryColors.food);
    expect(categoryColor('  shopping ')).toBe(categoryColors.shopping);
  });

  it('gives a custom category the same colour on every call and normalised spelling', () => {
    const c = categoryColor('Pet care');
    expect(chartCycle).toContain(c);
    expect(categoryColor('pet care')).toBe(c);
    expect(categoryColor(' PET CARE ')).toBe(c);
  });

  it('spreads different custom names across the palette', () => {
    const names = ['Pet care', 'Gym', 'Gifts', 'Kids', 'Books', 'Coffee', 'Parking', 'Donations'];
    expect(new Set(names.map(categoryColor)).size).toBeGreaterThan(1);
  });
});
