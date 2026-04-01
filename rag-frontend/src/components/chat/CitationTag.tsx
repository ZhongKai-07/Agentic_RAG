interface CitationTagProps {
  index: number
  onClick: (index: number) => void
}

export function CitationTag({ index, onClick }: CitationTagProps) {
  return (
    <button
      onClick={() => onClick(index)}
      className="inline-flex items-center justify-center w-5 h-5
                 bg-accent-blue/20 text-accent-blue wobbly-border text-[11px]
                 border border-text-primary hover:bg-accent-blue/30 hover:rotate-3 transition-transform duration-150
                 align-super mx-0.5"
    >
      {index}
    </button>
  )
}
