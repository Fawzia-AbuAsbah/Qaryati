import { SourceError } from './sourceContracts.js'

// Mutations never cancel-and-replace an in-flight submission or retry it.
export function createSourceMutation(isCurrent) {
  let current = null
  return {
    run(load, { onSuccess, onError, onSettled } = {}) {
      if (current) throw new SourceError('MUTATION_IN_PROGRESS')
      const controller = new AbortController()
      const task = { cancel() { if (current === task) current = null; controller.abort() } }
      current = task
      const live = () => current === task && !controller.signal.aborted && isCurrent()
      task.done = Promise.resolve().then(() => {
        controller.signal.throwIfAborted()
        if (!isCurrent()) throw new SourceError('AUTH_REQUIRED')
        return load(controller.signal)
      }).then(value => { if (live()) onSuccess?.(value) }, error => { if (live()) onError?.(error) })
        .finally(() => { const notify = live(); if (current === task) current = null; if (notify) onSettled?.() })
      return task
    },
    cancel() { current?.cancel() },
  }
}
