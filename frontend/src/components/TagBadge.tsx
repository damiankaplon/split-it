interface TagBadgeProps {
  name: string
  /** `#rrggbb`; falls back to the neutral style when missing. */
  color?: string | null
}

export function TagBadge({name, color}: TagBadgeProps) {
  return (
      <span
          className="rounded-full border bg-background px-2 py-px text-xs"
          style={color ? {color, borderColor: `${color}66`, backgroundColor: `${color}1f`} : undefined}
      >
      {name}
    </span>
  )
}
