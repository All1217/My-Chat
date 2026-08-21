/**
 * 完成通知音效。需用户手势解锁 AudioContext，否则浏览器会静音。
 * 双音门铃连响三遍（约 2s），避免单次短促 beep 被忽略。
 */

let audioCtx: AudioContext | null = null
let unlocked = false

export function unlockNotifySound() {
  if (unlocked) return
  try {
    const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    if (!Ctx) return
    audioCtx = audioCtx ?? new Ctx()
    void audioCtx.resume()
    unlocked = true
  } catch {
    /* 忽略 */
  }
}

function scheduleTone(ctx: AudioContext, start: number, freq: number, duration: number) {
  const osc = ctx.createOscillator()
  const gain = ctx.createGain()
  osc.type = 'sine'
  osc.frequency.value = freq
  // 避免 gain=0 时 exponentialRamp 报错
  gain.gain.setValueAtTime(0.0001, start)
  gain.gain.exponentialRampToValueAtTime(0.12, start + 0.03)
  gain.gain.exponentialRampToValueAtTime(0.001, start + duration)
  osc.connect(gain)
  gain.connect(ctx.destination)
  osc.start(start)
  osc.stop(start + duration + 0.02)
}

export function playNotifySound() {
  try {
    if (!audioCtx) return
    void audioCtx.resume()
    if (audioCtx.state !== 'running') return

    const now = audioCtx.currentTime
    const low = 880
    const high = 1175
    const note = 0.22
    const gap = 0.08
    const cycle = 0.62
    const repeats = 3

    for (let i = 0; i < repeats; i++) {
      const t = now + i * cycle
      scheduleTone(audioCtx, t, low, note)
      scheduleTone(audioCtx, t + note + gap, high, note + 0.06)
    }
  } catch {
    /* 自动播放策略：弹窗仍要出现 */
  }
}
