import {X} from 'lucide-react'
import {useTranslation} from 'react-i18next'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {type DateRange, EMPTY_DATE_RANGE, isDateRangeSet} from '@/lib/dateRange'

export function DateRangeFilter({value, onChange}: { value: DateRange; onChange: (range: DateRange) => void }) {
  const {t} = useTranslation()
  return (
      <div className="flex max-w-sm items-center gap-2">
        <Input
            type="date"
            aria-label={t('project.dateFrom')}
            className="h-10 rounded-lg bg-card"
            value={value.from}
            max={value.to || undefined}
            onChange={(e) => onChange({...value, from: e.target.value})}
        />
        <span aria-hidden className="text-muted-foreground">–</span>
        <Input
            type="date"
            aria-label={t('project.dateTo')}
            className="h-10 rounded-lg bg-card"
            value={value.to}
            min={value.from || undefined}
            onChange={(e) => onChange({...value, to: e.target.value})}
        />
        {isDateRangeSet(value) && (
            <Button variant="ghost" size="icon-sm" className="shrink-0 rounded-lg text-muted-foreground"
                    aria-label={t('project.clearDates')} onClick={() => onChange(EMPTY_DATE_RANGE)}>
              <X/>
            </Button>
        )}
      </div>
  )
}
