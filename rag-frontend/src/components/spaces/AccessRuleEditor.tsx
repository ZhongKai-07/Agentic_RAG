import { useState } from 'react'
import { spacesApi } from '@/api/spaces'
import type { AccessRuleInput, AccessRuleResponse } from '@/api/types'

interface AccessRuleEditorProps {
  spaceId: string
  rules: AccessRuleResponse[]
  onUpdated: () => void
}

export function AccessRuleEditor({ spaceId, rules, onUpdated }: AccessRuleEditorProps) {
  const [newRules, setNewRules] = useState<AccessRuleInput[]>(
    rules.map((r) => ({
      targetType: r.targetType,
      targetValue: r.targetValue,
      docSecurityClearance: r.docSecurityClearance,
    }))
  )

  const addRule = () =>
    setNewRules([...newRules, { targetType: 'TEAM', targetValue: '', docSecurityClearance: 'ALL' }])

  const removeRule = (i: number) =>
    setNewRules(newRules.filter((_, idx) => idx !== i))

  const updateRule = (i: number, field: string, value: string) =>
    setNewRules(newRules.map((r, idx) => (idx === i ? { ...r, [field]: value } : r)))

  const save = async () => {
    await spacesApi.updateAccessRules(spaceId, newRules)
    onUpdated()
  }

  return (
    <div className="space-y-3">
      <h4 className="text-caption text-text-muted">Access Rules</h4>
      {newRules.map((rule, i) => (
        <div key={i} className="flex gap-2 items-center">
          <select
            value={rule.targetType}
            onChange={(e) => updateRule(i, 'targetType', e.target.value)}
            className="input-hand px-2 py-1 text-caption"
          >
            <option value="BU">BU</option>
            <option value="TEAM">TEAM</option>
            <option value="USER">USER</option>
          </select>
          <input
            value={rule.targetValue}
            onChange={(e) => updateRule(i, 'targetValue', e.target.value)}
            placeholder="Value"
            className="flex-1 input-hand px-2 py-1 text-caption"
          />
          <select
            value={rule.docSecurityClearance || 'ALL'}
            onChange={(e) => updateRule(i, 'docSecurityClearance', e.target.value)}
            className="input-hand px-2 py-1 text-caption"
          >
            <option value="ALL">ALL</option>
            <option value="MANAGEMENT">MANAGEMENT</option>
          </select>
          <button onClick={() => removeRule(i)}
                  className="text-status-failed text-caption">×</button>
        </div>
      ))}
      <div className="flex gap-2">
        <button onClick={addRule}
                className="text-accent-blue text-caption hover:underline">+ Add Rule</button>
        <button onClick={save}
                className="bg-accent-blue text-white px-3 py-1 btn-hand text-caption
                           font-heading font-800">Save</button>
      </div>
    </div>
  )
}
