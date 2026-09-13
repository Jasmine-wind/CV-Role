let generation = 0

export const getAuthSessionGeneration = () => generation

export const advanceAuthSessionGeneration = () => {
  generation += 1
  return generation
}
