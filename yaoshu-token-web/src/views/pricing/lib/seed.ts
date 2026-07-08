// 用于 mock 数据生成，确保同一模型每次渲染产生相同数据。

/** djb2 字符串哈希 → 非负 31 位整数 */
export function hashStringToSeed(input: string): number {
  let hash = 5381
  for (let i = 0; i < input.length; i++) {
    hash = (hash * 33) ^ input.charCodeAt(i)
  }
  return Math.abs(hash | 0)
}

/** 线性同余生成器，产生 [0, 1) 范围内的伪随机数 */
export function seededRandom(seed: number): () => number {
  let state = (seed || 1) >>> 0
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0
    return state / 0x1_0000_0000
  }
}

/** 从 seeded PRNG 取 [min, max] 范围内的数 */
export function randomInRange(
  rand: () => number,
  min: number,
  max: number
): number {
  return min + rand() * (max - min)
}

/** 从 seeded PRNG 取 [min, max]（含）范围内的整数 */
export function randomIntInRange(
  rand: () => number,
  min: number,
  max: number
): number {
  return Math.floor(randomInRange(rand, min, max + 1))
}
