import { useEffect } from 'react'
import { useSpaceStore } from '@/stores/useSpaceStore'
import { spacesApi } from '@/api/spaces'
import * as Select from '@radix-ui/react-select'
import { ChevronDown, Check, Layers } from 'lucide-react'
import { WOBBLY_RADIUS } from '@/lib/utils'

export function SpaceSelector() {
  const { spaces, currentSpaceId, setSpaces, setCurrentSpaceId } = useSpaceStore()

  useEffect(() => {
    spacesApi.list()
      .then((list) => {
        console.log('[SpaceSelector] loaded spaces:', list)
        setSpaces(list)
        if (!currentSpaceId && list.length > 0) {
          setCurrentSpaceId(list[0].spaceId)
        }
      })
      .catch((err) => console.error('[SpaceSelector] load failed:', err))
  }, [])

  const currentSpace = spaces.find((s) => s.spaceId === currentSpaceId)

  return (
    <Select.Root value={currentSpaceId || ''} onValueChange={setCurrentSpaceId}>
      <Select.Trigger
        className="inline-flex items-center gap-2 px-3 py-1.5
                   bg-white text-text-primary border-2 border-text-primary
                   hover:border-accent-blue/50 focus:border-accent-blue focus:outline-none
                   transition-colors text-caption min-w-[200px] max-w-[300px] shadow-hard-sm"
        style={{ borderRadius: WOBBLY_RADIUS }}
        aria-label="Select knowledge space"
      >
        <Layers className="h-3.5 w-3.5 text-accent-purple shrink-0" strokeWidth={2.5} />
        <Select.Value placeholder="Select space">
          <span className="truncate">{currentSpace?.name || 'Select space'}</span>
        </Select.Value>
        <Select.Icon className="ml-auto shrink-0">
          <ChevronDown className="h-3.5 w-3.5 text-text-muted" strokeWidth={2.5} />
        </Select.Icon>
      </Select.Trigger>

      <Select.Portal>
        <Select.Content
          className="overflow-hidden border-2 border-text-primary
                     bg-white shadow-hard z-50
                     min-w-[var(--radix-select-trigger-width)]
                     animate-in fade-in-0 zoom-in-95"
          style={{ borderRadius: WOBBLY_RADIUS }}
          position="popper"
          sideOffset={4}
        >
          <Select.Viewport className="p-1">
            {spaces.map((s) => (
              <Select.Item
                key={s.spaceId}
                value={s.spaceId}
                className="relative flex items-center gap-2 wobbly-border-md px-3 py-2
                           text-caption text-text-primary cursor-pointer
                           outline-none select-none
                           data-[highlighted]:bg-bg-tertiary
                           data-[highlighted]:text-accent-blue"
              >
                <Select.ItemIndicator className="shrink-0">
                  <Check className="h-3.5 w-3.5 text-accent-blue" strokeWidth={2.5} />
                </Select.ItemIndicator>
                <Select.ItemText>{s.name}</Select.ItemText>
              </Select.Item>
            ))}
          </Select.Viewport>
        </Select.Content>
      </Select.Portal>
    </Select.Root>
  )
}
