import { describe, expect, it } from 'vitest'
import { createWheelGestureController, normalizeWheelDelta } from '../useSlideGestureController'

const input = (deltaY: number, now: number, currentIndex = 1, deltaX = 0) => ({
  deltaY,
  deltaX,
  currentIndex,
  maxIndex: 7,
  now,
})

describe('useSlideGestureController', () => {
  it('normalizes line and page wheel deltas', () => {
    expect(normalizeWheelDelta(3, 1)).toBe(48)
    expect(normalizeWheelDelta(1, 2, 16, 720)).toBe(720)
  })

  it('moves once for one wheel burst', () => {
    const controller = createWheelGestureController({ threshold: 60 })

    expect(controller.handle(input(22, 0)).action).toBe('none')
    expect(controller.handle(input(22, 30)).action).toBe('none')
    expect(controller.handle(input(22, 60)).action).toBe('next')
    expect(controller.handle(input(80, 90)).action).toBe('none')
    expect(controller.handle(input(80, 150)).action).toBe('none')
  })

  it('keeps the animation locked, then allows the next gesture after silence', () => {
    const controller = createWheelGestureController({ threshold: 60, quietPeriod: 200 })

    expect(controller.handle(input(60, 0)).action).toBe('next')
    controller.finishTransition()
    expect(controller.handle(input(60, 100)).action).toBe('none')
    expect(controller.handle(input(60, 301)).action).toBe('next')
  })

  it('resets the accumulated distance when direction reverses', () => {
    const controller = createWheelGestureController({ threshold: 60 })

    expect(controller.handle(input(40, 0)).action).toBe('none')
    expect(controller.handle(input(-40, 40)).action).toBe('none')
    expect(controller.handle(input(-20, 80)).action).toBe('previous')
  })

  it('does not handle ctrl-wheel, horizontal scrolling, or boundary gestures', () => {
    const controller = createWheelGestureController({ threshold: 60 })

    expect(controller.handle({ ...input(100, 0), ctrlKey: true })).toEqual({
      action: 'none',
      preventDefault: false,
    })
    expect(controller.handle(input(100, 0, 1, 130))).toEqual({
      action: 'none',
      preventDefault: false,
    })
    expect(controller.handle(input(-100, 20, 0))).toEqual({
      action: 'none',
      preventDefault: false,
    })
    expect(controller.handle(input(100, 40, 7))).toEqual({
      action: 'none',
      preventDefault: false,
    })
  })

  it('consumes inertia while entering the final stage', () => {
    const controller = createWheelGestureController({ threshold: 60 })

    expect(controller.handle(input(60, 0, 3)).action).toBe('next')
    expect(controller.handle(input(100, 50, 4))).toEqual({
      action: 'none',
      preventDefault: true,
    })
  })
})
