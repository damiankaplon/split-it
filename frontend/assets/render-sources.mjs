// Renders the app icon and splash sources in this folder from the SplitIt! mark (src/components/Logo.tsx).
// `npm run assets` turns them into the Android and iOS icons and splash screens.
// Run again after changing the mark: node assets/render-sources.mjs
import path from 'node:path'
import sharp from 'sharp'

const ION = '#2457FF'
const BACKGROUND = '#F2F5FB' // --background in src/index.css
const ICON_SIZE = 1024
const SPLASH_SIZE = 2732

// The two arrows, drawn on a 24x24 grid
const ARROWS = `
  <g stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none">
    <g stroke="#D6FF4D"><path d="M15 3h6v6"/><path d="M21 3l-7 7"/></g>
    <g stroke="#FFFFFF"><path d="M9 21H3v-6"/><path d="M3 21l7-7"/></g>
  </g>`

/** An SVG drawn on a `grid` x `grid` canvas, rendered at `size` pixels. */
const svg = (size, grid, body) =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${grid} ${grid}">${body}</svg>`

const sources = {
  // iOS and legacy Android: a full-bleed tile, the platform rounds the corners
  'icon-only.png': svg(
    ICON_SIZE,
    64,
    `<rect width="64" height="64" fill="${ION}"/><g transform="translate(14 14) scale(1.5)">${ARROWS}</g>`,
  ),
  // Android adaptive icon layers. capacitor-assets insets them to the part of the icon the launcher shows,
  // so they are drawn like the visible tile.
  'icon-foreground.png': svg(ICON_SIZE, 64, `<g transform="translate(14 14) scale(1.5)">${ARROWS}</g>`),
  'icon-background.png': svg(ICON_SIZE, 64, `<rect width="64" height="64" fill="${ION}"/>`),
  // The rounded mark in the middle of the app background; phones crop the sides
  'splash.png': svg(
    SPLASH_SIZE,
    SPLASH_SIZE,
    `<rect width="${SPLASH_SIZE}" height="${SPLASH_SIZE}" fill="${BACKGROUND}"/>
     <g transform="translate(1166 1166) scale(6.25)">
       <rect width="64" height="64" rx="18" fill="${ION}"/>
       <g transform="translate(14 14) scale(1.5)">${ARROWS}</g>
     </g>`,
  ),
}

for (const [file, source] of Object.entries(sources)) {
  await sharp(Buffer.from(source))
    .png()
    .toFile(path.join(import.meta.dirname, file))
  console.log(`assets/${file}`)
}
