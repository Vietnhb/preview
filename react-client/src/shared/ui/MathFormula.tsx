import { useMemo } from "react";
import katex from "katex";
import { astEquationLatex, astLatex, equationLatex, type PresentedEquation } from "../lib/formulaLatex";
import "katex/dist/katex.min.css";
import "./MathFormula.css";

type Props = {
  equation?: string;
  expression?: unknown;
  name?: string;
  derivative?: boolean;
  latex?: string;
  block?: boolean;
  className?: string;
};

/** Display only. Invalid/new notation stays visible as original text. */
export default function MathFormula({ equation, expression, name, derivative = false, latex, block = false, className = "" }: Readonly<Props>) {
  const fallback = equation ?? latex ?? (name ? `${derivative ? `d(${name})/dt` : name} = ` : "") + JSON.stringify(expression);
  const presentation = useMemo(() => {
    try {
      const value: PresentedEquation = latex !== undefined ? { latex }
        : equation !== undefined ? equationLatex(equation)
          : { latex: name ? astEquationLatex(name, expression, derivative) : astLatex(expression) };
      if (value.latex.length > 12000) throw new Error("Formula too long");
      const html = katex.renderToString(value.latex, {
        displayMode: block, throwOnError: true, trust: false, strict: "warn",
        output: "htmlAndMathml", maxExpand: 500, maxSize: 10,
      });
      return { ...value, html };
    } catch {
      return null;
    }
  }, [equation, expression, name, derivative, latex, block]);
  return <span className={`math-formula${block ? " math-formula--block" : ""} ${className}`}>
    {presentation ? <>
      {presentation.before && <span className="math-formula__note">{presentation.before} </span>}
      <span className="math-formula__math" dangerouslySetInnerHTML={{ __html: presentation.html }} />
      {presentation.after && <span className="math-formula__note"> {presentation.after}</span>}
    </> : <span className="math-formula__source">{fallback}</span>}
  </span>;
}
