import { readFileSync } from "fs";
import { resolve } from "path";

const read = (name: string) => readFileSync(resolve(__dirname, name), "utf8");
const declarations = (css: string): Record<string, string> =>
  Object.fromEntries(Array.from(css.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g), match => [match[1], match[2].trim()]));
const primitives = declarations(read("tokens.css"));
const palettes = read("themes.css").split(':root[data-theme="dark"]');
const light = declarations(palettes[0]);
const dark = declarations(palettes[1]);

const resolveToken = (name: string, tokens: Record<string, string>, seen: string[] = []): string => {
  if (seen.includes(name)) throw new Error(`Cyclic token: ${name}`);
  if (!tokens[name]) throw new Error(`Missing token: ${name}`);
  return tokens[name].replace(/var\((--[\w-]+)\)/g, (_, dependency) => resolveToken(dependency, tokens, [...seen, name]));
};
const luminance = (hex: string) => {
  const rgb = hex.replace("#", "").match(/../g)!.map(value => parseInt(value, 16) / 255);
  return rgb.map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
    .reduce((total, value, index) => total + value * [0.2126, 0.7152, 0.0722][index], 0);
};
const contrast = (a: string, b: string) => {
  const [bright, dim] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (bright + 0.05) / (dim + 0.05);
};

describe("design system accessibility contract", () => {
  it("defines the same semantic contract for both themes, without unresolved references", () => {
    expect(Object.keys(dark).sort()).toEqual(Object.keys(light).sort());
    [light, dark].forEach(palette => {
      const tokens = { ...primitives, ...palette };
      Object.keys(palette).forEach(name => expect(() => resolveToken(name, tokens)).not.toThrow());
    });
  });

  it.each([['light', light], ['dark', dark]] as const)("keeps normal text contrast at least 4.5:1 in %s", (_, palette) => {
    const tokens = { ...primitives, ...palette };
    const color = (name: string) => resolveToken(`--frotto-${name}`, tokens);
    const pairs = [
      ['text-primary', 'background'], ['text-secondary', 'surface'],
      ['text-muted', 'surface-secondary'], ['primary-contrast', 'primary'],
      ['primary-text', 'surface'], ['primary-hover-contrast', 'primary-hover'],
      ...['success', 'warning', 'danger', 'info'].map(status => [status, 'surface-modal']),
    ];
    pairs.forEach(([foreground, background]) => {
      expect(contrast(color(foreground), color(background))).toBeGreaterThanOrEqual(4.5);
    });
  });

  it("keeps the pre-paint canvas colors synchronized with the theme tokens", () => {
    const html = read("../../public/index.html").toLowerCase();
    [light, dark].forEach(palette => {
      expect(html).toContain(resolveToken('--frotto-background', { ...primitives, ...palette }).toLowerCase());
    });
    expect(read('variables.css')).not.toMatch(/prefers-color-scheme/);
  });
});
