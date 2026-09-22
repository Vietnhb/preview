export function ambiguityQuestionPrefix(code: string): string {
  return `ambiguity-question:${encodeURIComponent(code)}:`;
}

export function retainAmbiguityAnswer(
  answers: Record<string, string>,
  code: string,
  answer: string,
): Record<string, string> {
  return { ...answers, [code]: answer.trim() };
}
