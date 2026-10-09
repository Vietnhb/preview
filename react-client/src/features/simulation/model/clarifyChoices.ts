/**
 * Splits a clarifying question into its lead sentence and the alternatives it enumerates
 * ("1) … 2) …", "(a) … (b) …"). Only a run of consecutive markers starting at the first one
 * counts, so numbers that are part of the physics ("R1 = 1Ω", "2 m/s") are left alone.
 */
export type ClarifyChoices = { lead: string; choices: string[] };

const SEQUENCES = ["123456789", "abcdefghi"];

export function splitChoices(question: string): ClarifyChoices {
  const text = question.trim();
  for (const sequence of SEQUENCES) {
    const marks: Array<{ at: number; end: number }> = [];
    for (const symbol of sequence) {
      const pattern = new RegExp("(^|[\\s:;,.?])\\(?" + symbol + "[).]\\s", "gi");
      pattern.lastIndex = marks.length ? marks[marks.length - 1].end : 0;
      const hit = pattern.exec(text);
      if (!hit) break;
      marks.push({ at: hit.index + hit[1].length, end: hit.index + hit[0].length });
    }
    if (marks.length < 2) continue;
    const choices = marks.map((mark, i) => text.slice(mark.end, i + 1 < marks.length ? marks[i + 1].at : text.length)
      .trim().replace(/[\s;,]*(?:hoặc|hay|or)$/i, "").replace(/^[\s:;,]+|[\s;,.?]+$/g, "").trim());
    if (choices.some(choice => !choice)) continue;
    return { lead: text.slice(0, marks[0].at).trim().replace(/[\s:]+$/, ""), choices };
  }
  return { lead: text, choices: [] };
}
