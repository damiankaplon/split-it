import {cn} from '@/lib/utils'

const sizes = {
  sm: 'size-8',
  lg: 'size-14',
}

/**
 * SplitIt! brand mark: two arrows splitting apart. The glyph is unchanged; the colours are fixed brand colours
 * (an Ion tile, a Volt arrow and a white one) and deliberately the same in every theme.
 */
export function LogoMark({size = 'sm', className}: {size?: keyof typeof sizes; className?: string}) {
  return (
    <svg viewBox="0 0 64 64" fill="none" aria-hidden="true" className={cn('shrink-0', sizes[size], className)}>
      <rect width="64" height="64" rx="18" fill="#2457FF" />
      <g transform="translate(14 14) scale(1.5)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <g stroke="#D6FF4D">
          <path d="M15 3h6v6" />
          <path d="M21 3l-7 7" />
        </g>
        <g stroke="#FFFFFF">
          <path d="M9 21H3v-6" />
          <path d="M3 21l7-7" />
        </g>
      </g>
    </svg>
  )
}

/** "SplitIt!" in the display face; the exclamation mark is the only place the app allows one. */
export function Wordmark({className}: {className?: string}) {
  return (
    <span className={cn('font-display text-[18px] leading-none font-semibold tracking-[-0.03em]', className)}>
      SplitIt<span className="text-accent-foreground">!</span>
    </span>
  )
}
