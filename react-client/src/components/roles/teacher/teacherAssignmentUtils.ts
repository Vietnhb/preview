

export function questionPrompt(questions: unknown) {
  if (
    typeof questions === "object" &&
    questions !== null &&
    "prompt" in questions &&
    typeof (questions as { prompt?: unknown }).prompt === "string"
  ) {
    return (questions as { prompt: string }).prompt;
  }
  return "";
}

