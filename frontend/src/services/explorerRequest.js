// Also guards transports that complete after abort instead of respecting signal.
export function createLatestExplorerRequest() {
  let current
  return {
    run(load, { onSuccess, onError }) {
      current?.cancel()
      const controller = new AbortController()
      const task = {
        cancel() {
          if (current === task) current = undefined
          controller.abort()
        },
      }
      current = task
      task.done = Promise.resolve().then(() => {
        controller.signal.throwIfAborted()
        return load(controller.signal)
      }).then(data => {
        if (current === task && !controller.signal.aborted) onSuccess(data)
      }, error => {
        if (current === task && !controller.signal.aborted) onError(error)
      })
      return task
    },
  }
}
