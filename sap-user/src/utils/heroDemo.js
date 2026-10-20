export const HERO_GREETING = 'Hello，中南林业科技大学软件协会欢迎你！'
export const DEMO_EFFECTS = ['blur', 'spring', 'ink', 'scan', 'prism']

// Random draws without replacement let all five effects appear before a repeat.
// Also avoid repeating across cycles. No assets, canvas loop or network work.
export function createDemoEffectPicker(random = Math.random) {
  let remaining = [], previous
  return () => {
    if (!remaining.length) remaining = [...DEMO_EFFECTS]
    const choices = remaining.filter(effect => effect !== previous)
    const index = Math.min(choices.length - 1, Math.max(0, Math.floor(random() * choices.length)))
    const chosen = choices[index]
    remaining.splice(remaining.indexOf(chosen), 1)
    previous = chosen
    return chosen
  }
}
