import {cn} from '@/lib/utils'

interface TagBadgeProps {
  name: string
  /** `#rrggbb`; falls back to the neutral style when missing. */
  color?: string | null
}

/**
 * The stored colour is data, so the chip derives its own contrast: a light tint, a soft edge, and ink mixed toward the
 * foreground (every colour of the backend palette clears 4.5:1 this way).
 */
export function TagBadge({name, color}: TagBadgeProps) {
  return (
    <span
      // One line always; a name longer than the space it gets is cut short, the full text stays in the tooltip
      title={name}
      className={cn(
        'inline-flex max-w-full min-w-0 items-center gap-1.5 rounded-full border px-2 py-px text-xs font-medium',
        !color && 'bg-muted text-muted-foreground',
      )}
      style={
        color
          ? {
              color: `color-mix(in oklab, ${color} 55%, var(--foreground))`,
              backgroundColor: `color-mix(in srgb, ${color} 14%, var(--card))`,
              borderColor: `color-mix(in srgb, ${color} 28%, transparent)`,
            }
          : undefined
      }
    >
      <span aria-hidden className="size-1.5 shrink-0 rounded-full" style={{backgroundColor: color ?? 'currentColor'}} />
      <span className="truncate">{name}</span>
    </span>
  )
}
