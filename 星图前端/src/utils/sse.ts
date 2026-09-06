export interface SseHandlers {
  onSession?: (sessionId: string) => void
  onDelta?: (content: string) => void
  onProgress?: (message: string) => void
  onDone?: (data: any) => void
  onError?: (payload: { code?: number; message?: string }) => void
}

/**
 * 以 POST 请求消费后端 text/event-stream 流式响应。
 * 后端事件协议：event: delta / progress / done / error，data 为单行 JSON。
 * 传入 signal 时可在组件卸载/切换会话时主动中止前端展示，后端仍会继续执行并落库。
 */
export async function streamPost(url: string, body: any, handlers: SseHandlers, signal?: AbortSignal): Promise<void> {
  const token = localStorage.getItem('token')
  let res: Response
  try {
    res = await fetch('/api' + url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(body),
      signal
    })
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') return
    throw e
  }

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  if (!res.body) {
    throw new Error('浏览器不支持流式响应')
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let currentEvent = 'message'

  const dispatch = (event: string, data: string) => {
    let payload: any
    try {
      payload = JSON.parse(data)
    } catch {
      return
    }
    if (event === 'session') {
      handlers.onSession?.(payload?.sessionId ?? '')
    } else if (event === 'delta') {
      handlers.onDelta?.(payload?.content ?? '')
    } else if (event === 'progress') {
      handlers.onProgress?.(payload?.message ?? '')
    } else if (event === 'done') {
      handlers.onDone?.(payload)
    } else if (event === 'error') {
      handlers.onError?.(payload)
    } else {
      handlers.onDone?.(payload)
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let idx: number
      while ((idx = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, idx).replace(/\r$/, '')
        buffer = buffer.slice(idx + 1)
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data) {
            dispatch(currentEvent, data)
            currentEvent = 'message'
          }
        }
      }
    }
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') return
    throw e
  }
}
