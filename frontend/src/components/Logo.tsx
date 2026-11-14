import {Maximize2} from 'lucide-react'
import {cn} from '@/lib/utils'

const sizes = {
  sm: {box: 'size-7 rounded-[7px]', icon: 'size-4'},
  lg: {box: 'size-11 rounded-[10px]', icon: 'size-[22px]'},
}

/** SplitIt! brand mark: two arrows splitting apart on the primary color. */
export function LogoMark({size = 'sm', className}: { size?: keyof typeof sizes; className?: string }) {
  return (
      <span
          className={cn(
              'inline-flex items-center justify-center bg-primary text-primary-foreground',
              sizes[size].box,
              className,
          )}
      >
      <Maximize2 className={sizes[size].icon} aria-hidden="true"/>
    </span>
  )
}
