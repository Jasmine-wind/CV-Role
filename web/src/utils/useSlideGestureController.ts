export type PresentationGestureInput = {
  deltaY: number
  deltaX?: number
  deltaMode?: number
  currentIndex: number
  maxIndex: number
  now?: number
  viewportHeight?: number
  ctrlKey?: boolean
}

export type PresentationGestureDecision = {
  action: 'next' | 'previous' | 'none'
  preventDefault: boolean
}

type PresentationGestureControllerOptions = {
  threshold?: number
  quietPeriod?: number
  lineHeight?: number
  defaultViewportHeight?: number
}

const DOM_DELTA_LINE = 1
const DOM_DELTA_PAGE = 2

export const normalizeWheelDelta = (
  delta: number,
  deltaMode = 0,
  lineHeight = 16,
  viewportHeight = 800,
) => {
  if (!Number.isFinite(delta)) return 0
  if (deltaMode === DOM_DELTA_LINE) return delta * lineHeight
  if (deltaMode === DOM_DELTA_PAGE) return delta * viewportHeight
  return delta
}

/**
 * One controller for the Landing presentation. A completed transition keeps a
 * burst lock until the quiet period, so trackpad inertia is consumed rather
 * than queued into the next scene.
 */
export const createPresentationGestureController = ({
  threshold = 78,
  quietPeriod = 220,
  lineHeight = 16,
  defaultViewportHeight = 800,
}: PresentationGestureControllerOptions = {}) => {
  let accumulated = 0
  let direction = 0
  let burstLocked = false
  let transitioning = false
  let lastEventAt = Number.NEGATIVE_INFINITY

  const reset = () => {
    accumulated = 0
    direction = 0
    burstLocked = false
    transitioning = false
    lastEventAt = Number.NEGATIVE_INFINITY
  }

  const beginTransition = () => {
    if (transitioning) return false
    transitioning = true
    burstLocked = true
    accumulated = 0
    direction = 0
    return true
  }

  const finishTransition = () => {
    transitioning = false
    // The animation can outlast the hardware burst. Keep consuming until the
    // next quiet period has definitely separated a new gesture.
    burstLocked = true
  }

  const handle = (input: PresentationGestureInput): PresentationGestureDecision => {
    if (input.ctrlKey) return { action: 'none', preventDefault: false }

    const viewportHeight = input.viewportHeight || defaultViewportHeight
    const deltaY = normalizeWheelDelta(input.deltaY, input.deltaMode, lineHeight, viewportHeight)
    const deltaX = normalizeWheelDelta(input.deltaX ?? 0, input.deltaMode, lineHeight, viewportHeight)
    if (!deltaY || Math.abs(deltaX) > Math.abs(deltaY) * 1.15) {
      return { action: 'none', preventDefault: false }
    }

    const now = input.now ?? (typeof performance !== 'undefined' ? performance.now() : Date.now())
    if (now - lastEventAt > quietPeriod && !transitioning) {
      accumulated = 0
      direction = 0
      burstLocked = false
    }
    lastEventAt = now

    // Keep the scene still while its vertical transition is running and while
    // the same physical wheel burst is still producing inertia.
    if (transitioning || burstLocked) return { action: 'none', preventDefault: true }

    const stepDirection = Math.sign(deltaY)
    const atBoundary =
      (stepDirection < 0 && input.currentIndex <= 0) ||
      (stepDirection > 0 && input.currentIndex >= input.maxIndex)

    // There is no document behind the presentation, but returning false here
    // preserves the browser's native boundary behavior if the host changes.
    if (atBoundary) {
      accumulated = 0
      direction = 0
      burstLocked = false
      return { action: 'none', preventDefault: false }
    }

    if (stepDirection !== direction) {
      direction = stepDirection
      accumulated = deltaY
    } else {
      accumulated += deltaY
    }

    if (Math.abs(accumulated) < threshold) {
      return { action: 'none', preventDefault: true }
    }

    beginTransition()
    return {
      action: stepDirection > 0 ? 'next' : 'previous',
      preventDefault: true,
    }
  }

  return {
    handle,
    reset,
    beginTransition,
    finishTransition,
    isTransitioning: () => transitioning,
  }
}

// Kept as a compatibility export for the previous unit-test/import name.
export const createWheelGestureController = createPresentationGestureController
