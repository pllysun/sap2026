// Page-local cache: never persists an account's submissions across workspaces.
export function createWorkspaceCache(maxEntries = 24) {
  const values = new Map(), pending = new Map()
  function remember(key, value) {
    values.delete(key)
    values.set(key, value)
    while (values.size > maxEntries) values.delete(values.keys().next().value)
  }
  return {
    async load(key, loader, force = false) {
      if (pending.has(key)) return pending.get(key)
      if (!force && values.has(key)) {
        const value = values.get(key)
        remember(key, value)
        return value
      }
      const task = Promise.resolve().then(loader).then(value => {
        if (pending.get(key) === task) remember(key, value)
        return value
      }).finally(() => { if (pending.get(key) === task) pending.delete(key) })
      pending.set(key, task)
      return task
    },
    remember,
    invalidate(prefix = '') {
      for (const key of values.keys()) if (key.startsWith(prefix)) values.delete(key)
      for (const key of pending.keys()) if (key.startsWith(prefix)) pending.delete(key)
    },
  }
}
