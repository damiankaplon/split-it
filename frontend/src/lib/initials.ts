/** "damian.kaplon" -> "DK", "anna" -> "AN" */
export function initials(name: string): string {
  const parts = name.split(/[\s._-]+/).filter(Boolean)
  const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : name.slice(0, 2)
  return letters.toUpperCase()
}
